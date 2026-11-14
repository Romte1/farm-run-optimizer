package com.herbrunhelper;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Farm Run Planner",
	description = "Plans herb, tree, fruit tree and hardwood farm runs: what to bring, the fastest route, what to do at each patch, and every growth timer",
	tags = {"farming", "farm run", "herb run", "tree run", "fruit tree", "hardwood", "herbs", "patches", "timers", "fairy ring", "route", "planner"}
)
public class HerbRunPlugin extends Plugin
{
	// Raw container IDs (stable in the game cache): inventory, worn equipment, bank
	private static final int INVENTORY = 93;
	private static final int EQUIPMENT = 94;
	private static final int BANK = 95;

	private static final int QUEST_CHECK_INTERVAL_TICKS = 100;
	private static final int PATCH_SYNC_INTERVAL_TICKS = 2;
	/** Hint arrows only render inside the loaded area. */
	private static final int HINT_ARROW_RANGE = 50;
	private static final String TIMERS_KEY = "timers";
	private static final String CLOCK_KEY = "growthClock";
	private static final String TIME_TRACKING_GROUP = "timetracking";
	/** Standard spellbook = 0. */
	private static final int SPELLBOOK_VARBIT = 4070;
	private static final String[] TELEPORT_QUESTS = {"Plague City", "Eadgar's Ruse", "Client of Kourend", "Twilight's Promise"};
	private static final String FAIRYTALE_II = "Fairytale II - Cure a Queen";

	@Inject
	private Client client;

	@Inject
	private HerbRunConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private Notifier notifier;

	@Inject
	private HerbRunBankOverlay bankOverlay;

	@Inject
	private PatchNavigationOverlay navigationOverlay;

	@Inject
	private HerbRunInventoryOverlay inventoryOverlay;

	private HerbRunPanel panel;
	private NavigationButton navButton;

	// name -> qty snapshots and name -> item id, refreshed whenever a container changes
	private final Map<String, Integer> bankItems = new HashMap<>();
	private final Map<String, Integer> inventoryItems = new HashMap<>();
	private final Map<String, Integer> equipmentItems = new HashMap<>();
	private final Map<String, Integer> bankNameToId = new HashMap<>();
	private final Map<String, Integer> inventoryNameToId = new HashMap<>();
	private final Map<String, Integer> equipmentNameToId = new HashMap<>();
	private boolean bankSeen;

	private final Set<String> completedQuests = new HashSet<>();
	private boolean fairyRingsUnlocked;
	private int ticksSinceQuestCheck = QUEST_CHECK_INTERVAL_TICKS;
	private int ticksSincePatchSync;
	private boolean dirty = true;
	private boolean timersLoaded;

	private final PatchTimers timers = new PatchTimers();
	private final GrowthClock clock = new GrowthClock();
	private final Map<FarmPatch, Integer> lastSeenValue = new EnumMap<>(FarmPatch.class);
	private final Set<FarmPatch> notifiedReady = EnumSet.noneOf(FarmPatch.class);

	private final Set<GameObject> patchObjects = new HashSet<>();
	private volatile Set<Integer> highlightIds = Collections.emptySet();
	private volatile FarmPatch nextPatch;
	private FarmPatch shortestPathTarget;
	private boolean ourHintArrow;
	private RunPlan lastPlan;

	/** What's happened at each stop since you planted there (for the step highlight). */
	private final Map<FarmPatch, StepItems.Progress> progress = new EnumMap<>(FarmPatch.class);
	private volatile String stepItem;
	private FarmPatch stepPatch;
	private String stepAction;

	@Provides
	HerbRunConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HerbRunConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new HerbRunPanel(itemManager, timers, this::resetTimers, this::setRun,
			this::toggleTeleport, this::setUseSpells, this::setUseTabs, this::toggleCrop, this::togglePatch,
			this::setSetting);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Farm Run Planner")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(bankOverlay);
		overlayManager.add(navigationOverlay);
		overlayManager.add(inventoryOverlay);
		dirty = true;
		timersLoaded = false;
		ticksSinceQuestCheck = QUEST_CHECK_INTERVAL_TICKS;
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(bankOverlay);
		overlayManager.remove(navigationOverlay);
		overlayManager.remove(inventoryOverlay);
		progress.clear();
		stepItem = null;
		panel.stop();
		clearNavigation();
		bankItems.clear();
		inventoryItems.clear();
		equipmentItems.clear();
		bankNameToId.clear();
		inventoryNameToId.clear();
		equipmentNameToId.clear();
		patchObjects.clear();
		lastSeenValue.clear();
		bankSeen = false;
		highlightIds = Collections.emptySet();
		nextPatch = null;
	}

	// ================================================================ events

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				ticksSinceQuestCheck = QUEST_CHECK_INTERVAL_TICKS;
				dirty = true;
				break;
			case LOADING:
				patchObjects.clear();
				lastSeenValue.clear();
				break;
			case LOGIN_SCREEN:
				// Another account may log in next.
				bankItems.clear();
				bankNameToId.clear();
				bankSeen = false;
				timersLoaded = false;
				notifiedReady.clear();
				clearNavigation();
				break;
			default:
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.FARMING || event.getSkill() == Skill.MAGIC)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == BANK)
		{
			snapshot(event.getItemContainer(), bankItems, bankNameToId);
			bankSeen = true;
			dirty = true;
		}
		else if (id == INVENTORY)
		{
			snapshot(event.getItemContainer(), inventoryItems, inventoryNameToId);
			noticeItemsUsed();
			dirty = true;
		}
		else if (id == EQUIPMENT)
		{
			snapshot(event.getItemContainer(), equipmentItems, equipmentNameToId);
			dirty = true;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String lower = event.getMessage().toLowerCase();
		if (lower.contains("you treat the"))
		{
			// "You treat the herb patch with ultracompost."
			for (StepItems.Progress p : progress.values())
			{
				p.composted = true;
			}
			return;
		}
		Crop crop = Crop.fromPlantMessage(event.getMessage());
		Player player = client.getLocalPlayer();
		if (crop == null || player == null)
		{
			return;
		}
		WorldPoint where = player.getWorldLocation();
		FarmPatch patch = FarmPatch.at(where.getX(), where.getY(), crop.getType());
		if (patch == null)
		{
			log.debug("Planted {} but no known patch at {}", crop, where);
			return;
		}
		timers.planted(patch, crop, System.currentTimeMillis());
		RunPlan.RouteStep step = stepFor(patch);
		progress.clear();
		progress.put(patch, new StepItems.Progress(System.currentTimeMillis(),
			step == null || step.compost == null ? 0 : inventoryItems.getOrDefault(step.compost, 0),
			step == null || step.paymentItem == null ? 0 : inventoryItems.getOrDefault(step.paymentItem, 0)));
		notifiedReady.remove(patch);
		saveTimers();
		dirty = true;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject obj = event.getGameObject();
		if (isFarmPatchObject(obj.getId()))
		{
			patchObjects.add(obj);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		patchObjects.remove(event.getGameObject());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!HerbRunConfig.GROUP.equals(event.getGroup())
			|| TIMERS_KEY.equals(event.getKey()) || CLOCK_KEY.equals(event.getKey()))
		{
			return;
		}
		dirty = true;
		if (!config.useShortestPath())
		{
			clearShortestPath();
		}
		if (!config.hintArrow())
		{
			clearHintArrow();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!timersLoaded)
		{
			loadTimers();
		}
		if (++ticksSinceQuestCheck >= QUEST_CHECK_INTERVAL_TICKS)
		{
			ticksSinceQuestCheck = 0;
			refreshRequirements();
			if (timersLoaded)
			{
				importTimeTracking();
			}
		}
		if (timersLoaded && ++ticksSincePatchSync >= PATCH_SYNC_INTERVAL_TICKS)
		{
			ticksSincePatchSync = 0;
			syncNearbyPatches();
		}
		checkReadyNotifications();
		if (dirty)
		{
			dirty = false;
			recompute();
		}
		updateNavigation();
		updateStepItem();
	}

	// ================================================================ accessors for overlays

	Set<Integer> getHighlightIds()
	{
		return highlightIds;
	}

	FarmPatch getNextPatch()
	{
		return nextPatch;
	}

	Set<GameObject> getPatchObjects()
	{
		return patchObjects;
	}

	String getStepItem()
	{
		return stepItem;
	}

	// ================================================================ step highlight

	private RunPlan.RouteStep stepFor(FarmPatch patch)
	{
		if (lastPlan == null)
		{
			return null;
		}
		for (RunPlan.RouteStep s : lastPlan.route)
		{
			if (s.patch == patch)
			{
				return s;
			}
		}
		return null;
	}

	/** Compost or payment leaving the inventory after planting means that step is done. */
	private void noticeItemsUsed()
	{
		for (Map.Entry<FarmPatch, StepItems.Progress> e : progress.entrySet())
		{
			RunPlan.RouteStep step = stepFor(e.getKey());
			StepItems.Progress p = e.getValue();
			if (step == null)
			{
				continue;
			}
			if (!p.composted && step.compost != null && !step.compost.startsWith("Bottomless")
				&& inventoryItems.getOrDefault(step.compost, 0) < p.compostBaseline)
			{
				p.composted = true;
			}
			if (!p.paid && step.paymentItem != null
				&& inventoryItems.getOrDefault(step.paymentItem, 0) < p.paymentBaseline)
			{
				p.paid = true;
			}
		}
	}

	/**
	 * Works out which stop you're at (the route patch in the area you're standing in, else the
	 * next stop) and which inventory item its current step needs.
	 */
	private void updateStepItem()
	{
		Player player = client.getLocalPlayer();
		if (lastPlan == null || player == null)
		{
			stepItem = null;
			return;
		}
		long now = System.currentTimeMillis();
		WorldPoint wp = player.getWorldLocation();
		int region = FarmPatch.regionOf(wp.getX(), wp.getY());

		RunPlan.RouteStep focus = null;
		for (RunPlan.RouteStep s : lastPlan.route)
		{
			if (!s.patch.inRegion(region))
			{
				continue;
			}
			StepItems.Progress p = progress.get(s.patch);
			if (p != null && StepItems.next(s, timers.get(s.patch), timers.getStatus(s.patch), p, now) != null)
			{
				focus = s;
				break;
			}
			if (focus == null && timers.needsVisit(s.patch, now))
			{
				focus = s;
			}
		}
		if (focus == null && nextPatch != null)
		{
			focus = stepFor(nextPatch);
		}

		StepItems.Next next = focus == null ? null
			: StepItems.next(focus, timers.get(focus.patch), timers.getStatus(focus.patch), progress.get(focus.patch), now);
		String item = next == null ? null : next.item;
		String action = next == null ? null : next.action;
		FarmPatch patch = next == null ? null : focus.patch;
		stepItem = item;
		if (patch != stepPatch || !java.util.Objects.equals(action, stepAction))
		{
			stepPatch = patch;
			stepAction = action;
			SwingUtilities.invokeLater(() -> panel.setStepHint(patch, action, item));
		}
	}

	// ================================================================ items, quests, planning

	private boolean isFarmPatchObject(int objectId)
	{
		ObjectComposition def = client.getObjectDefinition(objectId);
		if (def == null)
		{
			return false;
		}
		if (isPatchName(def.getName()))
		{
			return true;
		}
		if (def.getImpostorIds() != null)
		{
			ObjectComposition imp = def.getImpostor();
			return imp != null && isPatchName(imp.getName());
		}
		return false;
	}

	private static boolean isPatchName(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase();
		return n.equals("herbs") || (n.endsWith("patch") && (n.contains("herb") || n.contains("tree")));
	}

	private void snapshot(ItemContainer container, Map<String, Integer> target, Map<String, Integer> nameToId)
	{
		target.clear();
		nameToId.clear();
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(item.getId());
			String name = itemManager.getItemComposition(canonical).getName();
			target.merge(name, item.getQuantity(), Integer::sum);
			nameToId.putIfAbsent(name, item.getId());
		}
	}

	/** Must run on the client thread (Quest.getState runs a client script). */
	private void refreshRequirements()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Set<String> wanted = new HashSet<>();
		Collections.addAll(wanted, TELEPORT_QUESTS);
		for (FarmPatch.Requirement r : FarmPatch.Requirement.values())
		{
			if (r.questName() != null)
			{
				wanted.add(r.questName());
			}
		}
		Set<String> before = new HashSet<>(completedQuests);
		boolean fairyBefore = fairyRingsUnlocked;
		completedQuests.clear();
		fairyRingsUnlocked = false;
		// Looked up by name so a renamed enum constant can't break the build.
		for (Quest quest : Quest.values())
		{
			String name = quest.getName();
			if (wanted.contains(name) && quest.getState(client) == QuestState.FINISHED)
			{
				completedQuests.add(name);
			}
			else if (FAIRYTALE_II.equals(name) && quest.getState(client) != QuestState.NOT_STARTED)
			{
				fairyRingsUnlocked = true;
			}
		}
		if (!before.equals(completedQuests) || fairyBefore != fairyRingsUnlocked)
		{
			dirty = true;
		}
	}

	private int farmingLevel()
	{
		return config.useBoostedLevel()
			? client.getBoostedSkillLevel(Skill.FARMING)
			: client.getRealSkillLevel(Skill.FARMING);
	}

	private Set<PatchType> runs()
	{
		Set<PatchType> runs = EnumSet.noneOf(PatchType.class);
		if (config.includeHerbs())
		{
			runs.add(PatchType.HERB);
		}
		if (config.includeTrees())
		{
			runs.add(PatchType.TREE);
		}
		if (config.includeFruitTrees())
		{
			runs.add(PatchType.FRUIT_TREE);
		}
		if (config.includeHardwood())
		{
			runs.add(PatchType.HARDWOOD);
		}
		return runs;
	}

	private void recompute()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int level = farmingLevel();

		Set<FarmPatch> unlocked = EnumSet.noneOf(FarmPatch.class);
		for (FarmPatch patch : FarmPatch.values())
		{
			FarmPatch.Requirement req = patch.getRequirement();
			boolean ok;
			switch (req)
			{
				case NONE:
					ok = true;
					break;
				case FARMING_65:
					ok = level >= 65;
					break;
				case FARMING_85:
					ok = level >= 85;
					break;
				case MORYTANIA_ELITE:
					ok = config.harmonyUnlocked();
					break;
				default:
					ok = completedQuests.contains(req.questName());
			}
			if (ok)
			{
				unlocked.add(patch);
			}
		}

		Map<String, Integer> owned = new HashMap<>(bankItems);
		inventoryItems.forEach((k, v) -> owned.merge(k, v, Integer::sum));
		equipmentItems.forEach((k, v) -> owned.merge(k, v, Integer::sum));

		RunPlanner.Options options = new RunPlanner.Options();
		options.runs.clear();
		options.runs.addAll(runs());
		options.payGardeners = config.payGardeners();
		options.useCompost = config.useCompost();
		options.bringRake = config.bringRake();
		options.bankKnown = bankSeen;
		options.disabledCrops = readSet(config.disabledCrops(), Crop.class);
		options.disabledPatches = readSet(config.disabledPatches(), FarmPatch.class);
		Teleport.Context ctx = options.teleports;
		ctx.magicLevel = client.getRealSkillLevel(Skill.MAGIC);
		ctx.standardSpellbook = client.getVarbitValue(SPELLBOOK_VARBIT) == 0;
		ctx.completedQuests = new HashSet<>(completedQuests);
		ctx.fairyRingsUnlocked = fairyRingsUnlocked;
		ctx.staffless = config.staffless();
		ctx.disabled = readSet(config.disabledTeleports(), Teleport.class);
		ctx.useSpells = config.useSpells();
		ctx.useTabs = config.useTabs();
		ctx.chainFairyRings = config.chainFairyRings();

		RunPlan plan = RunPlanner.plan(level, unlocked, owned, options);
		lastPlan = plan;

		// Highlight what's still to take from the bank (first leg).
		Set<Integer> ids = new HashSet<>();
		for (RunPlan.Withdrawal w : plan.withdrawals)
		{
			int carried = inventoryItems.getOrDefault(w.itemName, 0) + equipmentItems.getOrDefault(w.itemName, 0);
			Integer bankId = bankNameToId.get(w.itemName);
			if (bankId != null && carried < w.quantity)
			{
				ids.add(bankId);
			}
		}
		highlightIds = ids;

		Map<String, Integer> carried = new HashMap<>(inventoryItems);
		equipmentItems.forEach((k, v) -> carried.merge(k, v, Integer::sum));
		Map<String, Integer> nameToId = new HashMap<>(equipmentNameToId);
		inventoryNameToId.forEach(nameToId::putIfAbsent);
		bankNameToId.forEach(nameToId::putIfAbsent);
		Set<String> worn = new HashSet<>(equipmentItems.keySet());
		Set<PatchType> runs = options.runs.isEmpty() ? EnumSet.noneOf(PatchType.class) : EnumSet.copyOf(options.runs);
		Set<Teleport> disabled = EnumSet.noneOf(Teleport.class);
		disabled.addAll(ctx.disabled);
		boolean useSpells = ctx.useSpells;
		boolean useTabs = ctx.useTabs;
		Set<Crop> offCrops = options.disabledCrops;
		Set<FarmPatch> offPatches = options.disabledPatches;
		Map<String, Boolean> settings = new java.util.LinkedHashMap<>();
		settings.put("useShortestPath", config.useShortestPath());
		settings.put("highlightPatch", config.highlightPatch());
		settings.put("hintArrow", config.hintArrow());
		settings.put("highlightInventory", config.highlightInventory());
		settings.put("highlightBank", config.highlightBank());
		settings.put("notifyReady", config.notifyReady());
		settings.put("chainFairyRings", config.chainFairyRings());
		settings.put("payGardeners", config.payGardeners());
		settings.put("useCompost", config.useCompost());
		settings.put("bringRake", config.bringRake());
		settings.put("importTimeTracking", config.importTimeTracking());
		settings.put("useBoostedLevel", config.useBoostedLevel());
		settings.put("staffless", config.staffless());
		settings.put("harmonyUnlocked", config.harmonyUnlocked());
		settings.put("showTimers", config.showTimers());
		settings.put("showTravel", config.showTravel());
		SwingUtilities.invokeLater(() ->
		{
			panel.setSettings(settings);
			panel.setRuns(runs);
			panel.setPicks(offCrops, offPatches);
			panel.setPlan(plan, carried, worn, nameToId, disabled, useSpells, useTabs);
		});
	}

	// ================================================================ patch state sync

	/**
	 * Reads the state of every farming patch in the area you're standing in, straight from the
	 * game's farming variables (as RuneLite's Time Tracking does), and corrects the timers.
	 * Also learns your account's growth-tick offset when a patch changes stage in front of you.
	 */
	private void syncNearbyPatches()
	{
		Player player = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || player == null)
		{
			return;
		}
		WorldPoint wp = player.getWorldLocation();
		int region = FarmPatch.regionOf(wp.getX(), wp.getY());
		long now = System.currentTimeMillis();
		boolean changed = false;
		boolean clockChanged = false;
		for (FarmPatch patch : FarmPatch.values())
		{
			if (!patch.inRegion(region))
			{
				continue;
			}
			int value = client.getVarbitValue(patch.getVarbit());
			PatchStates.Decoded d = PatchStates.get().decode(patch.getType(), value);

			Integer last = lastSeenValue.put(patch, value);
			if (last != null && last != value && d.crop != null
				&& (d.state == PatchStates.State.GROWING || d.state == PatchStates.State.READY))
			{
				PatchStates.Decoded before = PatchStates.get().decode(patch.getType(), last);
				if (before.crop == d.crop && before.state == PatchStates.State.GROWING && d.stage > before.stage)
				{
					clock.learn(patch.getType(), now);
					clockChanged = true;
				}
			}

			PatchTimers.Planting existing = timers.get(patch);
			boolean justPlanted = existing != null && now - existing.plantedAtMillis < 2 * 60_000L;
			changed |= apply(patch, d, existing, justPlanted, now, -1);
		}
		if (clockChanged)
		{
			configManager.setRSProfileConfiguration(HerbRunConfig.GROUP, CLOCK_KEY, clock.serialize());
		}
		if (changed)
		{
			saveTimers();
			dirty = true;
		}
	}

	/** changedAt < 0 means "seen live right now"; otherwise when that stage started (import). */
	private boolean apply(FarmPatch patch, PatchStates.Decoded d, PatchTimers.Planting existing,
		boolean justPlanted, long now, long changedAt)
	{
		long tolerance = GrowthClock.tickMillis(patch.getType()) + 5 * 60_000L;
		switch (d.state)
		{
			case GROWING:
			{
				if (d.crop == null)
				{
					return setStatusIfNew(patch, PatchTimers.Status.GROWING_UNKNOWN, null);
				}
				long est = changedAt < 0
					? clock.readyFromLive(d.crop, d.stage, now)
					: GrowthClock.readyFromChange(d.crop, d.stage, changedAt);
				if (existing == null || existing.crop != d.crop
					|| Math.abs(existing.readyAtMillis() - est) > tolerance)
				{
					timers.syncReadyAt(patch, d.crop, est);
					return true;
				}
				return false;
			}
			case READY:
			{
				Crop crop = d.crop != null ? d.crop : existing != null ? existing.crop : null;
				if (crop == null)
				{
					return false;
				}
				if (existing == null || existing.crop != crop || !existing.isReady(now))
				{
					timers.syncReadyAt(patch, crop, changedAt < 0 ? now : Math.min(changedAt, now));
					return true;
				}
				return false;
			}
			case STUMP:
				return setStatusIfNew(patch, PatchTimers.Status.STUMP, d.crop);
			case EMPTY:
				// Weeds stage 0 = fully raked; anything above means weeds to rake.
				return !justPlanted && setStatusIfNew(patch,
					d.stage > 0 ? PatchTimers.Status.WEEDS : PatchTimers.Status.EMPTY, null);
			case DISEASED:
				return setStatusIfNew(patch, PatchTimers.Status.DISEASED, d.crop);
			case DEAD:
				return setStatusIfNew(patch, PatchTimers.Status.DEAD, d.crop);
			default:
				return false;
		}
	}

	private boolean setStatusIfNew(FarmPatch patch, PatchTimers.Status status, Crop crop)
	{
		if (timers.getStatus(patch) == status && timers.getStatusCrop(patch) == crop)
		{
			return false;
		}
		timers.setStatus(patch, status, crop);
		return true;
	}

	/**
	 * RuneLite's Time Tracking plugin saves each patch's last seen value and when it changed
	 * ("value:unixSeconds" under "regionId.varbitId"). Use it for patches we know nothing newer about.
	 */
	private void importTimeTracking()
	{
		if (!config.importTimeTracking() || configManager.getRSProfileKey() == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		boolean changed = false;
		for (FarmPatch patch : FarmPatch.values())
		{
			String raw = configManager.getRSProfileConfiguration(TIME_TRACKING_GROUP,
				patch.getRegionId() + "." + patch.getVarbit());
			if (raw == null)
			{
				continue;
			}
			String[] parts = raw.split(":");
			if (parts.length < 2)
			{
				continue;
			}
			int value;
			long changedAt;
			try
			{
				value = Integer.parseInt(parts[0]);
				changedAt = Long.parseLong(parts[1]) * 1000L;
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			PatchTimers.Planting existing = timers.get(patch);
			if (existing != null && existing.plantedAtMillis >= changedAt - 60_000L)
			{
				continue; // what we have is newer
			}
			PatchStates.Decoded d = PatchStates.get().decode(patch.getType(), value);
			if (d.state == PatchStates.State.UNKNOWN || d.state == PatchStates.State.EMPTY)
			{
				continue;
			}
			changed |= apply(patch, d, existing, false, now, changedAt);
		}
		if (changed)
		{
			saveTimers();
			dirty = true;
		}
	}

	// ================================================================ navigation

	private void updateNavigation()
	{
		if (lastPlan == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long now = System.currentTimeMillis();
		FarmPatch next = null;
		for (RunPlan.RouteStep step : lastPlan.route)
		{
			if (timers.needsVisit(step.patch, now))
			{
				next = step.patch;
				break;
			}
		}
		if (next != nextPatch)
		{
			nextPatch = next;
			FarmPatch shown = next;
			SwingUtilities.invokeLater(() -> panel.setNextPatch(shown));
		}

		// Shortest Path plugin: only post when the target changes.
		if (config.useShortestPath() && next != shortestPathTarget)
		{
			if (next == null)
			{
				clearShortestPath();
			}
			else
			{
				Map<String, Object> data = new HashMap<>();
				data.put("target", new WorldPoint(next.getX(), next.getY(), 0));
				eventBus.post(new PluginMessage("shortestpath", "path", data));
				shortestPathTarget = next;
			}
		}

		// Hint arrow when close enough to render.
		if (config.hintArrow())
		{
			Player player = client.getLocalPlayer();
			boolean near = false;
			if (next != null && player != null)
			{
				WorldPoint p = player.getWorldLocation();
				near = p.getPlane() == 0
					&& Math.max(Math.abs(p.getX() - next.getX()), Math.abs(p.getY() - next.getY())) <= HINT_ARROW_RANGE;
			}
			if (near)
			{
				WorldPoint target = new WorldPoint(next.getX(), next.getY(), 0);
				if (!ourHintArrow || !target.equals(client.getHintArrowPoint()))
				{
					client.setHintArrow(target);
					ourHintArrow = true;
				}
			}
			else
			{
				clearHintArrow();
			}
		}
	}

	private void checkReadyNotifications()
	{
		if (!config.notifyReady())
		{
			return;
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<FarmPatch, PatchTimers.Planting> e : timers.snapshot().entrySet())
		{
			if (e.getValue().isReady(now) && notifiedReady.add(e.getKey()))
			{
				// Only notify for patches that became ready while you were playing.
				if (now - e.getValue().readyAtMillis() < 60_000L)
				{
					notifier.notify(e.getKey().getDisplayName() + " (" + e.getValue().crop.getDisplayName() + ") is ready!");
				}
				dirty = true;
			}
		}
	}

	private void clearNavigation()
	{
		clearHintArrow();
		clearShortestPath();
	}

	private void clearHintArrow()
	{
		if (ourHintArrow)
		{
			client.clearHintArrow();
			ourHintArrow = false;
		}
	}

	private void clearShortestPath()
	{
		if (shortestPathTarget != null)
		{
			eventBus.post(new PluginMessage("shortestpath", "clear"));
			shortestPathTarget = null;
		}
	}

	// ================================================================ saving

	private void loadTimers()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return; // profile not known yet; try again next tick
		}
		timers.load(configManager.getRSProfileConfiguration(HerbRunConfig.GROUP, TIMERS_KEY));
		clock.load(configManager.getRSProfileConfiguration(HerbRunConfig.GROUP, CLOCK_KEY));
		timersLoaded = true;
		importTimeTracking();
		notifiedReady.clear();
		long now = System.currentTimeMillis();
		timers.snapshot().forEach((patch, planting) ->
		{
			if (planting.isReady(now))
			{
				notifiedReady.add(patch); // already ready before login: don't spam notifications
			}
		});
		dirty = true;
	}

	private void saveTimers()
	{
		configManager.setRSProfileConfiguration(HerbRunConfig.GROUP, TIMERS_KEY, timers.serialize());
	}

	/** Called from the panel's reset button (Swing thread). */
	private void resetTimers()
	{
		timers.load("");
		notifiedReady.clear();
		saveTimers();
		dirty = true;
	}

	// ================================================================ settings from the panel

	/** Reads a comma-separated list of enum names saved in config. Unknown names are skipped. */
	private static <E extends Enum<E>> Set<E> readSet(String raw, Class<E> type)
	{
		Set<E> out = EnumSet.noneOf(type);
		if (raw != null)
		{
			for (String part : raw.split(","))
			{
				try
				{
					if (!part.trim().isEmpty())
					{
						out.add(Enum.valueOf(type, part.trim()));
					}
				}
				catch (IllegalArgumentException ignored)
				{
					// renamed or removed in a newer version
				}
			}
		}
		return out;
	}

	private <E extends Enum<E>> void toggleInSet(String key, String raw, E value, Class<E> type)
	{
		Set<E> set = readSet(raw, type);
		if (!set.remove(value))
		{
			set.add(value);
		}
		StringBuilder sb = new StringBuilder();
		for (E e : set)
		{
			sb.append(sb.length() > 0 ? "," : "").append(e.name());
		}
		configManager.setConfiguration(HerbRunConfig.GROUP, key, sb.toString());
	}

	/** Called from a Travel tile click (Swing thread). Saving fires ConfigChanged. */
	private void toggleTeleport(Teleport teleport)
	{
		toggleInSet("disabledTeleports", config.disabledTeleports(), teleport, Teleport.class);
	}

	private void toggleCrop(Crop crop)
	{
		toggleInSet("disabledCrops", config.disabledCrops(), crop, Crop.class);
	}

	private void togglePatch(FarmPatch patch)
	{
		toggleInSet("disabledPatches", config.disabledPatches(), patch, FarmPatch.class);
	}

	/** Called from the panel's run checkboxes. */
	private void setRun(PatchType type, boolean on)
	{
		String key;
		switch (type)
		{
			case HERB:
				key = "includeHerbs";
				break;
			case TREE:
				key = "includeTrees";
				break;
			case FRUIT_TREE:
				key = "includeFruitTrees";
				break;
			default:
				key = "includeHardwood";
		}
		configManager.setConfiguration(HerbRunConfig.GROUP, key, on);
	}

	/** Called from the panel's Settings section. Saving fires ConfigChanged. */
	private void setSetting(String key, boolean value)
	{
		configManager.setConfiguration(HerbRunConfig.GROUP, key, value);
	}

	private void setUseSpells(boolean use)
	{
		configManager.setConfiguration(HerbRunConfig.GROUP, "useSpells", use);
	}

	private void setUseTabs(boolean use)
	{
		configManager.setConfiguration(HerbRunConfig.GROUP, "useTabs", use);
	}
}
