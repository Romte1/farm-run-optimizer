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
 * The search is A* with real distances (a diagonal step costs more than a straight one), so the
 * route is the one that looks shortest, not just the fewest steps. The route is then smoothed:
 * corners are skipped wherever you could walk straight between them, so the line runs in straight
 * stretches instead of zigzagging.
 */
final class PathGuide
{
	/** A walking map. */
	interface Walkable
	{
		/** Can you take one step from (x, y) by (dx, dy), each -1, 0 or 1? */
		boolean step(int x, int y, int z, int dx, int dy);

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

	private static final int STRAIGHT = 10;
	private static final int DIAGONAL = 14;
	private static final int[][] DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
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

	/**
	 * Walking route from (sx, sy) to the target box [minX..maxX] x [minY..maxY]: to a tile right
	 * beside it (sharing an edge, with no wall between), or onto it when {@code onto} is set (a
	 * fairy ring you stand in). Returns the tiles from start to end, every step included, or an
	 * empty list when there's no route within reach.
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
		open.push(start, heuristic(sx, sy, minX, minY, maxX, maxY));
		int goal = -1;
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
			if (isGoal(map, z, x, y, minX, minY, maxX, maxY, onto))
			{
				goal = cur;
				break;
			}
			for (int[] d : DIRS)
			{
				int nx = x + d[0];
				int ny = y + d[1];
				int lx = nx - wx0;
				int ly = ny - wy0;
				if (lx < 0 || ly < 0 || lx >= w || ly >= h)
				{
					continue;
				}
				int next = lx * h + ly;
				if (closed[next])
				{
					continue;
				}
				boolean inBox = nx >= minX && nx <= maxX && ny >= minY && ny <= maxY;
				if (inBox && !onto)
				{
					continue; // the patch itself can't be walked through
				}
				if (!map.step(x, y, z, d[0], d[1]))
				{
					continue;
				}
				int c = cost[cur] + (d[0] != 0 && d[1] != 0 ? DIAGONAL : STRAIGHT);
				if (c < cost[next])
				{
					cost[next] = c;
					prev[next] = cur;
					open.push(next, c + heuristic(nx, ny, minX, minY, maxX, maxY));
				}
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

	private static boolean isGoal(Walkable map, int z, int x, int y, int minX, int minY, int maxX, int maxY, boolean onto)
	{
		boolean inX = x >= minX && x <= maxX;
		boolean inY = y >= minY && y <= maxY;
		if (inX && inY)
		{
			return onto;
		}
		if (inX && y == minY - 1)
		{
			return !map.wall(x, y, z, 0, 1);
		}
		if (inX && y == maxY + 1)
		{
			return !map.wall(x, y, z, 0, -1);
		}
		if (inY && x == minX - 1)
		{
			return !map.wall(x, y, z, 1, 0);
		}
		if (inY && x == maxX + 1)
		{
			return !map.wall(x, y, z, -1, 0);
		}
		return false;
	}

	/** Shortest possible walking cost to the box, ignoring walls (keeps A* exact). */
	private static int heuristic(int x, int y, int minX, int minY, int maxX, int maxY)
	{
		int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
		int dy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
		int lo = Math.min(dx, dy);
		int hi = Math.max(dx, dy);
		int octile = lo * DIAGONAL + (hi - lo) * STRAIGHT;
		return Math.max(0, octile - DIAGONAL);
	}

	/** Chebyshev distance from a tile to a box (0 inside it). */
	static int distToBox(int x, int y, int minX, int minY, int maxX, int maxY)
	{
		int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
		int dy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
		return Math.max(dx, dy);
	}

	/**
	 * Smooths a step-by-step route: from each corner, jumps as far ahead as you could walk in a
	 * straight line. Returns the corners (start and end included).
	 */
	static List<int[]> smooth(Walkable map, int z, List<int[]> path)
	{
		if (path.size() <= 2)
		{
			return path;
		}
		List<int[]> out = new ArrayList<>();
		int i = 0;
		out.add(path.get(0));
		while (i < path.size() - 1)
		{
			int j = i + 1;
			while (j + 1 < path.size() && straightWalk(map, z, path.get(i), path.get(j + 1)))
			{
				j++;
			}
			out.add(path.get(j));
			i = j;
		}
		return out;
	}

	/** Can you walk in a straight line from a to b (tile by tile along the line)? */
	static boolean straightWalk(Walkable map, int z, int[] a, int[] b)
	{
		int x = a[0];
		int y = a[1];
		int dx = Math.abs(b[0] - x);
		int dy = Math.abs(b[1] - y);
		int sx = Integer.signum(b[0] - x);
		int sy = Integer.signum(b[1] - y);
		int err = dx - dy;
		while (x != b[0] || y != b[1])
		{
			int e2 = 2 * err;
			int mx = 0;
			int my = 0;
			if (e2 > -dy)
			{
				err -= dy;
				mx = sx;
			}
			if (e2 < dx)
			{
				err += dx;
				my = sy;
			}
			if (!map.step(x, y, z, mx, my))
			{
				return false;
			}
			x += mx;
			y += my;
		}
		return true;
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
