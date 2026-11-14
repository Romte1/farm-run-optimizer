package com.herbrunhelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Item ids for working out herb profit (game item ids, as in RuneLite's ItemID), and the
 * profit-per-patch sums. Pure Java: prices are passed in.
 */
public final class HerbPrices
{
	/** Rough average herbs per patch with compost, used until your own runs give a better number. */
	public static final double DEFAULT_YIELD = 8.0;

	private static final Map<Crop, int[]> IDS = new EnumMap<>(Crop.class);

	static
	{
		// {grimy herb, seed}
		IDS.put(Crop.GUAM, new int[]{199, 5291});
		IDS.put(Crop.MARRENTILL, new int[]{201, 5292});
		IDS.put(Crop.TARROMIN, new int[]{203, 5293});
		IDS.put(Crop.HARRALANDER, new int[]{205, 5294});
		IDS.put(Crop.RANARR, new int[]{207, 5295});
		IDS.put(Crop.TOADFLAX, new int[]{3049, 5296});
		IDS.put(Crop.IRIT, new int[]{209, 5297});
		IDS.put(Crop.AVANTOE, new int[]{211, 5298});
		IDS.put(Crop.KWUARM, new int[]{213, 5299});
		IDS.put(Crop.HUASCA, new int[]{30094, 30088});
		IDS.put(Crop.SNAPDRAGON, new int[]{3051, 5300});
		IDS.put(Crop.CADANTINE, new int[]{215, 5301});
		IDS.put(Crop.LANTADYME, new int[]{2485, 5302});
		IDS.put(Crop.DWARF_WEED, new int[]{217, 5303});
		IDS.put(Crop.TORSTOL, new int[]{219, 5304});
	}

	private HerbPrices()
	{
	}

	public static int grimyId(Crop crop)
	{
		int[] ids = IDS.get(crop);
		return ids == null ? -1 : ids[0];
	}

	public static int seedId(Crop crop)
	{
		int[] ids = IDS.get(crop);
		return ids == null ? -1 : ids[1];
	}

	/** The item you get when harvesting: grimy herbs, fruit, or logs. */
	public static int produceId(Crop crop)
	{
		int grimy = grimyId(crop);
		return grimy > 0 ? grimy : crop.getIconItemId();
	}

	/** Profit for one patch: yield x herb price - seed price. */
	public static int profitPerPatch(double yield, long herbPrice, long seedPrice)
	{
		return (int) Math.round(yield * herbPrice - seedPrice);
	}

	/** Herbs ordered most profitable first (only crops with a known price). */
	public static List<Crop> rankByProfit(Map<Crop, Integer> profit)
	{
		List<Crop> out = new ArrayList<>(profit.keySet());
		out.sort((a, b) -> Integer.compare(profit.get(b), profit.get(a)));
		return out;
	}

	/** 1234 -> "1.2k", 2500000 -> "2.5m". */
	public static String gp(long value)
	{
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000)
		{
			return sign + String.format("%.1fm", abs / 1_000_000.0);
		}
		if (abs >= 10_000)
		{
			return sign + (abs / 1000) + "k";
		}
		if (abs >= 1000)
		{
			return sign + String.format("%.1fk", abs / 1000.0);
		}
		return sign + abs;
	}
}
