package com.herbrunhelper;

import java.util.EnumMap;
import java.util.Map;

/**
 * When crops will be ready. Crops grow on fixed clock ticks (20 min herbs, 40 min trees,
 * 160 min fruit trees, 640 min hardwood) shifted by a per-account offset. The offset is learned
 * by watching a patch change stage while you stand near it (the approach RuneLite's Time
 * Tracking uses). Pure Java so it can be tested.
 */
public final class GrowthClock
{
	private final Map<PatchType, Long> phaseMillis = new EnumMap<>(PatchType.class);

	public synchronized void learn(PatchType type, long changedAt)
	{
		long tick = tickMillis(type);
		phaseMillis.put(type, Math.floorMod(changedAt, tick));
	}

	public synchronized boolean knows(PatchType type)
	{
		return phaseMillis.containsKey(type);
	}

	public synchronized String serialize()
	{
		StringBuilder sb = new StringBuilder();
		phaseMillis.forEach((t, p) -> sb.append(sb.length() > 0 ? ";" : "").append(t.name()).append(':').append(p));
		return sb.toString();
	}

	public synchronized void load(String data)
	{
		phaseMillis.clear();
		if (data == null || data.isEmpty())
		{
			return;
		}
		for (String part : data.split(";"))
		{
			String[] kv = part.split(":");
			try
			{
				phaseMillis.put(PatchType.valueOf(kv[0]), Long.parseLong(kv[1]));
			}
			catch (RuntimeException ignored)
			{
				// old or bad entry
			}
		}
	}

	public static long tickMillis(PatchType type)
	{
		return type.getTickMinutes() * 60_000L;
	}

	/** The next growth tick after now. */
	public synchronized long nextTick(PatchType type, long now)
	{
		long tick = tickMillis(type);
		long phase = phaseMillis.getOrDefault(type, 0L);
		return Math.floorDiv(now - phase, tick) * tick + phase + tick;
	}

	/** A crop's own tick length (potato cactus grows faster than its patch type). */
	public static long tickMillis(Crop crop)
	{
		return crop.getTickMinutes() * 60_000L;
	}

	/** The crop's next growth tick after now (lined up with its patch type's learned timing). */
	public synchronized long nextTick(Crop crop, long now)
	{
		long tick = tickMillis(crop);
		long phase = Math.floorMod(phaseMillis.getOrDefault(crop.getType(), 0L), tick);
		return Math.floorDiv(now - phase, tick) * tick + phase + tick;
	}

	/** Ready time for a crop seen at this growth stage right now. */
	public long readyFromLive(Crop crop, int stage, long now)
	{
		int last = crop.getStages() - 1;
		if (stage >= last)
		{
			return now;
		}
		return nextTick(crop, now) + (long) (last - 1 - stage) * tickMillis(crop);
	}

	/** Ready time for a crop that reached this stage at changedAt. */
	public static long readyFromChange(Crop crop, int stage, long changedAt)
	{
		int last = crop.getStages() - 1;
		return changedAt + (long) Math.max(0, last - stage) * tickMillis(crop);
	}
}
