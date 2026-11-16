package com.herbrunhelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Walking map of the whole game world, so the guide line can plan a route to places that aren't
 * loaded yet. The data file (walk-map.zip) is the collision map from the Shortest Path plugin
 * by Skretzo (BSD 2-Clause, see NOTICE): one entry per 64x64 region named "regionX_regionY",
 * holding a bit set with four bits per tile per plane: north edge open, east edge open, and two
 * wall markers we don't use.
 */
final class WorldWalkMap implements PathGuide.Walkable
{
	private static final int REGION = 64;
	private static final int BITS_PER_TILE = 4;
	private static final int BITS_PER_PLANE = REGION * REGION * BITS_PER_TILE;

	private final Map<Integer, BitSet> regions;

	private WorldWalkMap(Map<Integer, BitSet> regions)
	{
		this.regions = regions;
	}

	static WorldWalkMap load(InputStream in) throws IOException
	{
		Map<Integer, BitSet> regions = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(in))
		{
			ZipEntry entry;
			byte[] buf = new byte[8192];
			while ((entry = zip.getNextEntry()) != null)
			{
				String[] n = entry.getName().split("_");
				if (n.length != 2)
				{
					continue;
				}
				int rx;
				int ry;
				try
				{
					rx = Integer.parseInt(n[0]);
					ry = Integer.parseInt(n[1]);
				}
				catch (NumberFormatException e)
				{
					continue;
				}
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				int r;
				while ((r = zip.read(buf)) > 0)
				{
					out.write(buf, 0, r);
				}
				regions.put(key(rx, ry), BitSet.valueOf(out.toByteArray()));
			}
		}
		return new WorldWalkMap(regions);
	}

	private static int key(int rx, int ry)
	{
		return (rx << 16) | (ry & 0xFFFF);
	}

	private boolean bit(int x, int y, int z, int flag)
	{
		if (x < 0 || y < 0 || z < 0 || z > 3)
		{
			return false;
		}
		BitSet bits = regions.get(key(x / REGION, y / REGION));
		if (bits == null)
		{
			return false;
		}
		int i = z * BITS_PER_PLANE + ((y % REGION) * REGION + (x % REGION)) * BITS_PER_TILE + flag;
		return bits.get(i);
	}

	/** Can you walk from (x, y) one tile north? */
	boolean north(int x, int y, int z)
	{
		return bit(x, y, z, 0);
	}

	/** Can you walk from (x, y) one tile east? */
	boolean east(int x, int y, int z)
	{
		return bit(x, y, z, 1);
	}

	@Override
	public boolean step(int x, int y, int z, int dx, int dy)
	{
		if (dx == 0)
		{
			return dy > 0 ? north(x, y, z) : north(x, y - 1, z);
		}
		if (dy == 0)
		{
			return dx > 0 ? east(x, y, z) : east(x - 1, y, z);
		}
		// Diagonal: both ways round the corner must be open.
		return step(x, y, z, dx, 0) && step(x + dx, y, z, 0, dy)
			&& step(x, y, z, 0, dy) && step(x, y + dy, z, dx, 0);
	}

	@Override
	public boolean wall(int x, int y, int z, int dx, int dy)
	{
		if (dx == 0)
		{
			return dy > 0 ? bit(x, y, z, 2) : bit(x, y - 1, z, 2);
		}
		return dx > 0 ? bit(x, y, z, 3) : bit(x - 1, y, z, 3);
	}

	boolean hasRegion(int x, int y)
	{
		return regions.containsKey(key(x / REGION, y / REGION));
	}
}
