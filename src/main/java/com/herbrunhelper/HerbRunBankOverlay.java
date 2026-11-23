package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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

	/** A small, clean font for the labels. */
	private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 9);

	/** How many to take and noted / unnoted, a little below the item. */
	private static void drawLabel(Graphics2D g, String label, Rectangle b)
	{
		if (label == null)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(LABEL_FONT);
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(label) + 6;
		int h = fm.getAscent() + 3;
		int x = b.x + (b.width - w) / 2;
		int y = b.y + b.height + 3;
		g.setColor(new Color(0, 0, 0, 150));
		g.fillRoundRect(x, y, w, h, 4, 4);
		boolean done = label.startsWith("\u2713");
		g.setColor(done ? new Color(120, 230, 95) : label.endsWith("unnoted") ? new Color(255, 214, 90)
			: label.endsWith("noted") ? new Color(130, 200, 255) : Color.WHITE);
		g.drawString(label, x + 3, y + fm.getAscent() + 1);
	}
}
