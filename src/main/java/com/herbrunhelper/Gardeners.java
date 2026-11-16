package com.herbrunhelper;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The gardener NPC at each patch (ids from RuneLite's Time Tracking FarmingWorld). */
final class Gardeners
{
	private static final Map<FarmPatch, Integer> IDS = new EnumMap<>(FarmPatch.class);
	static final Set<Integer> ALL = new HashSet<>();

	static
	{
		put(FarmPatch.VARROCK_TREE, 11957);
		put(FarmPatch.LUMBRIDGE_TREE, 2681);
		put(FarmPatch.FALADOR_TREE, 2679);
		put(FarmPatch.TAVERLEY_TREE, 2678);
		put(FarmPatch.GNOME_TREE, 2687);
		put(FarmPatch.NEMUS_RETREAT_TREE, 14514);
		put(FarmPatch.FARMING_GUILD_TREE, 8534);
		put(FarmPatch.GNOME_FRUIT, 2682);
		put(FarmPatch.TREE_GNOME_VILLAGE_FRUIT, 2683);
		put(FarmPatch.CATHERBY_FRUIT, 2670);
		put(FarmPatch.BRIMHAVEN_FRUIT, 2669);
		put(FarmPatch.LLETYA_FRUIT, 2689);
		put(FarmPatch.FARMING_GUILD_FRUIT, 8533);
		put(FarmPatch.KASTORI_FRUIT, 14516);
		put(FarmPatch.FOSSIL_HARDWOOD_EAST, 7754);
		put(FarmPatch.FOSSIL_HARDWOOD_MIDDLE, 7755);
		put(FarmPatch.FOSSIL_HARDWOOD_WEST, 7756);
		put(FarmPatch.LOCUS_OASIS_HARDWOOD, 13401);
		put(FarmPatch.FALADOR_ALLOTMENT_A, 2663);
		put(FarmPatch.FALADOR_ALLOTMENT_B, 2663);
		put(FarmPatch.CATHERBY_ALLOTMENT_A, 2664);
		put(FarmPatch.CATHERBY_ALLOTMENT_B, 2664);
		put(FarmPatch.ARDOUGNE_ALLOTMENT_A, 2665);
		put(FarmPatch.ARDOUGNE_ALLOTMENT_B, 2665);
		put(FarmPatch.MORYTANIA_ALLOTMENT_A, 2666);
		put(FarmPatch.MORYTANIA_ALLOTMENT_B, 2666);
		put(FarmPatch.HOSIDIUS_ALLOTMENT_A, 6921);
		put(FarmPatch.HOSIDIUS_ALLOTMENT_B, 6921);
		put(FarmPatch.VARLAMORE_ALLOTMENT_A, 12766);
		put(FarmPatch.VARLAMORE_ALLOTMENT_B, 12766);
		put(FarmPatch.GUILD_ALLOTMENT_A, 8535);
		put(FarmPatch.GUILD_ALLOTMENT_B, 8535);
	}

	private Gardeners()
	{
	}

	private static void put(FarmPatch patch, int id)
	{
		IDS.put(patch, id);
		ALL.add(id);
	}

	/** The gardener's NPC id for this patch, or -1. */
	static int idFor(FarmPatch patch)
	{
		return IDS.getOrDefault(patch, -1);
	}
}
