package com.herbrunhelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * When the guide line is worked out again, ported from the Shortest Path plugin's scheduler
 * (PathScheduler.isNearPath and RefreshCoordinator.offRouteTick, BSD 2-Clause, see NOTICE).
 * <ul>
 *   <li>A route is planned from where you stand when there's a new target.</li>
 *   <li>It then stays exactly where it is on the ground while you're within
 *       {@link #RECALCULATE_DISTANCE} tiles of any tile of it, wherever you wander.</li>
 *   <li>Only once you're that far from every tile of it (you walked off, or teleported) is it
 *       planned again, from where you are now.</li>
 *   <li>When the target's tile is refined (the patch or ring object has loaded) the route is kept
 *       if it already ends beside the refined spot.</li>
 *   <li>Once you reach the target the line goes away until the next target.</li>
 * </ul>
 * Pure logic with no game calls, so it can be tested on its own.
 */
final class RouteSession
{
	/** Shortest Path's default "recalculate distance". */
	static final int RECALCULATE_DISTANCE = 10;

	/** Where the line goes: a box of tiles on a floor, and what it is (a stop, a ring...). */
	static final class Target
	{
		final String kind;
		final int minX;
		final int minY;
		final int maxX;
		final int maxY;
		final int plane;
		/** True when you stand on it (a fairy ring); else the route ends beside it. */
		final boolean onto;
		/** True when the box is the real object; false for a rough, remembered tile. */
		final boolean exact;

		Target(String kind, int minX, int minY, int maxX, int maxY, int plane, boolean onto, boolean exact)
		{
			this.kind = kind;
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.plane = plane;
			this.onto = onto;
			this.exact = exact;
		}

		boolean sameBox(Target o)
		{
			return o != null && minX == o.minX && minY == o.minY && maxX == o.maxX && maxY == o.maxY
				&& plane == o.plane && onto == o.onto;
		}

		@Override
		public String toString()
		{
			return kind + " at " + minX + "," + minY + (maxX != minX || maxY != minY ? " to " + maxX + "," + maxY : "");
		}
	}

	/** Works out a route (each step {x, y, plane}) from a tile to a target; empty if none. */
	interface Planner
	{
		List<int[]> plan(int x, int y, int plane, Target target);
	}

	private Target target;
	private List<int[]> steps = Collections.emptyList();
	private boolean reached;
	private int lastX = Integer.MIN_VALUE;
	private int lastY;
	private int lastPlane;
	private int plans;
	private String why = "";

	/** The route's steps (empty when there's no line). */
	List<int[]> steps()
	{
		return steps;
	}

	boolean reached()
	{
		return reached;
	}

	int plans()
	{
		return plans;
	}

	/** Why the route was last planned (for the status line). */
	String why()
	{
		return why;
	}

	Target target()
	{
		return target;
	}

	/** The line to draw: the route, unless it's been reached or there isn't one. */
	List<int[]> line()
	{
		return reached ? Collections.emptyList() : steps;
	}

	void clear()
	{
		target = null;
		steps = Collections.emptyList();
		reached = false;
		lastX = Integer.MIN_VALUE;
	}

	/**
	 * Called every game tick with where you are and where the line should go.
	 * Returns true if the route was planned again.
	 */
	boolean update(int x, int y, int plane, Target next, Planner planner)
	{
		if (next == null)
		{
			clear();
			return false;
		}
		boolean moved = x != lastX || y != lastY || plane != lastPlane;
		lastX = x;
		lastY = y;
		lastPlane = plane;

		String reason = null;
		if (target == null || !Objects.equals(target.kind, next.kind))
		{
			reason = "new target";
		}
		else if (!target.sameBox(next))
		{
			// Same stop, box refined or moved: keep the route if it still ends right beside it.
			if (steps.isEmpty() || !endsBeside(next))
			{
				reason = "target moved";
			}
		}
		else if (steps.isEmpty() && moved && !reached)
		{
			reason = "no route yet";
		}
		else if (!reached && moved && !nearRoute(x, y, plane))
		{
			reason = "off route";
		}
		target = next;

		boolean planned = false;
		if ("target moved".equals(reason) && !steps.isEmpty() && endsNear(next))
		{
			// The target was only refined a tile or two (its object loaded): keep the line on the
			// ground and just redo its last bit, from where it ended.
			int[] end = steps.get(steps.size() - 1);
			List<int[]> tail = planner.plan(end[0], end[1], PathGuide.plane(end, plane), next);
			if (tail != null && !tail.isEmpty())
			{
				List<int[]> joined = new ArrayList<>(steps);
				joined.addAll(tail.subList(1, tail.size()));
				steps = joined;
				plans++;
				why = "target refined";
				reason = null;
				planned = true;
			}
		}
		if (reason != null)
		{
			List<int[]> found = planner.plan(x, y, plane, next);
			steps = found == null ? Collections.<int[]>emptyList() : found;
			reached = false;
			plans++;
			why = reason;
			planned = true;
		}
		if (!reached && !steps.isEmpty() && atTarget(x, y, plane))
		{
			reached = true;
		}
		return planned;
	}

	/** Shortest Path's isNearPath: within the recalculate distance of some tile of the route. */
	boolean nearRoute(int x, int y, int plane)
	{
		for (int[] t : steps)
		{
			if (PathGuide.plane(t, plane) == plane
				&& Math.max(Math.abs(t[0] - x), Math.abs(t[1] - y)) < RECALCULATE_DISTANCE)
			{
				return true;
			}
		}
		return false;
	}

	/** The route ends within a few tiles of the box, on its floor. */
	private boolean endsNear(Target t)
	{
		int[] end = steps.get(steps.size() - 1);
		return PathGuide.plane(end, t.plane) == t.plane
			&& PathGuide.distToBox(end[0], end[1], t.minX, t.minY, t.maxX, t.maxY) <= REFINE_TILES;
	}

	/** How far a refined target may be from the route's end and still keep the line. */
	static final int REFINE_TILES = 4;

	private boolean endsBeside(Target t)
	{
		int[] end = steps.get(steps.size() - 1);
		if (PathGuide.plane(end, t.plane) != t.plane)
		{
			return false;
		}
		int d = PathGuide.distToBox(end[0], end[1], t.minX, t.minY, t.maxX, t.maxY);
		return t.onto ? d == 0 : d == 1;
	}

	/** Beside the target (or on it, for a ring), or on the route's last tile. */
	private boolean atTarget(int x, int y, int plane)
	{
		int[] end = steps.get(steps.size() - 1);
		if (end[0] == x && end[1] == y && PathGuide.plane(end, plane) == plane)
		{
			return true;
		}
		return plane == target.plane
			&& PathGuide.distToBox(x, y, target.minX, target.minY, target.maxX, target.maxY) <= 1;
	}
}
