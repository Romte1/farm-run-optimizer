package com.herbrunhelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds a walking route over the loaded part of the map (the game's collision map, in scene
 * coordinates) from the player to a target area, for the built-in guide line. Pure Java.
 *
 * Wall and blocking flags follow RuneLite's CollisionDataFlag.
 */
final class PathGuide
{
	static final int NORTH_WEST = 0x1;
	static final int NORTH = 0x2;
	static final int NORTH_EAST = 0x4;
	static final int EAST = 0x8;
	static final int SOUTH_EAST = 0x10;
	static final int SOUTH = 0x20;
	static final int SOUTH_WEST = 0x40;
	static final int WEST = 0x80;
	static final int FULL = 0x100 | 0x40000 | 0x200000;

	private PathGuide()
	{
	}

	/**
	 * Route from (sx, sy) to the closest walkable tile next to the target box
	 * [minX..maxX] x [minY..maxY] (or onto it, if the target tiles themselves are walkable, like a
	 * fairy ring). If the box is off the loaded map or can't be reached, the route goes to the
	 * reachable tile closest to it. Returns scene tiles from start to end, or an empty list.
	 */
	static List<int[]> find(int[][] flags, int sx, int sy, int minX, int minY, int maxX, int maxY)
	{
		int w = flags.length;
		int h = flags[0].length;
		if (sx < 0 || sy < 0 || sx >= w || sy >= h)
		{
			return Collections.emptyList();
		}
		int[] prev = new int[w * h];
		java.util.Arrays.fill(prev, -2);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int start = sx * h + sy;
		prev[start] = -1;
		queue.add(start);
		int best = start;
		int bestDist = distToBox(sx, sy, minX, minY, maxX, maxY);
		int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
		while (!queue.isEmpty())
		{
			int cur = queue.poll();
			int x = cur / h;
			int y = cur % h;
			int d = distToBox(x, y, minX, minY, maxX, maxY);
			if (d <= 1)
			{
				best = cur;
				bestDist = d;
				break; // next to (or on) the target
			}
			if (d < bestDist)
			{
				bestDist = d;
				best = cur;
			}
			for (int[] dir : dirs)
			{
				int nx = x + dir[0];
				int ny = y + dir[1];
				if (nx < 0 || ny < 0 || nx >= w || ny >= h || prev[nx * h + ny] != -2)
				{
					continue;
				}
				if (canMove(flags, x, y, dir[0], dir[1]))
				{
					prev[nx * h + ny] = cur;
					queue.add(nx * h + ny);
				}
			}
		}
		List<int[]> path = new ArrayList<>();
		for (int at = best; at != -1; at = prev[at])
		{
			path.add(new int[]{at / h, at % h});
		}
		Collections.reverse(path);
		return path.size() > 1 ? path : Collections.emptyList();
	}

	/** Chebyshev distance from a tile to a box (0 inside it). */
	static int distToBox(int x, int y, int minX, int minY, int maxX, int maxY)
	{
		int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
		int dy = y < minY ? minY - y : y > maxY ? y - maxY : 0;
		return Math.max(dx, dy);
	}

	static boolean canMove(int[][] f, int x, int y, int dx, int dy)
	{
		if (dx == 0 || dy == 0)
		{
			return canStep(f, x, y, dx, dy);
		}
		// Diagonal: allowed when both ways round the corner are open.
		return canStep(f, x, y, dx, 0) && canStep(f, x + dx, y, 0, dy)
			&& canStep(f, x, y, 0, dy) && canStep(f, x, y + dy, dx, 0);
	}

	private static boolean canStep(int[][] f, int x, int y, int dx, int dy)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (nx < 0 || ny < 0 || nx >= f.length || ny >= f[0].length)
		{
			return false;
		}
		int a = f[x][y];
		int b = f[nx][ny];
		if ((b & FULL) != 0)
		{
			return false;
		}
		if (dy == 1)
		{
			return (a & NORTH) == 0 && (b & SOUTH) == 0;
		}
		if (dy == -1)
		{
			return (a & SOUTH) == 0 && (b & NORTH) == 0;
		}
		if (dx == 1)
		{
			return (a & EAST) == 0 && (b & WEST) == 0;
		}
		return (a & WEST) == 0 && (b & EAST) == 0;
	}
}
