package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the inventory item your current step needs (seed, compost, payment...). */
public class HerbRunInventoryOverlay extends WidgetItemOverlay
{
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;
	private final ItemManager itemManager;

	@Inject
	HerbRunInventoryOverlay(HerbRunPlugin plugin, HerbRunConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightInventory() || plugin.getStepItem() == null)
		{
			return;
		}
		// Noted items (gardener payments) canonicalize to the unnoted item's name.
		String name = itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
		if (!plugin.isStepItem(name))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		Color c = config.stepColor();
		graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
		graphics.fill(bounds);
		graphics.setColor(c);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(bounds);
	}
}
