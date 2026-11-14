package com.herbrunhelper;

import java.util.EnumMap;
import java.util.Map;

/**
 * Remembers what was planted where and when, and works out countdowns.
 * Pure Java; the plugin persists it per account with {@link #serialize()}.
 */
public final class PatchTimers
{
	/** What we saw at a patch when there's no countdown to show. */
	public enum Status
	{
		EMPTY("empty"),
		WEEDS("weeds"),
		GROWING_UNKNOWN("growing"),
		STUMP("stump"),
		DISEASED("diseased!"),
		DEAD("dead");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public static final class Planting
	{
		public final Crop crop;
		public final long plantedAtMillis;

		Planting(Crop crop, long plantedAtMillis)
		{
			this.crop = crop;
			this.plantedAtMillis = plantedAtMillis;
		}

		public long readyAtMillis()
		{
			return plantedAtMillis + crop.getGrowthMinutes() * 60_000L;
		}

		public long millisLeft(long now)
		{
			return Math.max(0, readyAtMillis() - now);
		}

		public boolean isReady(long now)
		{
			return now >= readyAtMillis();
		}
	}

	private final EnumMap<FarmPatch, Planting> plantings = new EnumMap<>(FarmPatch.class);
	private final EnumMap<FarmPatch, Status> statuses = new EnumMap<>(FarmPatch.class);
	private final EnumMap<FarmPatch, Crop> statusCrops = new EnumMap<>(FarmPatch.class);

	public synchronized void planted(FarmPatch patch, Crop crop, long now)
	{
		plantings.put(patch, new Planting(crop, now));
		statuses.remove(patch);
		statusCrops.remove(patch);
	}

	/** Set a timer from an observed ready time (e.g. from the patch's growth stage). */
	public synchronized void syncReadyAt(FarmPatch patch, Crop crop, long readyAt)
	{
		plantings.put(patch, new Planting(crop, readyAt - crop.getGrowthMinutes() * 60_000L));
		statuses.remove(patch);
		statusCrops.remove(patch);
	}

	/** Record a state with no countdown (empty, diseased, dead, growing-time-unknown). */
	public synchronized void setStatus(FarmPatch patch, Status status, Crop crop)
	{
		plantings.remove(patch);
		statuses.put(patch, status);
		if (crop != null)
		{
			statusCrops.put(patch, crop);
		}
		else
		{
			statusCrops.remove(patch);
		}
	}

	public synchronized Status getStatus(FarmPatch patch)
	{
		return statuses.get(patch);
	}

	public synchronized Crop getStatusCrop(FarmPatch patch)
	{
		return statusCrops.get(patch);
	}

	public synchronized Planting get(FarmPatch patch)
	{
		return plantings.get(patch);
	}

	public synchronized void clear(FarmPatch patch)
	{
		plantings.remove(patch);
		statuses.remove(patch);
		statusCrops.remove(patch);
	}

	public synchronized Map<FarmPatch, Planting> snapshot()
	{
		return new EnumMap<>(plantings);
	}

	/** A patch needs visiting unless something is known to be growing there. */
	public synchronized boolean needsVisit(FarmPatch patch, long now)
	{
		Planting p = plantings.get(patch);
		if (p != null)
		{
			return p.isReady(now);
		}
		return statuses.get(patch) != Status.GROWING_UNKNOWN;
	}

	/** True if any patch has a timer or a status. */
	public synchronized boolean hasAnything()
	{
		return !plantings.isEmpty() || !statuses.isEmpty();
	}

	public synchronized String serialize()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<FarmPatch, Planting> e : plantings.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(e.getKey().name()).append(':').append(e.getValue().crop.name())
				.append(':').append(e.getValue().plantedAtMillis);
		}
		return sb.toString();
	}

	public synchronized void load(String data)
	{
		plantings.clear();
		statuses.clear();
		statusCrops.clear();
		if (data == null || data.isEmpty())
		{
			return;
		}
		for (String entry : data.split(";"))
		{
			String[] parts = entry.split(":");
			if (parts.length != 3)
			{
				continue;
			}
			try
			{
				plantings.put(FarmPatch.valueOf(parts[0]),
					new Planting(Crop.valueOf(parts[1]), Long.parseLong(parts[2])));
			}
			catch (IllegalArgumentException ignored)
			{
				// Patch/crop renamed in a newer version; skip it.
			}
		}
	}

	/** "2h 14m" when an hour or more is left, otherwise "14m 05s". */
	public static String formatDuration(long millis)
	{
		long totalSeconds = Math.max(0, millis) / 1000;
		long h = totalSeconds / 3600;
		long m = (totalSeconds % 3600) / 60;
		long s = totalSeconds % 60;
		if (h > 0)
		{
			return h + "h " + String.format("%02d", m) + "m";
		}
		return m + "m " + String.format("%02d", s) + "s";
	}
}
