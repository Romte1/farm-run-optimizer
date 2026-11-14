package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Outlines the teleport spell for your next stop in the spellbook (e.g. Falador Teleport). */
class SpellHighlightOverlay extends Overlay
{
	/** The spellbook interface. */
	private static final int SPELLBOOK_GROUP = 218;
	private static final int MAX_CHILD = 250;

	private final Client client;
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;

	@Inject
	SpellHighlightOverlay(Client client, HerbRunPlugin plugin, HerbRunConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String spell = plugin.getSpellToHighlight();
		if (spell == null)
		{
			return null;
		}
		Widget w = findSpell(spell);
		if (w == null)
		{
			return null;
		}
		Rectangle bounds = w.getBounds();
		if (bounds == null || bounds.width <= 0)
		{
			return null;
		}
		Color c = config.stepColor();
		graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
		graphics.fill(bounds);
		graphics.setColor(c);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(bounds);
		return null;
	}

	/** The spell's icon in the open spellbook, found by its name (it moves between game updates). */
	private Widget findSpell(String spell)
	{
		for (int child = 0; child < MAX_CHILD; child++)
		{
			Widget w = client.getWidget(SPELLBOOK_GROUP, child);
			if (w == null || w.isHidden())
			{
				continue;
			}
			String name = w.getName();
			if (name != null && name.replaceAll("<[^>]*>", "").trim().equalsIgnoreCase(spell))
			{
				return w;
			}
		}
		return null;
	}
}
