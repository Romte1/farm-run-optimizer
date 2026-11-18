package com.herbrunhelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What the guide line's doors, stairs and shortcuts need (walk-reqs.tsv, built by
 * scripts/build_walk_links.py from the Shortest Path plugin's transport lists): skill levels,
 * finished quests, items carried or worn, and game state (varbits / varplayers). Checks follow
 * Shortest Path's meaning; the code is our own. Pure Java: the game is read through
 * {@link Player}.
 */
final class WalkRequirements
{
	/** What the checks need to know about you. */
	interface Player
	{
		/** Level in a skill by its name ("Agility"), or -1 if unknown. */
		int level(String skill);

		/** Has this quest (by its full name) been finished? */
		boolean questDone(String quest);

		int varbit(int id);

		int varp(int id);

		/** How many of this item you have in your inventory and worn. */
		int carried(int itemId);
	}

	private static final class Req
	{
		final String[] skills;
		final String items;
		final String[] quests;
		final String varbits;
		final String varps;

		Req(String[] c)
		{
			skills = split(c, 1);
			items = c.length > 2 ? c[2].trim() : "";
			quests = split(c, 3);
			varbits = c.length > 4 ? c[4].trim() : "";
			varps = c.length > 5 ? c[5].trim() : "";
		}

		private static String[] split(String[] c, int i)
		{
			return c.length > i && !c[i].trim().isEmpty() ? c[i].trim().split(";") : new String[0];
		}
	}

	/** Index = requirement id; 0 is "nothing needed". */
	private final List<Req> reqs = new ArrayList<>();

	private WalkRequirements()
	{
		reqs.add(null);
	}

	static WalkRequirements empty()
	{
		return new WalkRequirements();
	}

	static WalkRequirements load(InputStream in) throws IOException
	{
		WalkRequirements w = new WalkRequirements();
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = r.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			int id;
			try
			{
				id = Integer.parseInt(c[0].trim());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			while (w.reqs.size() <= id)
			{
				w.reqs.add(null);
			}
			w.reqs.set(id, new Req(c));
		}
		return w;
	}

	int size()
	{
		return reqs.size();
	}

	/** Which requirement ids you meet right now ([0] is always true). */
	boolean[] check(Player p)
	{
		boolean[] ok = new boolean[reqs.size()];
		ok[0] = true;
		for (int i = 1; i < reqs.size(); i++)
		{
			Req r = reqs.get(i);
			ok[i] = r != null && meets(r, p);
		}
		return ok;
	}

	private static boolean meets(Req r, Player p)
	{
		for (String s : r.skills)
		{
			String[] lv = s.trim().split(" ", 2);
			if (lv.length != 2)
			{
				return false;
			}
			int need;
			try
			{
				need = "Max".equals(lv[0]) ? 99 : Integer.parseInt(lv[0]);
			}
			catch (NumberFormatException e)
			{
				return false;
			}
			int have = p.level(lv[1].trim());
			if (have < need)
			{
				return false; // also covers skills we can't read (-1)
			}
		}
		for (String q : r.quests)
		{
			if (!q.trim().isEmpty() && !p.questDone(q.trim()))
			{
				return false;
			}
		}
		return items(r.items, p) && vars(r.varbits, p, true) && vars(r.varps, p, false);
	}

	/** "ids:qty&ids:qty|ids:qty": every &-part needs one of its |-branches. */
	static boolean items(String spec, Player p)
	{
		if (spec.isEmpty())
		{
			return true;
		}
		for (String part : spec.split("&"))
		{
			boolean any = false;
			for (String branch : part.split("\\|"))
			{
				String[] iq = branch.split(":");
				if (iq.length != 2)
				{
					return false;
				}
				int need;
				try
				{
					need = Integer.parseInt(iq[1]);
				}
				catch (NumberFormatException e)
				{
					return false;
				}
				int have = 0;
				for (String id : iq[0].split(","))
				{
					try
					{
						have += p.carried(Integer.parseInt(id.trim()));
					}
					catch (NumberFormatException e)
					{
						return false;
					}
				}
				// Shortest Path: quantity 0 means you must carry none of it.
				if (need == 0 ? have == 0 : have >= need)
				{
					any = true;
					break;
				}
			}
			if (!any)
			{
				return false;
			}
		}
		return true;
	}

	/** "538=1;451>0": every check must hold. = equal, > greater, < smaller, & any of these bits set. */
	static boolean vars(String spec, Player p, boolean varbit)
	{
		if (spec.isEmpty())
		{
			return true;
		}
		for (String v : spec.split(";"))
		{
			if (v.isEmpty())
			{
				continue;
			}
			char op = 0;
			int at = -1;
			for (int i = 1; i < v.length(); i++)
			{
				char c = v.charAt(i);
				if (c == '=' || c == '>' || c == '<' || c == '&')
				{
					op = c;
					at = i;
					break;
				}
			}
			if (at < 0)
			{
				return false;
			}
			int id;
			int want;
			try
			{
				id = Integer.parseInt(v.substring(0, at).trim());
				want = Integer.parseInt(v.substring(at + 1).trim());
			}
			catch (NumberFormatException e)
			{
				return false;
			}
			int have = varbit ? p.varbit(id) : p.varp(id);
			boolean ok;
			switch (op)
			{
				case '=':
					ok = have == want;
					break;
				case '>':
					ok = have > want;
					break;
				case '<':
					ok = have < want;
					break;
				default:
					ok = (have & want) > 0; // mask, as Shortest Path checks it
					break;
			}
			if (!ok)
			{
				return false;
			}
		}
		return true;
	}
}
