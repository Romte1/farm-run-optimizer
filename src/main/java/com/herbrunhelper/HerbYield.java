package com.herbrunhelper;

import java.util.EnumMap;
import java.util.Map;

/**
 * Expected herbs per patch, the way the OSRS Wiki works it out (Farming page, "Herb yield", and
 * its herb calculator, Module:Herb Farming calculator):
 * <ul>
 *   <li>A grown patch has 3 harvest lives, plus 1, 2 or 3 for compost, supercompost or ultracompost.</li>
 *   <li>Each pick can save a life. Chance to save = (1 + round(low x (99 - F) / 98 + high x (F - 1) / 98)) / 256,
 *       using the herb's low/high values after item, diary and attas boosts.</li>
 *   <li>Item boost: floor(value x (1 + 0.10 magic secateurs + 0.05 Farming cape)). Diary boost: + a flat
 *       amount (Catherby: Kandarin medium 10, hard 17, elite 25; Hosidius and Farming Guild: Kourend hard 10).</li>
 *   <li>Herbs per patch = lives / (1 - chance to save).</li>
 *   <li>Disease: each of the 3 stages that can die has a (floor(27 / r) + 1) / 128 chance, r = 1, 2, 5, 10 for
 *       none, compost, supercompost, ultracompost. Disease-free patches always survive.</li>
 * </ul>
 * Pure Java, so it's tested on its own.
 */
public final class HerbYield
{
	/** {low, high} chance-to-save values (OSRS Wiki herb calculator data). */
	private static final Map<Crop, int[]> CTS = new EnumMap<>(Crop.class);

	static
	{
		CTS.put(Crop.GUAM, new int[]{25, 80});
		CTS.put(Crop.MARRENTILL, new int[]{28, 80});
		CTS.put(Crop.TARROMIN, new int[]{31, 80});
		CTS.put(Crop.HARRALANDER, new int[]{36, 80});
		CTS.put(Crop.RANARR, new int[]{39, 80});
		CTS.put(Crop.TOADFLAX, new int[]{43, 80});
		CTS.put(Crop.IRIT, new int[]{46, 80});
		CTS.put(Crop.AVANTOE, new int[]{50, 80});
		CTS.put(Crop.KWUARM, new int[]{54, 80});
		CTS.put(Crop.SNAPDRAGON, new int[]{57, 80});
		CTS.put(Crop.HUASCA, new int[]{58, 80});
		CTS.put(Crop.CADANTINE, new int[]{60, 80});
		CTS.put(Crop.LANTADYME, new int[]{64, 80});
		CTS.put(Crop.DWARF_WEED, new int[]{67, 80});
		CTS.put(Crop.TORSTOL, new int[]{71, 80});
	}

	/** Your boosts: level, compost, items. Diaries and disease-free patches are per patch. */
	public static final class Setup
	{
		public int level = 99;
		/** 0 none, 1 compost, 2 supercompost, 3 ultracompost. */
		public int compostTier;
		public boolean magicSecateurs;
		public boolean farmingCape;
	}

	private HerbYield()
	{
	}

	public static boolean known(Crop crop)
	{
		return CTS.containsKey(crop);
	}

	/** Chance (0-1) to save a harvest life on each pick. */
	public static double chanceToSave(Crop crop, Setup s, int diaryBonus)
	{
		int[] cts = CTS.get(crop);
		if (cts == null)
		{
			return 0;
		}
		double item = (s.magicSecateurs ? 0.10 : 0) + (s.farmingCape ? 0.05 : 0);
		int low = (int) Math.floor(cts[0] * (1 + item)) + diaryBonus;
		int high = (int) Math.floor(cts[1] * (1 + item)) + diaryBonus;
		int f = Math.max(1, Math.min(99, s.level));
		long value = 1 + (long) Math.floor(low * (99 - f) / 98.0 + high * (f - 1) / 98.0 + 0.5);
		return Math.min(Math.max(value / 256.0, 0), 1);
	}

	/** Herbs from one grown patch (it didn't die). */
	public static double herbsIfGrown(Crop crop, Setup s, int diaryBonus)
	{
		double save = chanceToSave(crop, s, diaryBonus);
		return save >= 1 ? 0 : (3 + Math.max(0, Math.min(3, s.compostTier))) / (1 - save);
	}

	/** Chance the patch survives to harvest. */
	public static double survival(int compostTier, boolean diseaseFree)
	{
		if (diseaseFree)
		{
			return 1;
		}
		int[] reduction = {1, 2, 5, 10};
		double perStage = (Math.floor(27.0 / reduction[Math.max(0, Math.min(3, compostTier))]) + 1) / 128.0;
		return Math.pow(1 - perStage, 3);
	}

	/** Expected herbs from one patch, counting the chance it dies. */
	public static double expected(Crop crop, Setup s, int diaryBonus, boolean diseaseFree)
	{
		return herbsIfGrown(crop, s, diaryBonus) * survival(s.compostTier, diseaseFree);
	}

	/** Compost tier from the compost's name (the bottomless bucket counts as ultracompost). */
	public static int compostTier(String compost)
	{
		if (compost == null)
		{
			return 0;
		}
		if (compost.startsWith("Ultracompost") || compost.startsWith("Bottomless"))
		{
			return 3;
		}
		if (compost.startsWith("Supercompost"))
		{
			return 2;
		}
		return compost.startsWith("Compost") ? 1 : 0;
	}
}
