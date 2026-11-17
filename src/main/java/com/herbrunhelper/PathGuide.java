package com.herbrunhelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Finds the walking route for the built-in guide line. Pure Java, in world tile coordinates, over
 * any {@link Walkable} map: the whole-world walking map, or the part of the map the game has
 * loaded (scene collision flags) as a fallback.
 *
 * The search is A* in game ticks (every walking step is one tick, a door or shortcut its own time).
 * Each walking stretch is then rewritten into the exact tiles the game's own walking takes when
 * you click its end ({@link #gameWalk}), the way the Shortest Path plugin does it, so the line is
 * where your character really walks.
 */
final class PathGuide
{
	/** A walking map. */
	interface Walkable
	{
		/** Can you take one step from (x, y) by (dx, dy), each -1, 0 or 1? */
		boolean step(int x, int y, int z, int dx, int dy);

		/**
		 * Doors, gates and shortcuts you can use from (x, y): flattened {destX, destY, cost, ...},
		 * or null for none.
		 */
		default int[] links(int x, int y, int z)
		{
			return null;
		}

		/** Is there a wall on the edge between (x, y) and the tile next to it at (dx, dy)? */
		default boolean wall(int x, int y, int z, int dx, int dy)
		{
			return false;
		}
	}

	static final int NORTH = 0x2;
	static final int EAST = 0x8;
	static final int SOUTH = 0x20;
	static final int WEST = 0x80;
	/** Blocked tile: object, floor decoration, blocked floor, or no map data loaded there. */
	static final int FULL = 0x100 | 0x40000 | 0x200000 | 0x1000000;

	/**
	 * The order the game's own walking checks a tile's neighbours: W, E, S, N, SW, SE, NW, NE
	 * (as documented by the Shortest Path plugin's GameWalkOrder).
	 */
	static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
	/** Largest search window, in tiles per side. */
	static final int MAX_WINDOW = 640;

	private PathGuide()
	{
	}

	/** The part of the map the game has loaded, from the scene's collision flags. */
	static Walkable scene(int[][] flags, int baseX, int baseY)
	{
		return new Walkable()
		{
			private int at(int x, int y)
			{
				int sx = x - baseX;
				int sy = y - baseY;
				if (sx < 0 || sy < 0 || sx >= flags.length || sy >= flags[0].length)
				{
					return -1;
				}
				return flags[sx][sy];
			}

			private boolean straight(int x, int y, int dx, int dy)
			{
				int a = at(x, y);
				int b = at(x + dx, y + dy);
				if (a == -1 || b == -1 || (b & FULL) != 0)
				{
					return false;
				}
				return !wall(x, y, 0, dx, dy);
			}

			@Override
			public boolean step(int x, int y, int z, int dx, int dy)
			{
				if (dx == 0 || dy == 0)
				{
					return straight(x, y, dx, dy);
				}
				return straight(x, y, dx, 0) && straight(x + dx, y, 0, dy)
					&& straight(x, y, 0, dy) && straight(x, y + dy, dx, 0);
			}

			@Override
			public boolean wall(int x, int y, int z, int dx, int dy)
			{
				int a = at(x, y);
				int b = at(x + dx, y + dy);
				if (a == -1 || b == -1)
				{
					return false;
				}
				if (dy == 1)
				{
					return (a & NORTH) != 0 || (b & SOUTH) != 0;
				}
				if (dy == -1)
				{
					return (a & SOUTH) != 0 || (b & NORTH) != 0;
				}
				if (dx == 1)
				{
					return (a & EAST) != 0 || (b & WEST) != 0;
				}
				return (a & WEST) != 0 || (b & EAST) != 0;
			}
		};
	}

	/** How far out from the target to look for somewhere to stop, like Shortest Path's goal rule. */
	static final int GOAL_RINGS = 5;

	/**
	 * Walking route from (sx, sy) to the target box [minX..maxX] x [minY..maxY]. Like the Shortest
	 * Path plugin's goal rule: a walkable target you stand on ({@code onto}) is its own goal;
	 * otherwise the route ends on the nearest reachable tile around it, preferring, right beside
	 * it, a side with no wall between, then a side behind a wall, then a corner; failing those,
	 * the nearest reachable tile up to {@link #GOAL_RINGS} tiles out. Returns every step from
	 * start to end, or an empty list when nothing near the target can be reached.
	 */
	static List<int[]> find(Walkable map, int z, int sx, int sy, int minX, int minY, int maxX, int maxY,
		boolean onto, int margin)
	{
		int wx0 = Math.min(sx, minX) - margin;
		int wy0 = Math.min(sy, minY) - margin;
		int w = Math.max(sx, maxX) + margin - wx0 + 1;
		int h = Math.max(sy, maxY) + margin - wy0 + 1;
		if (w > MAX_WINDOW || h > MAX_WINDOW || w <= 0 || h <= 0)
		{
			return Collections.emptyList();
		}
		int n = w * h;
		int[] cost = new int[n];
		int[] prev = new int[n];
		Arrays.fill(cost, Integer.MAX_VALUE);
		Arrays.fill(prev, -1);
		boolean[] closed = new boolean[n];
		Heap open = new Heap();
		int start = (sx - wx0) * h + (sy - wy0);
		cost[start] = 0;
		open.push(start, 0);
		// Standing on a tile the map counts as blocked (a doorway, in a fairy ring...): allow the
		// first step off it, like Shortest Path does.
		boolean stuck = true;
		for (int[] d : DIRS)
		{
			if (map.step(sx, sy, z, d[0], d[1]))
			{
				stuck = false;
				break;
			}
		}

		// Everything you can reach from where you are (within the window), cheapest first.
		while (!open.isEmpty())
		{
			int cur = open.pop();
			if (closed[cur])
			{
				continue;
			}
			closed[cur] = true;
			int x = cur / h + wx0;
			int y = cur % h + wy0;
			for (int[] d : DIRS)
			{
				int lx = x + d[0] - wx0;
				int ly = y + d[1] - wy0;
				if (lx < 0 || ly < 0 || lx >= w || ly >= h)
				{
					continue;
				}
				int next = lx * h + ly;
				if (closed[next] || !(map.step(x, y, z, d[0], d[1]) || (stuck && cur == start)))
				{
					continue;
				}
				int nx = x + d[0];
				int ny = y + d[1];
				if (!onto && nx >= minX && nx <= maxX && ny >= minY && ny <= maxY)
				{
					continue; // the patch itself can't be walked through
				}
				int c = cost[cur] + 1;
				if (c < cost[next])
				{
					cost[next] = c;
					prev[next] = cur;
					open.push(next, c);
				}
			}
			int[] links = map.links(x, y, z);
			if (links != null)
			{
				for (int i = 0; i + 2 < links.length; i += 3)
				{
					int lx = links[i] - wx0;
					int ly = links[i + 1] - wy0;
					if (lx < 0 || ly < 0 || lx >= w || ly >= h)
					{
						continue;
					}
					int next = lx * h + ly;
					if (closed[next])
					{
						continue;
					}
					int c = cost[cur] + links[i + 2];
					if (c < cost[next])
					{
						cost[next] = c;
						prev[next] = cur;
						open.push(next, c);
					}
				}
			}
		}

		// Pick where to stop.
		int goal = -1;
		if (onto)
		{
			goal = cheapest(cost, wx0, wy0, w, h, 0, -1, map, z, minX, minY, maxX, maxY);
		}
		for (int ring = 1; goal < 0 && ring <= GOAL_RINGS; ring++)
		{
			if (ring == 1)
			{
				// Beside it: open side, side behind a wall, then corner.
				for (int tier = 0; goal < 0 && tier < 3; tier++)
				{
					goal = cheapest(cost, wx0, wy0, w, h, 1, tier, map, z, minX, minY, maxX, maxY);
				}
			}
			else
			{
				goal = cheapest(cost, wx0, wy0, w, h, ring, -1, map, z, minX, minY, maxX, maxY);
			}
		}
		if (goal < 0)
		{
			return Collections.emptyList();
		}
		List<int[]> path = new ArrayList<>();
		for (int at = goal; at != -1; at = prev[at])
		{
			path.add(new int[]{at / h + wx0, at % h + wy0});
		}
		Collections.reverse(path);
		return path;
	}

	/**
	 * The cheapest reached tile on the given ring around the box (ring 0: the box itself).
	 * On ring 1, tier 0 = sides with no wall, 1 = sides behind a wall, 2 = corners; -1 = any.
	 */
	private static int cheapest(int[] cost, int wx0, int wy0, int w, int h, int ring, int tier, Walkable map, int z,
		int minX, int minY, int maxX, int maxY)
	{
		int best = -1;
		int bestCost = Integer.MAX_VALUE;
		for (int x = minX - ring; x <= maxX + ring; x++)
		{
			for (int y = minY - ring; y <= maxY + ring; y++)
			{
				if (distToBox(x, y, minX, minY, maxX, maxY) != ring)
				{
					continue;
				}
				int lx = x - wx0;
				int ly = y - wy0;
				if (lx < 0 || ly < 0 || lx >= w || ly >= h)
				{
					continue;
				}
				int i = lx * h + ly;
				if (cost[i] == Integer.MAX_VALUE || cost[i] >= bestCost)
				{
					continue;
				}
				if (tier >= 0)
				{
					boolean side = (x >= minX && x <= maxX) || (y >= minY && y <= maxY);
					if (tier == 2 ? side : !side)
					{
						continue;
					}
					if (tier < 2)
					{
						int dx = x < minX ? 1 : x > maxX ? -1 : 0;
						int dy = y < minY ? 1 : y > maxY ? -1 : 0;
						if (map.wall(x, y, z, dx, dy) != (tier == 1))
						{
							continue;
						}
					}
				}
				best = i;
				bestCost = cost[i];
			}
		}
		return best;
	}

	/** Chebyshev distance from a tile to a box (0 inside it). */
	static int distToBox(int x, int y, int minX, int minY, int maxX, int maxY)
	{
		int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
		int dy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
		return Math.max(dx, dy);
	}

	/**
	 * Rewrites every walking stretch of a route (between doors and shortcuts) into the game's own
	 * walk between its ends: a breadth-first search from the start in the game's neighbour order,
	 * traced back from the end through each tile's first discoverer. That's the path your character
	 * takes when you click the end, and every part of it is also the game's path between its own
	 * tiles, so the line doesn't change as you walk along it.
	 */
	static List<int[]> gameWalk(Walkable map, int z, List<int[]> path)
	{
		if (path.size() <= 2)
		{
			return path;
		}
		List<int[]> out = new ArrayList<>();
		out.add(path.get(0));
		int legStart = 0;
		for (int i = 1; i <= path.size(); i++)
		{
			boolean walkStep = i < path.size() && isStep(map, z, path.get(i - 1), path.get(i));
			if (walkStep)
			{
				continue;
			}
			// path[legStart .. i-1] is one walking stretch.
			int legEnd = i - 1;
			if (legEnd > legStart)
			{
				List<int[]> walk = walkBetween(map, z, path.get(legStart), path.get(legEnd), legEnd - legStart);
				if (walk == null)
				{
					walk = path.subList(legStart, legEnd + 1);
				}
				out.addAll(walk.subList(1, walk.size()));
			}
			if (i < path.size())
			{
				out.add(path.get(i)); // through a door or shortcut
			}
			legStart = i;
		}
		return out;
	}

	private static boolean isStep(Walkable map, int z, int[] a, int[] b)
	{
		int dx = b[0] - a[0];
		int dy = b[1] - a[1];
		return Math.max(Math.abs(dx), Math.abs(dy)) == 1 && map.step(a[0], a[1], z, dx, dy);
	}

	/** The game's walk from a to b, if it is no longer than maxSteps; else null. */
	private static List<int[]> walkBetween(Walkable map, int z, int[] a, int[] b, int maxSteps)
	{
		int r = maxSteps + 1;
		int x0 = Math.min(a[0], b[0]) - r;
		int y0 = Math.min(a[1], b[1]) - r;
		int w = Math.abs(a[0] - b[0]) + 2 * r + 1;
		int h = Math.abs(a[1] - b[1]) + 2 * r + 1;
		if (w > MAX_WINDOW || h > MAX_WINDOW)
		{
			return null;
		}
		int[] prev = new int[w * h];
		int[] dist = new int[w * h];
		Arrays.fill(prev, -2);
		int[] queue = new int[w * h];
		int head = 0;
		int tail = 0;
		int start = (a[0] - x0) * h + (a[1] - y0);
		int goal = (b[0] - x0) * h + (b[1] - y0);
		prev[start] = -1;
		queue[tail++] = start;
		while (head < tail)
		{
			int cur = queue[head++];
			if (cur == goal)
			{
				break;
			}
			if (dist[cur] >= maxSteps)
			{
				continue;
			}
			int x = cur / h + x0;
			int y = cur % h + y0;
			for (int[] d : DIRS)
			{
				int lx = x + d[0] - x0;
				int ly = y + d[1] - y0;
				if (lx < 0 || ly < 0 || lx >= w || ly >= h)
				{
					continue;
				}
				int next = lx * h + ly;
				if (prev[next] != -2 || !map.step(x, y, z, d[0], d[1]))
				{
					continue;
				}
				prev[next] = cur;
				dist[next] = dist[cur] + 1;
				queue[tail++] = next;
			}
		}
		if (prev[goal] == -2)
		{
			return null;
		}
		List<int[]> walk = new ArrayList<>();
		for (int at = goal; at != -1; at = prev[at])
		{
			walk.add(new int[]{at / h + x0, at % h + y0});
		}
		Collections.reverse(walk);
		return walk;
	}

	/** Small binary min-heap of (node, priority). */
	private static final class Heap
	{
		private int[] nodes = new int[1024];
		private int[] prio = new int[1024];
		private int size;

		boolean isEmpty()
		{
			return size == 0;
		}

		void push(int node, int p)
		{
			if (size == nodes.length)
			{
				nodes = Arrays.copyOf(nodes, size * 2);
				prio = Arrays.copyOf(prio, size * 2);
			}
			int i = size++;
			while (i > 0)
			{
				int parent = (i - 1) / 2;
				if (prio[parent] <= p)
				{
					break;
				}
				nodes[i] = nodes[parent];
				prio[i] = prio[parent];
				i = parent;
			}
			nodes[i] = node;
			prio[i] = p;
		}

		int pop()
		{
			int top = nodes[0];
			size--;
			int node = nodes[size];
			int p = prio[size];
			int i = 0;
			while (true)
			{
				int c = 2 * i + 1;
				if (c >= size)
				{
					break;
				}
				if (c + 1 < size && prio[c + 1] < prio[c])
				{
					c++;
				}
				if (prio[c] >= p)
				{
					break;
				}
				nodes[i] = nodes[c];
				prio[i] = prio[c];
				i = c;
			}
			nodes[i] = node;
			prio[i] = p;
			return top;
		}
	}
}
