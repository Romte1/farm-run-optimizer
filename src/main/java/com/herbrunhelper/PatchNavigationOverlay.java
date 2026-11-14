package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Highlights the next patch on the ground. It snaps to the real patch object when one is
 * loaded near the target; otherwise it outlines the target tile.
 */
public class PatchNavigationOverlay extends Overlay
{
	private final Client client;
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;

	@Inject
	PatchNavigationOverlay(Client client, HerbRunPlugin plugin, HerbRunConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		FarmPatch next = plugin.getNextPatch();
		if (!config.highlightPatch() || next == null)
		{
			return null;
		}
		WorldPoint target = plugin.tileFor(next);
		Color color = config.patchColor();
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 50);

		// Heading to a fairy ring first: mark the ring and say what to dial.
		WorldPoint ring = plugin.getNavRing();
		if (ring != null)
		{
			LocalPoint lp = LocalPoint.fromWorld(client, ring);
			if (lp == null)
			{
				return null;
			}
			Polygon tile = Perspective.getCanvasTilePoly(client, lp);
			if (tile != null)
			{
				OverlayUtil.renderPolygon(graphics, tile, color);
			}
			String text = plugin.getNavRingLabel();
			Point textLoc = Perspective.getCanvasTextLocation(client, graphics, lp, text, 150);
			if (textLoc != null)
			{
				OverlayUtil.renderTextLocation(graphics, textLoc, text, color);
			}
			return null;
		}

		// The real patch object (matched by its farming varbit), when it's loaded.
		GameObject best = plugin.objectFor(next);

		LocalPoint labelPoint;
		if (best != null)
		{
			Shape hull = best.getConvexHull();
			if (hull != null)
			{
				graphics.setColor(fill);
				graphics.fill(hull);
				graphics.setColor(color);
				graphics.setStroke(new BasicStroke(2));
				graphics.draw(hull);
			}
			labelPoint = best.getLocalLocation();
		}
		else
		{
			labelPoint = LocalPoint.fromWorld(client, target);
			if (labelPoint == null)
			{
				return null; // not in the loaded area yet
			}
			Polygon tile = Perspective.getCanvasTilePoly(client, labelPoint);
			if (tile != null)
			{
				OverlayUtil.renderPolygon(graphics, tile, color);
			}
		}

		if (labelPoint != null)
		{
			String text = next.getDisplayName();
			Point textLoc = Perspective.getCanvasTextLocation(client, graphics, labelPoint, text, 150);
			if (textLoc != null)
			{
				OverlayUtil.renderTextLocation(graphics, textLoc, text, color);
			}
		}
		return null;
	}
}
