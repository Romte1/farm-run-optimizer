package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
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
		Rectangle bounds = widgetItem.getCanvasBounds();
		if (config.bankSupplyTab())
		{
			drawLabel(graphics, plugin.getBankLabel(itemId), bounds);
		}
		if (!config.highlightBank() || !plugin.getHighlightIds().contains(itemId))
		{
			return;
		}
		Color c = config.highlightColor();
		graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
		graphics.fill(bounds);
		graphics.setColor(c);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(bounds);
	}

	/** How many to take and noted / unnoted, underneath the item. */
	private static void drawLabel(Graphics2D g, String label, Rectangle b)
	{
		if (label == null)
		{
			return;
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(label) + 4;
		// Just below the item, so it doesn't cover the icon or the bank's own count.
		int x = b.x + (b.width - w) / 2;
		int y = b.y + b.height + 1;
		g.setColor(new Color(0, 0, 0, 170));
		g.fillRect(x, y, w, fm.getAscent());
		boolean done = label.startsWith("\u2713");
		g.setColor(done ? new Color(120, 230, 95) : label.endsWith("unnoted") ? new Color(255, 214, 90)
			: label.endsWith("noted") ? new Color(130, 200, 255) : Color.WHITE);
		g.drawString(label, x + 2, y + fm.getAscent() - 1);
	}
}
