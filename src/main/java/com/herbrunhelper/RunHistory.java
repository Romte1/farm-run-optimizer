package com.herbrunhelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a log of your farm runs: what you harvested, what the seeds cost, and the profit at
 * Grand Exchange prices. A run ends after a while with no farming. Pure Java.
 */
public final class RunHistory
{
	public static final int MAX_RUNS = 30;
	/** A run ends after this long without harvesting or planting. */
	public static final long IDLE_END_MS = 20 * 60_000L;

	public static final class Run
	{
		public long start;
		public long end;
		public final Set<FarmPatch> patches = EnumSet.noneOf(FarmPatch.class);
		/** Produce name -> amount. */
		public final Map<String, Integer> produce = new LinkedHashMap<>();
		public long value;
		public long cost;
		/** Herbs picked and herb patches harvested, to learn your average yield. */
		public int herbsPicked;
		public int herbPatches;

		public long profit()
		{
			return value - cost;
		}

		public boolean isEmpty()
		{
			return patches.isEmpty() && produce.isEmpty();
		}
	}

	private final List<Run> runs = new ArrayList<>();
	private Run current;
	private long lastActivity;
	private final Set<FarmPatch> harvestedHerbs = EnumSet.noneOf(FarmPatch.class);

	public synchronized Run current()
	{
		return current;
	}

	public synchronized List<Run> runs()
	{
		return Collections.unmodifiableList(new ArrayList<>(runs));
	}

	private Run active(long now)
	{
		if (current == null)
		{
			current = new Run();
			current.start = now;
			harvestedHerbs.clear();
		}
		lastActivity = now;
		current.end = now;
		return current;
	}

	/** You picked some produce at a patch. */
	public synchronized void harvested(FarmPatch patch, String name, int qty, long unitPrice, boolean herb, long now)
	{
		Run r = active(now);
		r.patches.add(patch);
		r.produce.merge(name, qty, Integer::sum);
		r.value += qty * Math.max(0L, unitPrice);
		if (herb)
		{
			r.herbsPicked += qty;
			if (harvestedHerbs.add(patch))
			{
				r.herbPatches++;
			}
		}
	}

	/** You planted a seed or sapling. */
	public synchronized void planted(FarmPatch patch, long seedPrice, long now)
	{
		Run r = active(now);
		r.patches.add(patch);
		r.cost += Math.max(0L, seedPrice);
		harvestedHerbs.remove(patch);
	}

	/** Ends the current run if you've stopped farming. Returns true if a run was saved. */
	public synchronized boolean tick(long now)
	{
		if (current != null && now - lastActivity > IDLE_END_MS)
		{
			return finish();
		}
		return false;
	}

	public synchronized boolean finish()
	{
		Run r = current;
		current = null;
		if (r == null || r.isEmpty())
		{
			return false;
		}
		runs.add(0, r);
		while (runs.size() > MAX_RUNS)
		{
			runs.remove(runs.size() - 1);
		}
		return true;
	}

	public synchronized void clear()
	{
		runs.clear();
		current = null;
	}

	/** Your average herbs per patch, or the default until there are a few patches logged. */
	public synchronized double averageHerbYield()
	{
		int herbs = 0;
		int patches = 0;
		for (Run r : runs)
		{
			herbs += r.herbsPicked;
			patches += r.herbPatches;
		}
		return patches >= 3 ? (double) herbs / patches : HerbPrices.DEFAULT_YIELD;
	}

	public synchronized long totalProfit()
	{
		long t = 0;
		for (Run r : runs)
		{
			t += r.profit();
		}
		return t;
	}

	// ---------------------------------------------------------------- saving

	/** One run per line: start|end|value|cost|herbsPicked|herbPatches|PATCH,PATCH|name=qty;name=qty */
	public synchronized String serialize()
	{
		StringBuilder sb = new StringBuilder();
		for (Run r : runs)
		{
			line(sb, r);
		}
		return sb.toString();
	}

	/** The run in progress, so it survives closing the client mid-run. Empty when there's none. */
	public synchronized String serializeCurrent()
	{
		StringBuilder sb = new StringBuilder();
		if (current != null && !current.isEmpty())
		{
			line(sb, current);
		}
		return sb.toString();
	}

	/** Restores the run in progress; one that's been idle too long is filed into the history. */
	public synchronized void loadCurrent(String raw, long now)
	{
		current = null;
		List<Run> parsed = parse(raw);
		if (parsed.isEmpty())
		{
			return;
		}
		Run r = parsed.get(0);
		if (now - r.end > IDLE_END_MS)
		{
			runs.add(0, r);
			while (runs.size() > MAX_RUNS)
			{
				runs.remove(runs.size() - 1);
			}
		}
		else
		{
			current = r;
			lastActivity = r.end;
		}
	}

	private static void line(StringBuilder sb, Run r)
	{
		{
			sb.append(r.start).append('|').append(r.end).append('|').append(r.value).append('|').append(r.cost)
				.append('|').append(r.herbsPicked).append('|').append(r.herbPatches).append('|');
			boolean first = true;
			for (FarmPatch p : r.patches)
			{
				sb.append(first ? "" : ",").append(p.name());
				first = false;
			}
			sb.append('|');
			first = true;
			for (Map.Entry<String, Integer> e : r.produce.entrySet())
			{
				sb.append(first ? "" : ";").append(e.getKey().replace("|", "").replace(";", "").replace("=", ""))
					.append('=').append(e.getValue());
				first = false;
			}
			sb.append('\n');
		}
	}

	public synchronized void load(String raw)
	{
		runs.clear();
		runs.addAll(parse(raw));
	}

	private static List<Run> parse(String raw)
	{
		List<Run> out = new ArrayList<>();
		if (raw == null)
		{
			return out;
		}
		for (String line : raw.split("\n"))
		{
			String[] f = line.split("\\|", -1);
			if (f.length < 8)
			{
				continue;
			}
			try
			{
				Run r = new Run();
				r.start = Long.parseLong(f[0]);
				r.end = Long.parseLong(f[1]);
				r.value = Long.parseLong(f[2]);
				r.cost = Long.parseLong(f[3]);
				r.herbsPicked = Integer.parseInt(f[4]);
				r.herbPatches = Integer.parseInt(f[5]);
				for (String p : f[6].split(","))
				{
					try
					{
						if (!p.isEmpty())
						{
							r.patches.add(FarmPatch.valueOf(p));
						}
					}
					catch (IllegalArgumentException ignored)
					{
						// patch renamed in a later version
					}
				}
				for (String kv : f[7].split(";"))
				{
					int eq = kv.lastIndexOf('=');
					if (eq > 0)
					{
						r.produce.put(kv.substring(0, eq), Integer.parseInt(kv.substring(eq + 1)));
					}
				}
				out.add(r);
			}
			catch (NumberFormatException ignored)
			{
				// skip a damaged line
			}
		}
		return out;
	}
}
