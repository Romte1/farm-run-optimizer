package com.herbrunhelper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fairy ring tiles by code (from RuneLite's world map plugin, FairyRingLocation.java). */
final class FairyRings
{
	private static final Map<String, int[]> TILES = new HashMap<>();
	private static final Pattern RING = Pattern.compile("fairy ring ([A-D][I-L][P-S])");

	static
	{
		put("AIQ", 2995, 3112);
		put("AIR", 2699, 3249);
		put("AIS", 1430, 3323);
		put("AJP", 1648, 3011);
		put("AJR", 2779, 3615);
		put("AJS", 2499, 3898);
		put("AKP", 3283, 2704);
		put("AKQ", 2318, 3617);
		put("AKR", 1823, 3539);
		put("AKS", 2570, 2958);
		put("ALP", 2502, 3638);
		put("ALQ", 3598, 3496);
		put("ALR", 3059, 4877);
		put("ALS", 2643, 3497);
		put("BIP", 3409, 3326);
		put("BIQ", 3248, 3095);
		put("BIS", 2635, 3268);
		put("BJP", 2264, 2976);
		put("BJS", 2147, 3069);
		put("BKP", 2384, 3037);
		put("BKR", 3468, 3433);
		put("BKS", 2411, 4436);
		put("BLP", 2432, 5127);
		put("BLR", 2739, 3353);
		put("BLS", 1293, 3495);
		put("CIP", 2512, 3886);
		put("CIQ", 2527, 3129);
		put("CIR", 1303, 3762);
		put("CIS", 1636, 3869);
		put("CJQ", 3178, 2445);
		put("CJR", 2704, 3578);
		put("CKQ", 1358, 2943);
		put("CKR", 2800, 3005);
		put("CKS", 3446, 3472);
		put("CLP", 3081, 3208);
		put("CLR", 2737, 2739);
		put("CLS", 2681, 3083);
		put("DIP", 3036, 4761);
		put("DIS", 3109, 3149);
		put("DJP", 2657, 3232);
		put("DJR", 1452, 3659);
		put("DKP", 2899, 3113);
		put("DKR", 3126, 3496);
		put("DKS", 2743, 3721);
		put("DLP", 2923, 10455);
		put("DLQ", 3422, 3018);
		put("DLR", 2212, 3101);
	}

	private FairyRings()
	{
	}

	private static void put(String code, int x, int y)
	{
		TILES.put(code, new int[]{x, y});
	}

	/** True if a ring stands within a few tiles of this spot. */
	static boolean isRingAt(int x, int y)
	{
		for (int[] t : TILES.values())
		{
			if (Math.abs(t[0] - x) <= 3 && Math.abs(t[1] - y) <= 3)
			{
				return true;
			}
		}
		return false;
	}

	/** {x, y} of the ring, or null. */
	static int[] tile(String code)
	{
		return code == null ? null : TILES.get(code);
	}

	/**
	 * The ring you walk to for this step: named in the directions ("Walk to fairy ring ALQ"),
	 * or the ring you arrived at on the step before ("Back to the ring").
	 */
	static String startRing(RunPlan.RouteStep step, RunPlan.RouteStep previous)
	{
		if (step.teleport == null || step.teleport.getFairyCode() == null || step.teleportLabel == null)
		{
			return null;
		}
		Matcher m = RING.matcher(step.teleportLabel);
		if (m.find())
		{
			return m.group(1);
		}
		if (step.teleportLabel.startsWith("Back to the ring") && previous != null && previous.teleport != null)
		{
			return previous.teleport.getFairyCode();
		}
		return null;
	}
}
