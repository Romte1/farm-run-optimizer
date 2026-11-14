package com.herbrunhelper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure planning logic with no RuneLite dependencies, so it can be unit tested on its own.
 *
 * Input: Farming level, which patches the player has unlocked, and every item they own
 * (bank + inventory + equipment) as exact item name -> quantity.
 */
public final class RunPlanner
{
	public static final class Options
	{
		public final Set<PatchType> runs = EnumSet.of(PatchType.HERB, PatchType.TREE);
		public boolean useCompost = true;
		public boolean bringRake = true;
		public boolean payGardeners = true;
		public boolean bankKnown = true;
		public int inventorySlots = 28;
		/** Crops you've unticked: never planted. */
		public Set<Crop> disabledCrops = EnumSet.noneOf(Crop.class);
		/** Patches you've unticked: skipped. */
		public Set<FarmPatch> disabledPatches = EnumSet.noneOf(FarmPatch.class);
		/** Patches with something of yours growing or ready: always visited, and get seeds first. */
		public Set<FarmPatch> usedPatches = EnumSet.noneOf(FarmPatch.class);
		/** Levels, quests, spellbook, disabled teleports, spells/tabs, fairy chaining. */
		public Teleport.Context teleports = new Teleport.Context();
	}

	/** Coins the gardener charges to clear a fully grown tree so you can replant. */
	public static final int TREE_REMOVAL_COINS = 200;

	private static final String[] COMPOST_BEST_FIRST = {"Ultracompost", "Supercompost", "Compost"};

	/** Items you can wear instead of carrying, by equipment slot (first owned in a slot is worn). */
	private static final String[][] WEAR_SLOTS = {
		{"Magic secateurs", "Dramen staff", "Lunar staff"},
		{"Skills necklace", "Digsite pendant", "Pendant of ates", "Xeric's talisman"},
		{"Farming cape", "Ardougne cloak", "Hunter cape"},
		{"Explorer's ring"},
	};

	private RunPlanner()
	{
	}

	public static RunPlan plan(int farmingLevel, Set<FarmPatch> unlockedPatches,
		Map<String, Integer> owned, Options options)
	{
		RunPlan plan = new RunPlan();
		plan.farmingLevel = farmingLevel;
		plan.bankKnown = options.bankKnown;
		plan.unlocked.addAll(unlockedPatches);

		Teleport.Context ctx = options.teleports;
		ctx.owned = owned;
		for (Teleport t : Teleport.values())
		{
			plan.availability.put(t, t.check(ctx));
		}

		for (Crop crop : Crop.values())
		{
			if (crop.getLevel() <= farmingLevel)
			{
				plan.best.put(crop.getType(), crop);
			}
			else if (!plan.next.containsKey(crop.getType()))
			{
				plan.next.put(crop.getType(), crop);
			}
		}

		Set<PatchType> doing = EnumSet.noneOf(PatchType.class);
		for (PatchType t : options.runs)
		{
			if (plan.best.containsKey(t))
			{
				doing.add(t);
			}
			else
			{
				Crop first = plan.next.get(t);
				plan.warnings.add(t.getDisplayName() + " need level " + first.getLevel() + " Farming ("
					+ first.getDisplayName() + ").");
			}
		}

		Set<FarmPatch> included = EnumSet.noneOf(FarmPatch.class);
		for (FarmPatch patch : FarmPatch.values())
		{
			if (!doing.contains(patch.getType()))
			{
				continue;
			}
			if (!unlockedPatches.contains(patch))
			{
				plan.lockedPatches.add(patch);
				continue;
			}
			if (options.disabledPatches.contains(patch))
			{
				plan.skippedPatches.add(patch);
				continue;
			}
			included.add(patch);
		}

		// Decide what goes in each patch first: seeds go to the patches you use, then the rest.
		// A patch with nothing to plant and nothing of yours growing is left off the run.
		Map<FarmPatch, Crop> crops = new java.util.EnumMap<>(FarmPatch.class);
		for (PatchType t : doing)
		{
			allocateCrops(plan, t, included, owned, options, crops);
		}
		included.removeIf(p -> !crops.containsKey(p) && !options.usedPatches.contains(p));

		List<RunPlan.RouteStep> picked = new ArrayList<>();
		for (FarmPatch patch : included)
		{
			picked.add(pickTeleport(patch, plan, ctx, included));
		}
		plan.route.addAll(orderRoute(picked, ctx));

		if (plan.route.isEmpty())
		{
			if (plan.warnings.isEmpty())
			{
				plan.warnings.add("No patches to visit.");
			}
			return plan;
		}

		if (!options.bankKnown)
		{
			plan.warnings.add("Open your bank once so the plugin can see what you own.");
		}

		for (RunPlan.RouteStep step : plan.route)
		{
			step.crop = crops.get(step.patch);
			if (step.crop != null && step.patch.getType().isTreeLike() && options.payGardeners
				&& step.crop.getPaymentItem() != null)
			{
				step.paymentItem = step.crop.getPaymentItem();
				step.paymentQuantity = step.crop.getPaymentQuantity();
			}
		}
		paymentWarnings(plan, owned);
		String bottomless = assignCompost(plan, owned, options);
		for (RunPlan.RouteStep s : plan.route)
		{
			if (s.patch.getType().isTreeLike())
			{
				s.coins = TREE_REMOVAL_COINS;
			}
		}

		List<RunPlan.Withdrawal> fixed = fixedItems(plan, owned, options, bottomless, doing.contains(PatchType.HERB));
		splitIntoTrips(plan, fixed, options);
		return plan;
	}

	private static boolean usable(FarmPatch.Route r, RunPlan plan, Teleport.Context ctx, Set<FarmPatch> included)
	{
		if (r.teleport == null)
		{
			// A walk only works if the patch it starts from is on this run.
			return r.anchor == null || included.contains(r.anchorPatch());
		}
		return plan.availability.get(r.teleport).available && !ctx.disabled.contains(r.teleport);
	}

	private static RunPlan.RouteStep pickTeleport(FarmPatch patch, RunPlan plan, Teleport.Context ctx,
		Set<FarmPatch> included)
	{
		List<FarmPatch.Route> usable = new ArrayList<>();
		for (FarmPatch.Route r : patch.getRoutes())
		{
			if (usable(r, plan, ctx, included))
			{
				usable.add(r);
			}
		}

		// Spells / tabs toggles. When both are allowed and a spell works here, skip the tab.
		boolean spellHere = false;
		List<FarmPatch.Route> filtered = new ArrayList<>();
		for (FarmPatch.Route r : usable)
		{
			Teleport.Kind k = r.teleport == null ? null : r.teleport.getKind();
			if ((k == Teleport.Kind.SPELL && !ctx.useSpells) || (k == Teleport.Kind.TABLET && !ctx.useTabs))
			{
				continue;
			}
			spellHere |= k == Teleport.Kind.SPELL;
			filtered.add(r);
		}
		usable = new ArrayList<>();
		for (FarmPatch.Route r : filtered)
		{
			if (!(spellHere && r.teleport != null && r.teleport.getKind() == Teleport.Kind.TABLET))
			{
				usable.add(r);
			}
		}

		FarmPatch.Route chosen = null;

		// A walk from the patch you just did always wins (you're already there).
		for (FarmPatch.Route r : usable)
		{
			if (r.teleport == null && r.anchor != null)
			{
				chosen = r;
				break;
			}
		}

		if (chosen == null && ctx.chainFairyRings)
		{
			FarmPatch.Route fairy = null;
			boolean beaten = false;
			for (FarmPatch.Route r : usable)
			{
				if (r.beatsFairy)
				{
					beaten = true;
					break;
				}
				if (r.teleport != null && r.teleport.getKind() == Teleport.Kind.FAIRY_RING)
				{
					fairy = r;
					break;
				}
			}
			if (!beaten && fairy != null)
			{
				chosen = fairy;
			}
		}

		if (chosen == null && !usable.isEmpty())
		{
			chosen = usable.get(0);
		}

		if (chosen == null)
		{
			FarmPatch.Route best = patch.getRoutes().get(0);
			return new RunPlan.RouteStep(patch, null, "No teleport available (best: " + best.describe() + ")", null, false);
		}
		String item = null;
		if (chosen.teleport != null && (chosen.teleport.getKind() == Teleport.Kind.ITEM
			|| chosen.teleport.getKind() == Teleport.Kind.TABLET))
		{
			item = plan.availability.get(chosen.teleport).ownedItem;
		}
		RunPlan.RouteStep step = new RunPlan.RouteStep(patch, chosen.teleport, chosen.describe(), item, true);
		step.anchor = chosen.teleport == null ? chosen.anchorPatch() : null;
		return step;
	}

	private static boolean isFairy(RunPlan.RouteStep s)
	{
		return s.teleport != null && s.teleport.getKind() == Teleport.Kind.FAIRY_RING;
	}

	/**
	 * Keeps the base order, but pulls every fairy-ring stop together into one chain that starts
	 * right after a patch with a fairy ring nearby (e.g. Ardougne cloak, then walk to BLR).
	 * Walk stops stay glued to the stop before them. Fairy directions say which code to dial.
	 */
	static List<RunPlan.RouteStep> orderRoute(List<RunPlan.RouteStep> steps, Teleport.Context ctx)
	{
		// Group into blocks: a stop plus any walks that follow it.
		List<List<RunPlan.RouteStep>> blocks = new ArrayList<>();
		for (RunPlan.RouteStep s : steps)
		{
			List<RunPlan.RouteStep> home = s.anchor == null ? null : blockWith(blocks, s.anchor);
			if (home != null)
			{
				home.add(s);
			}
			else
			{
				List<RunPlan.RouteStep> b = new ArrayList<>();
				b.add(s);
				blocks.add(b);
			}
		}

		List<List<RunPlan.RouteStep>> fairy = new ArrayList<>();
		List<List<RunPlan.RouteStep>> other = new ArrayList<>();
		for (List<RunPlan.RouteStep> b : blocks)
		{
			(isFairy(b.get(0)) ? fairy : other).add(b);
		}

		List<List<RunPlan.RouteStep>> ordered = new ArrayList<>();
		String startRing = null;
		if (fairy.isEmpty())
		{
			ordered.addAll(other);
		}
		else
		{
			// Start the chain from the patch whose ring is the shortest walk away.
			int starter = -1;
			int bestRank = Integer.MAX_VALUE;
			for (int i = 0; i < other.size(); i++)
			{
				RunPlan.RouteStep head = other.get(i).get(0);
				String ring = head.patch.getNearbyRing();
				if (head.hasTeleport && ring != null && ringWalkRank(ring) < bestRank)
				{
					starter = i;
					bestRank = ringWalkRank(ring);
				}
			}
			if (starter < 0)
			{
				ordered.addAll(fairy);
				ordered.addAll(other);
			}
			else
			{
				ordered.addAll(other.subList(0, starter + 1));
				ordered.addAll(fairy);
				ordered.addAll(other.subList(starter + 1, other.size()));
				startRing = other.get(starter).get(0).patch.getNearbyRing();
			}
		}

		// Flatten and rewrite fairy directions based on where you're coming from.
		List<RunPlan.RouteStep> out = new ArrayList<>();
		RunPlan.RouteStep prev = null;
		for (List<RunPlan.RouteStep> b : ordered)
		{
			for (RunPlan.RouteStep s : b)
			{
				RunPlan.RouteStep step = s;
				if (isFairy(s))
				{
					String code = s.teleport.getFairyCode();
					String rest = s.teleportLabel.substring(s.teleport.getLabel().length());
					String from;
					if (prev != null && isFairy(prev))
					{
						from = "Back to the ring, dial " + code;
					}
					else if (prev != null && startRing != null && prev.patch.getNearbyRing() != null)
					{
						from = "Walk to fairy ring " + prev.patch.getNearbyRing() + ", dial " + code;
					}
					else
					{
						from = "From any fairy ring, dial " + code;
					}
					step = new RunPlan.RouteStep(s.patch, s.teleport, from + rest, null, true);
					step.crop = s.crop;
				}
				out.add(step);
				prev = step;
			}
		}
		return out;
	}

	/** Lower = the ring is closer to its patch (ALQ is right by the Morytania patch; BLR is a walk). */
	private static int ringWalkRank(String ring)
	{
		switch (ring)
		{
			case "ALQ":
				return 1;
			case "AKR":
				return 2;
			case "CIR":
				return 3;
			case "AJP":
				return 4;
			default:
				return 9;
		}
	}

	private static List<RunPlan.RouteStep> blockWith(List<List<RunPlan.RouteStep>> blocks, FarmPatch patch)
	{
		for (List<RunPlan.RouteStep> b : blocks)
		{
			for (RunPlan.RouteStep s : b)
			{
				if (s.patch == patch)
				{
					return b;
				}
			}
		}
		return null;
	}

	/**
	 * Hands out the best seeds/saplings you own to this type's patches: patches you already use
	 * first, then the rest in route order. Patches left without one simply aren't in the map.
	 */
	private static void allocateCrops(RunPlan plan, PatchType type, Set<FarmPatch> included,
		Map<String, Integer> owned, Options options, Map<FarmPatch, Crop> out)
	{
		List<Crop> plantable = new ArrayList<>();
		for (Crop crop : Crop.values())
		{
			if (crop.getType() == type && crop.getLevel() <= plan.farmingLevel
				&& !options.disabledCrops.contains(crop))
			{
				plantable.add(crop);
			}
		}
		if (plantable.isEmpty())
		{
			plan.warnings.add("No " + type.getDisplayName().toLowerCase()
				+ " ticked to plant. Pick some in the run's section.");
			return;
		}
		List<Crop> queue = new ArrayList<>();
		for (int i = plantable.size() - 1; i >= 0; i--)
		{
			Crop crop = plantable.get(i);
			int have = owned.getOrDefault(crop.getSeedName(), 0);
			for (int n = 0; n < have; n++)
			{
				queue.add(crop);
			}
		}
		List<FarmPatch> order = new ArrayList<>();
		for (FarmPatch p : included)
		{
			if (p.getType() == type && options.usedPatches.contains(p))
			{
				order.add(p);
			}
		}
		for (FarmPatch p : included)
		{
			if (p.getType() == type && !options.usedPatches.contains(p))
			{
				order.add(p);
			}
		}
		int next = 0;
		int unplanted = 0;
		for (FarmPatch p : order)
		{
			if (next < queue.size())
			{
				out.put(p, queue.get(next++));
			}
			else
			{
				unplanted++;
			}
		}
		if (unplanted > 0 && !order.isEmpty())
		{
			Crop best = plantable.get(plantable.size() - 1);
			String what = type == PatchType.HERB ? "seeds" : "saplings";
			String patchName = type == PatchType.HERB ? "herb" : type == PatchType.TREE ? "tree"
				: type == PatchType.FRUIT_TREE ? "fruit tree" : "hardwood";
			plan.warnings.add("Only enough " + what + " for " + (order.size() - unplanted) + " of " + order.size()
				+ " " + patchName + " patches, so the rest are left off. Best you can plant: " + best.getSeedName() + ".");
		}
	}

	private static void paymentWarnings(RunPlan plan, Map<String, Integer> owned)
	{
		Map<String, Integer> need = new LinkedHashMap<>();
		for (RunPlan.RouteStep s : plan.route)
		{
			if (s.paymentItem != null)
			{
				need.merge(s.paymentItem, s.paymentQuantity, Integer::sum);
			}
		}
		need.forEach((item, qty) ->
		{
			int have = owned.getOrDefault(item, 0);
			if (have < qty)
			{
				plan.warnings.add("Short " + (qty - have) + " " + item + " to pay the gardeners.");
			}
		});
	}

	/** Returns the bottomless bucket's name if used, else assigns compost per stop best-first. */
	private static String assignCompost(RunPlan plan, Map<String, Integer> owned, Options options)
	{
		if (!options.useCompost)
		{
			return null;
		}
		for (String name : owned.keySet())
		{
			if (name.startsWith("Bottomless compost bucket") && owned.get(name) > 0)
			{
				for (RunPlan.RouteStep s : plan.route)
				{
					s.compost = name;
				}
				return name;
			}
		}
		Map<String, Integer> left = new LinkedHashMap<>();
		for (String c : COMPOST_BEST_FIRST)
		{
			left.put(c, owned.getOrDefault(c, 0));
		}
		int missing = 0;
		for (RunPlan.RouteStep s : plan.route)
		{
			for (String c : COMPOST_BEST_FIRST)
			{
				if (left.get(c) > 0)
				{
					s.compost = c;
					left.merge(c, -1, Integer::sum);
					break;
				}
			}
			if (s.compost == null)
			{
				missing++;
			}
		}
		if (missing > 0)
		{
			plan.warnings.add("Short " + missing + " compost.");
		}
		return null;
	}

	/** Tools, teleports, runes, staff: carried for the whole run. */
	private static List<RunPlan.Withdrawal> fixedItems(RunPlan plan, Map<String, Integer> owned, Options options,
		String bottomless, boolean herbs)
	{
		List<RunPlan.Withdrawal> out = new ArrayList<>();
		if (herbs)
		{
			requireTool(plan, out, owned, "Seed dibber");
		}
		requireTool(plan, out, owned, "Spade");
		if (options.bringRake)
		{
			requireTool(plan, out, owned, "Rake");
		}
		if (bottomless != null)
		{
			out.add(new RunPlan.Withdrawal(bottomless, 1, "make sure it's filled"));
		}
		if (herbs && owned.getOrDefault("Magic secateurs", 0) > 0)
		{
			out.add(new RunPlan.Withdrawal("Magic secateurs", 1, "+10% herb yield, wield it"));
		}

		// Teleport items and tablets
		Map<String, Integer> uses = new LinkedHashMap<>();
		Map<String, String> firstPatch = new LinkedHashMap<>();
		Map<String, Integer> runes = new LinkedHashMap<>();
		boolean fairy = false;
		boolean unknownRunes = false;
		for (RunPlan.RouteStep step : plan.route)
		{
			if (step.teleportItem != null)
			{
				uses.merge(step.teleportItem, 1, Integer::sum);
				firstPatch.putIfAbsent(step.teleportItem, step.patch.getDisplayName());
			}
			if (step.teleport == null)
			{
				continue;
			}
			if (step.teleport.getKind() == Teleport.Kind.SPELL)
			{
				unknownRunes |= step.teleport.getRunes().isEmpty();
				step.teleport.getRunes().forEach((rune, qty) -> runes.merge(rune, qty, Integer::sum));
			}
			else if (step.teleport.getKind() == Teleport.Kind.FAIRY_RING)
			{
				fairy = true;
			}
		}
		for (Map.Entry<String, Integer> e : uses.entrySet())
		{
			String name = e.getKey();
			int qty = name.toLowerCase().endsWith(" teleport") ? e.getValue() : 1;
			out.add(new RunPlan.Withdrawal(name, qty, firstPatch.get(name)));
		}
		for (Map.Entry<String, Integer> e : runes.entrySet())
		{
			out.add(new RunPlan.Withdrawal(e.getKey(), e.getValue(), "spell runes"));
			int have = owned.getOrDefault(e.getKey(), 0);
			if (have < e.getValue())
			{
				plan.warnings.add("Short " + (e.getValue() - have) + " " + e.getKey()
					+ " (fine if a staff or rune pouch covers it).");
			}
		}
		if (unknownRunes)
		{
			plan.warnings.add("Check the runes for Kourend/Civitas teleports in your spellbook.");
		}
		if (fairy && !options.teleports.staffless)
		{
			String staff = Teleport.staffName(owned);
			if (staff != null)
			{
				out.add(new RunPlan.Withdrawal(staff, 1, "wield it for fairy rings"));
			}
		}
		return out;
	}

	private static void requireTool(RunPlan plan, List<RunPlan.Withdrawal> out, Map<String, Integer> owned, String tool)
	{
		out.add(new RunPlan.Withdrawal(tool, 1, ""));
		if (owned.getOrDefault(tool, 0) <= 0)
		{
			plan.warnings.add("You don't seem to own a " + tool + " (unless it's in a tool leprechaun store).");
		}
	}

	/** Slots the fixed items take, counting one wearable per equipment slot as worn. */
	static int fixedSlots(List<RunPlan.Withdrawal> fixed)
	{
		int slots = 0;
		boolean[] worn = new boolean[WEAR_SLOTS.length];
		outer:
		for (RunPlan.Withdrawal w : fixed)
		{
			for (int i = 0; i < WEAR_SLOTS.length; i++)
			{
				for (String prefix : WEAR_SLOTS[i])
				{
					if (w.itemName.startsWith(prefix) && !worn[i])
					{
						worn[i] = true;
						continue outer;
					}
				}
			}
			slots++;
		}
		return slots;
	}

	/** Slots one leg's supplies take: saplings and unnoted compost one each; seeds, payments, coins stack. */
	static int legSlots(List<RunPlan.RouteStep> leg)
	{
		Set<String> stackTypes = new LinkedHashSet<>();
		int slots = 0;
		for (RunPlan.RouteStep s : leg)
		{
			if (s.crop != null)
			{
				if (s.patch.getType() == PatchType.HERB)
				{
					stackTypes.add(s.crop.getSeedName());
				}
				else
				{
					slots++;
				}
			}
			if (s.compost != null && !s.compost.startsWith("Bottomless"))
			{
				slots++;
			}
			if (s.paymentItem != null)
			{
				stackTypes.add(s.paymentItem + " (noted)");
			}
			if (s.coins > 0)
			{
				stackTypes.add("Coins");
			}
		}
		return slots + stackTypes.size();
	}

	/**
	 * Splits the route into legs that each fit in one inventory, adding a bank stop before
	 * each new leg. The first leg's supplies plus the fixed items are what you take at the start.
	 */
	static void splitIntoTrips(RunPlan plan, List<RunPlan.Withdrawal> fixed, Options options)
	{
		int fixedSlots = fixedSlots(fixed);
		List<List<RunPlan.RouteStep>> legs = new ArrayList<>();
		List<Integer> legStart = new ArrayList<>();
		List<RunPlan.RouteStep> leg = new ArrayList<>();
		for (int i = 0; i < plan.route.size(); i++)
		{
			RunPlan.RouteStep s = plan.route.get(i);
			leg.add(s);
			if (leg.size() > 1 && fixedSlots + legSlots(leg) > options.inventorySlots)
			{
				leg.remove(leg.size() - 1);
				legs.add(leg);
				legStart.add(i - leg.size());
				leg = new ArrayList<>();
				leg.add(s);
			}
		}
		legs.add(leg);
		legStart.add(plan.route.size() - leg.size());

		plan.withdrawals.addAll(supplies(legs.get(0)));
		plan.withdrawals.addAll(fixed);
		plan.slotsNeeded = fixedSlots + legSlots(legs.get(0));

		for (int l = 1; l < legs.size(); l++)
		{
			int before = legStart.get(l);
			String bank = null;
			for (int i = before - 1; i >= 0 && bank == null; i--)
			{
				bank = plan.route.get(i).patch.getBank();
			}
			RunPlan.BankStop stop = new RunPlan.BankStop(before, bank == null ? "the nearest bank" : bank);
			stop.withdrawals.addAll(supplies(legs.get(l)));
			plan.bankStops.add(stop);
		}
	}

	private static List<RunPlan.Withdrawal> supplies(List<RunPlan.RouteStep> leg)
	{
		Map<String, Integer> seeds = new LinkedHashMap<>();
		Map<String, Integer> compost = new LinkedHashMap<>();
		Map<String, Integer> pay = new LinkedHashMap<>();
		Map<String, String> notes = new LinkedHashMap<>();
		int coins = 0;
		for (RunPlan.RouteStep s : leg)
		{
			if (s.crop != null)
			{
				seeds.merge(s.crop.getSeedName(), 1, Integer::sum);
				notes.put(s.crop.getSeedName(), "level " + s.crop.getLevel());
			}
			if (s.compost != null && !s.compost.startsWith("Bottomless"))
			{
				compost.merge(s.compost, 1, Integer::sum);
			}
			if (s.paymentItem != null)
			{
				pay.merge(s.paymentItem, s.paymentQuantity, Integer::sum);
			}
			coins += s.coins;
		}
		List<RunPlan.Withdrawal> out = new ArrayList<>();
		seeds.forEach((k, v) -> out.add(new RunPlan.Withdrawal(k, v, notes.get(k))));
		compost.forEach((k, v) -> out.add(new RunPlan.Withdrawal(k, v, "")));
		pay.forEach((k, v) -> out.add(new RunPlan.Withdrawal(k, v, "gardener payment, noted is fine")));
		if (coins > 0)
		{
			out.add(new RunPlan.Withdrawal("Coins", coins, "gardener clears grown trees, "
				+ TREE_REMOVAL_COINS + " each"));
		}
		return out;
	}
}
