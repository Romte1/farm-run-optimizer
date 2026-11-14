/*
 * Decoding of farming patch varbit values.
 *
 * The data table (patch-states.tsv) is derived from RuneLite's Time Tracking plugin
 * (PatchImplementation), Copyright (c) 2019 Abex, via Larry: Farming Runs,
 * Copyright (c) 2026, agrahamlincoln. Both BSD 2-Clause; see NOTICE.
 */
package com.herbrunhelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a patch's varbit value into what's growing there. Pure Java so it can be tested.
 */
public final class PatchStates
{
	public enum State
	{
		/** Weeds or bare soil: needs raking/planting. */
		EMPTY,
		GROWING,
		/** Ready to harvest, or (trees) fully grown and waiting for check-health / chopping. */
		READY,
		/** A tree that's been chopped: stump left (clear it, or it regrows). */
		STUMP,
		DISEASED,
		DEAD,
		UNKNOWN
	}

	public static final class Decoded
	{
		public final State state;
		/** Null for weeds, dead herbs (the game doesn't say which) or crops the plugin doesn't plant. */
		public final Crop crop;
		public final int stage;
		/** Tree still standing that must be chopped down before replanting. */
		public final boolean fell;

		Decoded(State state, Crop crop, int stage, boolean fell)
		{
			this.state = state;
			this.crop = crop;
			this.stage = stage;
			this.fell = fell;
		}
	}

	private static final class Row
	{
		final int lo;
		final int hi;
		final String crop;
		final String state;
		final int stage;
		final int step;
		final boolean fell;

		Row(String[] c)
		{
			lo = Integer.parseInt(c[1]);
			hi = Integer.parseInt(c[2]);
			crop = c[3];
			state = c[4];
			stage = Integer.parseInt(c[5]);
			step = Integer.parseInt(c[6]);
			fell = "fell".equals(c[7]);
		}
	}

	private static volatile PatchStates instance;

	private final Map<PatchType, List<Row>> rows = new EnumMap<>(PatchType.class);

	private PatchStates(InputStream in) throws IOException
	{
		for (PatchType t : PatchType.values())
		{
			rows.put(t, new ArrayList<>());
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t");
				for (PatchType t : PatchType.values())
				{
					if (t.getTableName().equals(c[0]))
					{
						rows.get(t).add(new Row(c));
					}
				}
			}
		}
	}

	/** Shared table loaded from the plugin's resources. */
	public static PatchStates get()
	{
		if (instance == null)
		{
			synchronized (PatchStates.class)
			{
				if (instance == null)
				{
					instance = load(PatchStates.class.getResourceAsStream("patch-states.tsv"));
				}
			}
		}
		return instance;
	}

	public static PatchStates load(InputStream in)
	{
		if (in == null)
		{
			throw new IllegalStateException("patch-states.tsv missing from resources");
		}
		try
		{
			return new PatchStates(in);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	public Decoded decode(PatchType type, int value)
	{
		for (Row row : rows.get(type))
		{
			if (value < row.lo || value > row.hi)
			{
				continue;
			}
			int stage = row.stage + row.step * (value - row.lo);
			Crop crop = Crop.fromTableName(row.crop);
			if ("WEEDS".equals(row.crop))
			{
				return new Decoded(State.EMPTY, null, stage, false);
			}
			switch (row.state)
			{
				case "GROWING":
					return new Decoded(State.GROWING, crop, stage, false);
				case "HARVESTABLE":
					if (type.isTreeLike() && type != PatchType.FRUIT_TREE && !row.fell)
					{
						return new Decoded(State.STUMP, crop, stage, false);
					}
					return new Decoded(State.READY, crop, stage, row.fell);
				case "DISEASED":
					return new Decoded(State.DISEASED, crop, stage, false);
				case "DEAD":
					return new Decoded(State.DEAD, crop, stage, false);
				default:
					return new Decoded(State.UNKNOWN, crop, stage, false);
			}
		}
		return new Decoded(State.UNKNOWN, null, 0, false);
	}
}
