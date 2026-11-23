package com.herbrunhelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;

/**
 * Spaces out the items while the bank shows the Farm run tag: 7 wider columns instead of the bank's
 * 8, and taller rows so the "15 noted" / "3 unnoted" label under each item has room, and the bank's tab
 * divider lines hidden. Only moves the item widgets on screen; each keeps its item, so clicking
 * it withdraws exactly as normal. (Laying out the bank tag's item widgets follows the approach
 * in Romte1's Farm run bank view and Quest Helper.)
 */
final class RunBankLayout
{
	static final int PER_ROW = 7;
	static final int X_SPACING = 56;
	static final int Y_SPACING = 54;
	private static final int TOP = 8;

	private RunBankLayout()
	{
	}

	/** Called on the client thread after the bank has finished building with the run tag open. */
	static void apply(Client client)
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (items == null || items.isHidden())
		{
			return;
		}
		Widget[] children = items.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		List<Widget> shown = new ArrayList<>();
		int startX = Integer.MAX_VALUE;
		for (Widget child : children)
		{
			if (child == null || child.isHidden())
			{
				continue;
			}
			if (child.getItemId() > 0)
			{
				shown.add(child);
				startX = Math.min(startX, child.getOriginalX());
			}
			else if (child.getSpriteId() == SpriteID.TRADEBACKING_DARK)
			{
				child.setHidden(true); // the line between bank tabs
			}
		}
		if (shown.isEmpty())
		{
			return;
		}
		// Keep the bank's own order (top to bottom, left to right).
		shown.sort(Comparator.comparingInt(Widget::getOriginalY).thenComparingInt(Widget::getOriginalX));
		for (int i = 0; i < shown.size(); i++)
		{
			Widget w = shown.get(i);
			w.setOriginalX(startX + (i % PER_ROW) * X_SPACING);
			w.setOriginalY(TOP + (i / PER_ROW) * Y_SPACING);
			w.revalidate();
		}
		int rows = (shown.size() + PER_ROW - 1) / PER_ROW;
		int height = Math.max(items.getHeight(), TOP + rows * Y_SPACING);
		int scroll = Math.min(items.getScrollY(), Math.max(0, height - items.getHeight()));
		items.setScrollHeight(height);
		items.setScrollY(scroll);
		client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scroll);
	}
}
