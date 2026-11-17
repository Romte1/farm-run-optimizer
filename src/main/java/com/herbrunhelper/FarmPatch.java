/*
 * Patch regions and varbit ids match RuneLite's Time Tracking plugin (FarmingWorld),
 * Copyright (c) 2018, NotFoxtrot and Copyright (c) 2018 Abex, BSD 2-Clause; see NOTICE.
 * Checked by scripts/verify_patches.py. Patch tiles are the farming patch points from
 * RuneLite's World Map plugin (FarmingPatchLocation); the plugin also learns each patch's exact
 * tile from the game the first time you see it. Directions follow the OSRS Wiki farming guides.
 */
package com.herbrunhelper;

import java.util.Arrays;
import java.util.List;

/**
 * Every patch the runs visit, in route order (nearby patches back-to-back). The planner moves
 * fairy-ring stops so they're chained together. Each patch lists its ways in, fastest first.
 *
 * regions: the map regions the game reports this patch in (exact, from Time Tracking).
 * varbit: the game variable holding the patch's state (exact, from Time Tracking).
 * x/y: patch tile from RuneLite's world map (allotment areas mark the middle of the area).
 *   Replaced by the exact tile once the plugin has seen the patch in game.
 * nearbyRing: fairy ring you can walk to from the patch (starts a fairy chain).
 * bank: nearest bank, used when the run needs a bank stop.
 */
public enum FarmPatch
{
	// ---- Trees around Varrock / Lumbridge / Falador
	VARROCK_TREE("Varrock tree", PatchType.TREE, Requirement.NONE, 3229, 3459, r(12854, 12853), 4771, null, "Varrock East bank",
		via(Teleport.VARROCK_TAB, "run east to the palace garden"),
		via(Teleport.VARROCK_SPELL, "run east to the palace garden")),
	LUMBRIDGE_TREE("Lumbridge tree", PatchType.TREE, Requirement.NONE, 3193, 3231, r(12594, 12850), 4771, null, "Lumbridge Castle bank (top floor)",
		via(Teleport.LUMBRIDGE_TAB, "run west over the bridge"),
		via(Teleport.LUMBRIDGE_SPELL, "run west over the bridge"),
		walk("Home Teleport, then run west", null)),
	FALADOR_TREE("Falador tree (park)", PatchType.TREE, Requirement.NONE, 3004, 3373, r(11828, 12084), 4771, null, "Falador West bank",
		via(Teleport.FALADOR_TAB, "run east into the park"),
		via(Teleport.FALADOR_SPELL, "run east into the park")),
	TAVERLEY_TREE("Taverley tree", PatchType.TREE, Requirement.NONE, 2936, 3438, r(11573, 11829), 4771, null, "Falador West bank",
		walk("Walk west from Falador park to Taverley", "FALADOR_TREE"),
		via(Teleport.FALADOR_TAB, "run west to Taverley"),
		via(Teleport.FALADOR_SPELL, "run west to Taverley")),
	FALADOR_HERB("Falador herb", PatchType.HERB, Requirement.NONE, 3058, 3311, r(12083), 4774, null, "Falador East bank",
		via(Teleport.EXPLORERS_RING, "cabbage patch teleport, run north"),
		walk("Walk south from Falador park", "FALADOR_TREE"),
		via(Teleport.FALADOR_TAB, "run south-east"),
		via(Teleport.FALADOR_SPELL, "run south-east")),

	// ---- Gnome Stronghold / Tree Gnome Village
	GNOME_TREE("Gnome Stronghold tree", PatchType.TREE, Requirement.NONE, 2436, 3415, r(9781, 9525, 9526, 9782), 4771, null, "Grand Tree bank",
		via(Teleport.ROYAL_SEED_POD, "Grand Tree, run south-west"),
		via(Teleport.SPIRIT_TREE, "Varrock Teleport, spirit tree at the Grand Exchange to Gnome Stronghold, run south-west")),
	GNOME_FRUIT("Gnome Stronghold fruit tree", PatchType.FRUIT_TREE, Requirement.NONE, 2476, 3446, r(9781, 9525, 9526, 9782), 4772, null, "Grand Tree bank",
		walk("Head north-east through the Stronghold to the fruit tree patch", "GNOME_TREE"),
		via(Teleport.ROYAL_SEED_POD, "Grand Tree, fruit tree patch beside it"),
		via(Teleport.SPIRIT_TREE, "Varrock Teleport, spirit tree at the Grand Exchange to Gnome Stronghold, fruit tree patch beside the Grand Tree")),
	TREE_GNOME_VILLAGE_FRUIT("Tree Gnome Village fruit tree", PatchType.FRUIT_TREE, Requirement.NONE, 2490, 3180, r(9777, 10033), 4771, null, "Grand Tree bank",
		walk("Spirit tree to Tree Gnome Village, get Elkoy to guide you out of the maze", "GNOME_FRUIT"),
		walk("Spirit tree to Tree Gnome Village, get Elkoy to guide you out of the maze", "GNOME_TREE"),
		via(Teleport.FAIRY_CIQ, "head north-west to the patch"),
		via(Teleport.SPIRIT_TREE, "Varrock Teleport, spirit tree at the Grand Exchange to Tree Gnome Village, get Elkoy to guide you out")),

	// ---- Catherby / Brimhaven / Ardougne
	CATHERBY_HERB("Catherby herb", PatchType.HERB, Requirement.NONE, 2813, 3463, r(11062, 11061, 11317, 11318), 4774, null, "Catherby bank",
		via(Teleport.CAMELOT_TAB, "run east to Catherby"),
		via(Teleport.CAMELOT_SPELL, "run east to Catherby")),
	CATHERBY_FRUIT("Catherby fruit tree", PatchType.FRUIT_TREE, Requirement.NONE, 2860, 3433, r(11317), 4771, null, "Catherby bank",
		walk("Follow the beach east to the fruit tree patch", "CATHERBY_HERB"),
		via(Teleport.CAMELOT_TAB, "head through Catherby to the fruit tree patch"),
		via(Teleport.CAMELOT_SPELL, "head through Catherby to the fruit tree patch")),
	BRIMHAVEN_FRUIT("Brimhaven fruit tree", PatchType.FRUIT_TREE, Requirement.NONE, 2765, 3213, r(11058, 11057), 4771, null, "Ardougne South bank",
		via(Teleport.BRIMHAVEN_TAB, "patch is just north"),
		via(Teleport.ARDOUGNE_TAB, "boat from Ardougne docks to Brimhaven (30 coins), patch north-west"),
		via(Teleport.ARDOUGNE_SPELL, "boat from Ardougne docks to Brimhaven (30 coins), patch north-west")),
	ARDOUGNE_HERB("Ardougne herb", PatchType.HERB, Requirement.NONE, 2670, 3374, r(10548), 4774, "BLR", "Ardougne North bank",
		beatsFairy(Teleport.ARDOUGNE_CLOAK, "farm teleport (cloak 2+)"),
		beatsFairy(Teleport.ARDOUGNE_TAB, "run north-east"),
		beatsFairy(Teleport.ARDOUGNE_SPELL, "run north-east"),
		via(Teleport.FAIRY_BLR, "run west")),

	// ---- Fairy-ring chain patches
	MORYTANIA_HERB("Morytania herb", PatchType.HERB, Requirement.PRIEST_IN_PERIL, 3605, 3529, r(14391, 14390), 4774, "ALQ", "Port Phasmatys bank",
		beatsFairy(Teleport.ECTOPHIAL, "run west"),
		via(Teleport.FAIRY_ALQ, "run north"),
		via(Teleport.FENKENSTRAIN_TAB, "run east")),
	HOSIDIUS_HERB("Hosidius herb", PatchType.HERB, Requirement.NONE, 1738, 3550, r(6967, 6711), 4774, "AKR", "Hosidius kitchen bank",
		via(Teleport.XERICS_TALISMAN, "Xeric's Glade, run south-west"),
		via(Teleport.KHAREDSTS_MEMOIRS, "Hosidius, run south-east"),
		via(Teleport.FAIRY_AKR, "run west"),
		via(Teleport.KOUREND_SPELL, "run south-east")),
	FARMING_GUILD_TREE("Farming Guild tree", PatchType.TREE, Requirement.FARMING_65, 1232, 3736, r(4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179), 7905, "CIR", "Farming Guild bank",
		beatsFairy(Teleport.FARMING_CAPE, null),
		via(Teleport.SKILLS_NECKLACE, "Farming Guild"),
		via(Teleport.FAIRY_CIR, "run south-west")),
	FARMING_GUILD_HERB("Farming Guild herb", PatchType.HERB, Requirement.FARMING_65, 1238, 3726, r(4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179), 4775, "CIR", "Farming Guild bank",
		walk("Walk from the guild tree patch", "FARMING_GUILD_TREE"),
		beatsFairy(Teleport.FARMING_CAPE, "run west"),
		via(Teleport.SKILLS_NECKLACE, "Farming Guild, run west"),
		via(Teleport.FAIRY_CIR, "run south-west")),
	FARMING_GUILD_FRUIT("Farming Guild fruit tree", PatchType.FRUIT_TREE, Requirement.FARMING_85, 1242, 3758, r(4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179), 7909, "CIR", "Farming Guild bank",
		walk("Into the guild's top tier, north side", "FARMING_GUILD_HERB"),
		walk("Into the guild's top tier, north side", "FARMING_GUILD_TREE"),
		beatsFairy(Teleport.FARMING_CAPE, "run north"),
		via(Teleport.SKILLS_NECKLACE, "Farming Guild, run north"),
		via(Teleport.FAIRY_CIR, "head into the guild, top tier")),

	// ---- Varlamore
	VARLAMORE_HERB("Varlamore herb (Ortus Farm)", PatchType.HERB, Requirement.CHILDREN_OF_THE_SUN, 1582, 3094,
		r(6192, 6191, 6193, 6447, 6448, 6449), 4774, "AJP", "Civitas illa Fortis bank",
		via(Teleport.QUETZAL_WHISTLE, "Hunter Guild, run north"),
		via(Teleport.HUNTER_CAPE, "Hunter Guild, run north-east"),
		via(Teleport.FAIRY_AJP, "run north-west"),
		via(Teleport.CIVITAS_SPELL, "quetzal to the Hunter Guild, run north")),
	LOCUS_OASIS_HARDWOOD("Locus Oasis hardwood", PatchType.HARDWOOD, Requirement.CHILDREN_OF_THE_SUN, 1687, 2972, r(6702, 6446), 4771, "AJP", null,
		via(Teleport.FAIRY_AJP, "Avium Savannah, patch at the oasis")),
	KASTORI_FRUIT("Kastori fruit tree", PatchType.FRUIT_TREE, Requirement.CHILDREN_OF_THE_SUN, 1350, 3057, r(5423, 5167, 5424), 4772, null, null,
		via(Teleport.PENDANT_OF_ATES, "Kastori"),
		via(Teleport.QUETZAL_WHISTLE, "Kastori"),
		walk("Quetzal from the Hunter Guild to Kastori", "VARLAMORE_HERB")),
	NEMUS_RETREAT_TREE("Nemus Retreat tree", PatchType.TREE, Requirement.CHILDREN_OF_THE_SUN, 1367, 3322, r(5427, 5428, 5684), 4771, null, null,
		via(Teleport.PENDANT_OF_ATES, "Nemus Retreat"),
		via(Teleport.QUETZAL_WHISTLE, "then head to Nemus Retreat"),
		via(Teleport.FAIRY_AIS, "Auburnvale, then west to Nemus Retreat")),

	// ---- Elsewhere
	LLETYA_FRUIT("Lletya fruit tree", PatchType.FRUIT_TREE, Requirement.MOURNINGS_END, 2347, 3162, r(9265, 11103), 4771, null, "Lletya bank",
		via(Teleport.TELEPORT_CRYSTAL, "Lletya")),
	TROLL_HERB("Troll Stronghold herb", PatchType.HERB, Requirement.MY_ARMS_BIG_ADVENTURE, 2826, 3694, r(11321), 4771, null, null,
		via(Teleport.STONY_BASALT, null),
		via(Teleport.TROLLHEIM_TAB, "climb to the stronghold roof"),
		via(Teleport.TROLLHEIM_SPELL, "climb to the stronghold roof")),
	WEISS_HERB("Weiss herb", PatchType.HERB, Requirement.MAKING_FRIENDS_WITH_MY_ARM, 2848, 3934, r(11325), 4771, null, null,
		via(Teleport.ICY_BASALT, null)),
	HARMONY_HERB("Harmony Island herb", PatchType.HERB, Requirement.MORYTANIA_ELITE, 3789, 2837, r(15148), 4772, null, null,
		via(Teleport.HARMONY_TAB, null)),
	FOSSIL_HARDWOOD_EAST("Fossil Island hardwood (east)", PatchType.HARDWOOD, Requirement.BONE_VOYAGE, 3715, 3835, r(14651, 14650, 14652, 14906, 14907, 14908, 15162, 15163, 15164), 4771, null, "Museum Camp bank",
		via(Teleport.DIGSITE_PENDANT, "Fossil Island, patches by the camp")),
	FOSSIL_HARDWOOD_MIDDLE("Fossil Island hardwood (middle)", PatchType.HARDWOOD, Requirement.BONE_VOYAGE, 3708, 3833, r(14651, 14650, 14652, 14906, 14907, 14908, 15162, 15163, 15164), 4772, null, "Museum Camp bank",
		walk("Next patch along", "FOSSIL_HARDWOOD_EAST"),
		via(Teleport.DIGSITE_PENDANT, "Fossil Island, patches by the camp")),
	FOSSIL_HARDWOOD_WEST("Fossil Island hardwood (west)", PatchType.HARDWOOD, Requirement.BONE_VOYAGE, 3702, 3837, r(14651, 14650, 14652, 14906, 14907, 14908, 15162, 15163, 15164), 4773, null, "Museum Camp bank",
		walk("Next patch along", "FOSSIL_HARDWOOD_MIDDLE"),
		via(Teleport.DIGSITE_PENDANT, "Fossil Island, patches by the camp")),

	// ---- Allotments (3 seeds each; the gardener can protect them)
	FALADOR_ALLOTMENT_A("Falador allotment (north-west)", PatchType.ALLOTMENT, Requirement.NONE, 3049, 3312,
		r(12083), 4771, null, "Falador East bank",
		walk("Next to the herb patch", "FALADOR_HERB"),
		via(Teleport.EXPLORERS_RING, "cabbage patch teleport, run north"),
		walk("Walk south from Falador park", "FALADOR_TREE"),
		via(Teleport.FALADOR_TAB, "run south-east"),
		via(Teleport.FALADOR_SPELL, "run south-east")),
	FALADOR_ALLOTMENT_B("Falador allotment (south-east)", PatchType.ALLOTMENT, Requirement.NONE, 3055, 3306,
		r(12083), 4772, null, "Falador East bank",
		walk("The other allotment, across the patch", "FALADOR_ALLOTMENT_A"),
		walk("Next to the herb patch", "FALADOR_HERB"),
		via(Teleport.EXPLORERS_RING, "cabbage patch teleport, run north"),
		walk("Walk south from Falador park", "FALADOR_TREE"),
		via(Teleport.FALADOR_TAB, "run south-east"),
		via(Teleport.FALADOR_SPELL, "run south-east")),
	CATHERBY_ALLOTMENT_A("Catherby allotment (north)", PatchType.ALLOTMENT, Requirement.NONE, 2807, 3465,
		r(11062, 11061, 11317, 11318), 4771, null, "Catherby bank",
		walk("Next to the herb patch", "CATHERBY_HERB"),
		via(Teleport.CAMELOT_TAB, "run east to Catherby"),
		via(Teleport.CAMELOT_SPELL, "run east to Catherby")),
	CATHERBY_ALLOTMENT_B("Catherby allotment (south)", PatchType.ALLOTMENT, Requirement.NONE, 2813, 3459,
		r(11062, 11061, 11317, 11318), 4772, null, "Catherby bank",
		walk("The other allotment, across the patch", "CATHERBY_ALLOTMENT_A"),
		walk("Next to the herb patch", "CATHERBY_HERB"),
		via(Teleport.CAMELOT_TAB, "run east to Catherby"),
		via(Teleport.CAMELOT_SPELL, "run east to Catherby")),
	ARDOUGNE_ALLOTMENT_A("Ardougne allotment (north)", PatchType.ALLOTMENT, Requirement.NONE, 2660, 3378,
		r(10548), 4771, "BLR", "Ardougne North bank",
		walk("Next to the herb patch", "ARDOUGNE_HERB"),
		beatsFairy(Teleport.ARDOUGNE_CLOAK, "farm teleport (cloak 2+)"),
		beatsFairy(Teleport.ARDOUGNE_TAB, "run north-east"),
		beatsFairy(Teleport.ARDOUGNE_SPELL, "run north-east"),
		via(Teleport.FAIRY_BLR, "run west")),
	ARDOUGNE_ALLOTMENT_B("Ardougne allotment (south)", PatchType.ALLOTMENT, Requirement.NONE, 2666, 3372,
		r(10548), 4772, "BLR", "Ardougne North bank",
		walk("The other allotment, across the patch", "ARDOUGNE_ALLOTMENT_A"),
		walk("Next to the herb patch", "ARDOUGNE_HERB"),
		beatsFairy(Teleport.ARDOUGNE_CLOAK, "farm teleport (cloak 2+)"),
		beatsFairy(Teleport.ARDOUGNE_TAB, "run north-east"),
		beatsFairy(Teleport.ARDOUGNE_SPELL, "run north-east"),
		via(Teleport.FAIRY_BLR, "run west")),
	MORYTANIA_ALLOTMENT_A("Morytania allotment (north-west)", PatchType.ALLOTMENT, Requirement.PRIEST_IN_PERIL, 3595, 3527,
		r(14391, 14390), 4771, "ALQ", "Port Phasmatys bank",
		walk("Next to the herb patch", "MORYTANIA_HERB"),
		beatsFairy(Teleport.ECTOPHIAL, "run west"),
		via(Teleport.FAIRY_ALQ, "run north"),
		via(Teleport.FENKENSTRAIN_TAB, "run east")),
	MORYTANIA_ALLOTMENT_B("Morytania allotment (south-east)", PatchType.ALLOTMENT, Requirement.PRIEST_IN_PERIL, 3601, 3521,
		r(14391, 14390), 4772, "ALQ", "Port Phasmatys bank",
		walk("The other allotment, across the patch", "MORYTANIA_ALLOTMENT_A"),
		walk("Next to the herb patch", "MORYTANIA_HERB"),
		beatsFairy(Teleport.ECTOPHIAL, "run west"),
		via(Teleport.FAIRY_ALQ, "run north"),
		via(Teleport.FENKENSTRAIN_TAB, "run east")),
	HOSIDIUS_ALLOTMENT_A("Hosidius allotment (north-east)", PatchType.ALLOTMENT, Requirement.NONE, 1726, 3561,
		r(6967, 6711), 4771, "AKR", "Hosidius kitchen bank",
		walk("Next to the herb patch", "HOSIDIUS_HERB"),
		via(Teleport.XERICS_TALISMAN, "Xeric's Glade, run south-west"),
		via(Teleport.KHAREDSTS_MEMOIRS, "Hosidius, run south-east"),
		via(Teleport.FAIRY_AKR, "run west"),
		via(Teleport.KOUREND_SPELL, "run south-east")),
	HOSIDIUS_ALLOTMENT_B("Hosidius allotment (south-west)", PatchType.ALLOTMENT, Requirement.NONE, 1732, 3555,
		r(6967, 6711), 4772, "AKR", "Hosidius kitchen bank",
		walk("The other allotment, across the patch", "HOSIDIUS_ALLOTMENT_A"),
		walk("Next to the herb patch", "HOSIDIUS_HERB"),
		via(Teleport.XERICS_TALISMAN, "Xeric's Glade, run south-west"),
		via(Teleport.KHAREDSTS_MEMOIRS, "Hosidius, run south-east"),
		via(Teleport.FAIRY_AKR, "run west"),
		via(Teleport.KOUREND_SPELL, "run south-east")),
	VARLAMORE_ALLOTMENT_A("Varlamore (Ortus Farm) allotment (north)", PatchType.ALLOTMENT, Requirement.CHILDREN_OF_THE_SUN, 1584, 3104,
		r(6192, 6191, 6193, 6447, 6448, 6449), 4771, "AJP", "Civitas illa Fortis bank",
		walk("Next to the herb patch", "VARLAMORE_HERB"),
		via(Teleport.QUETZAL_WHISTLE, "Hunter Guild, run north"),
		via(Teleport.HUNTER_CAPE, "Hunter Guild, run north-east"),
		via(Teleport.FAIRY_AJP, "run north-west"),
		via(Teleport.CIVITAS_SPELL, "quetzal to the Hunter Guild, run north")),
	VARLAMORE_ALLOTMENT_B("Varlamore (Ortus Farm) allotment (south)", PatchType.ALLOTMENT, Requirement.CHILDREN_OF_THE_SUN, 1590, 3098,
		r(6192, 6191, 6193, 6447, 6448, 6449), 4772, "AJP", "Civitas illa Fortis bank",
		walk("The other allotment, across the patch", "VARLAMORE_ALLOTMENT_A"),
		walk("Next to the herb patch", "VARLAMORE_HERB"),
		via(Teleport.QUETZAL_WHISTLE, "Hunter Guild, run north"),
		via(Teleport.HUNTER_CAPE, "Hunter Guild, run north-east"),
		via(Teleport.FAIRY_AJP, "run north-west"),
		via(Teleport.CIVITAS_SPELL, "quetzal to the Hunter Guild, run north")),
	GUILD_ALLOTMENT_A("Farming Guild allotment (north)", PatchType.ALLOTMENT, Requirement.FARMING_45, 1266, 3733,
		r(4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179), 4773, "CIR", "Farming Guild bank",
		walk("Walk from the guild herb patch", "FARMING_GUILD_HERB"),
		walk("Walk from the guild tree patch", "FARMING_GUILD_TREE"),
		beatsFairy(Teleport.FARMING_CAPE, "run east"),
		via(Teleport.SKILLS_NECKLACE, "Farming Guild, run east"),
		via(Teleport.FAIRY_CIR, "run south-west, then into the guild")),
	GUILD_ALLOTMENT_B("Farming Guild allotment (south)", PatchType.ALLOTMENT, Requirement.FARMING_45, 1272, 3727,
		r(4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179), 4774, "CIR", "Farming Guild bank",
		walk("The other allotment, across the patch", "GUILD_ALLOTMENT_A"),
		walk("Walk from the guild herb patch", "FARMING_GUILD_HERB"),
		walk("Walk from the guild tree patch", "FARMING_GUILD_TREE"),
		beatsFairy(Teleport.FARMING_CAPE, "run east"),
		via(Teleport.SKILLS_NECKLACE, "Farming Guild, run east"),
		via(Teleport.FAIRY_CIR, "run south-west, then into the guild"));

	private static int[] r(int... regions)
	{
		return regions;
	}

	/** One way into a patch: a teleport plus directions, or a walk (teleport == null). */
	public static final class Route
	{
		public final Teleport teleport;
		public final String note;
		/** For walks: the patch you must have just done (enum name), or null for "from anywhere". */
		public final String anchor;
		/** True if this beats chaining a fairy ring (e.g. Ardougne cloak straight to the patch). */
		public final boolean beatsFairy;

		Route(Teleport teleport, String note, String anchor, boolean beatsFairy)
		{
			this.teleport = teleport;
			this.note = note;
			this.anchor = anchor;
			this.beatsFairy = beatsFairy;
		}

		public FarmPatch anchorPatch()
		{
			return anchor == null ? null : FarmPatch.valueOf(anchor);
		}

		public String describe()
		{
			if (teleport == null)
			{
				return note;
			}
			return teleport.getLabel() + (note == null ? "" : ", " + note);
		}
	}

	private static Route via(Teleport t, String note)
	{
		return new Route(t, note, null, false);
	}

	private static Route beatsFairy(Teleport t, String note)
	{
		return new Route(t, note, null, true);
	}

	private static Route walk(String note, String anchor)
	{
		return new Route(null, note, anchor, false);
	}

	public enum Requirement
	{
		NONE(""),
		PRIEST_IN_PERIL("Priest in Peril"),
		MY_ARMS_BIG_ADVENTURE("My Arm's Big Adventure"),
		MAKING_FRIENDS_WITH_MY_ARM("Making Friends with My Arm"),
		CHILDREN_OF_THE_SUN("Children of the Sun"),
		BONE_VOYAGE("Bone Voyage"),
		MOURNINGS_END("Mourning's End Part I"),
		FARMING_45("45 Farming"),
		FARMING_65("65 Farming"),
		FARMING_85("85 Farming"),
		MORYTANIA_ELITE("Morytania Elite Diary");

		private final String description;

		Requirement(String description)
		{
			this.description = description;
		}

		public String getDescription()
		{
			return description;
		}

		/** The quest name, for requirements that are quests. */
		public String questName()
		{
			switch (this)
			{
				case PRIEST_IN_PERIL:
				case MY_ARMS_BIG_ADVENTURE:
				case MAKING_FRIENDS_WITH_MY_ARM:
				case CHILDREN_OF_THE_SUN:
				case BONE_VOYAGE:
				case MOURNINGS_END:
					return description;
				default:
					return null;
			}
		}
	}

	private final String displayName;
	private final PatchType type;
	private final Requirement requirement;
	private final int x;
	private final int y;
	private final int[] regions;
	private final int varbit;
	private final String nearbyRing;
	private final String bank;
	private final List<Route> routes;

	FarmPatch(String displayName, PatchType type, Requirement requirement, int x, int y, int[] regions,
		int varbit, String nearbyRing, String bank, Route... routes)
	{
		this.displayName = displayName;
		this.type = type;
		this.requirement = requirement;
		this.x = x;
		this.y = y;
		this.regions = regions;
		this.varbit = varbit;
		this.nearbyRing = nearbyRing;
		this.bank = bank;
		this.routes = Arrays.asList(routes);
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public PatchType getType()
	{
		return type;
	}

	public Requirement getRequirement()
	{
		return requirement;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getVarbit()
	{
		return varbit;
	}

	/** The main region, used as Time Tracking's storage key. */
	public int getRegionId()
	{
		return regions[0];
	}

	public boolean inRegion(int regionId)
	{
		for (int r : regions)
		{
			if (r == regionId)
			{
				return true;
			}
		}
		return false;
	}

	/** Fairy ring code you can walk to from this patch, or null. */
	public String getNearbyRing()
	{
		return nearbyRing;
	}

	/** Nearest bank, or null if none is close. */
	public String getBank()
	{
		return bank;
	}

	public List<Route> getRoutes()
	{
		return routes;
	}

	/** Same formula the game uses. */
	public static int regionOf(int x, int y)
	{
		return ((x >> 6) << 8) | (y >> 6);
	}

	/**
	 * The patch of this type you're at: matched by the game's region first (exact), then the
	 * closest by tile when several share a region (Fossil Island). Null if none.
	 */
	public static FarmPatch at(int px, int py, PatchType type)
	{
		int region = regionOf(px, py);
		FarmPatch best = null;
		int bestDist = Integer.MAX_VALUE;
		for (FarmPatch p : values())
		{
			if ((type != null && p.type != type) || !p.inRegion(region))
			{
				continue;
			}
			int d = Math.max(Math.abs(p.x - px), Math.abs(p.y - py));
			if (d < bestDist)
			{
				best = p;
				bestDist = d;
			}
		}
		return best;
	}
}
