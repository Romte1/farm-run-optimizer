package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the items you still need to withdraw while the bank is open. */
public class HerbRunBankOverlay extends WidgetItemOverlay
{
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;

	@Inject
	HerbRunBankOverlay(HerbRunPlugin plugin, HerbRunConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightBank() || !plugin.getHighlightIds().contains(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		Color c = config.highlightColor();
		graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
		graphics.fill(bounds);
		graphics.setColor(c);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(bounds);
	}
}
