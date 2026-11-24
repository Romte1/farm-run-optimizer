package com.herbrunhelper;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Draws a check and icon for items that are already carried but no longer in the bank. Real bank
 * items are left entirely to the bank and to {@link HerbRunBankOverlay}.
 */
class FarmBankViewOverlay extends Overlay
{
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;

	private final Client client;
	private final FarmBankLayout layout;
	private final FarmBankButton button;
	private final ItemManager itemManager;

	@Inject
	FarmBankViewOverlay(Client client, FarmBankLayout layout, FarmBankButton button, ItemManager itemManager)
	{
		this.client = client;
		this.layout = layout;
		this.button = button;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<FarmBankLayout.Draw> draws = layout.getDraws();
		if (!button.isActive() || draws.isEmpty())
		{
			return null;
		}
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (items == null || items.isHidden())
		{
			return null;
		}
		Point base = items.getCanvasLocation();
		if (base == null)
		{
			return null;
		}
		int scroll = items.getScrollY();
		Shape oldClip = graphics.getClip();
		graphics.clip(items.getBounds());
		for (FarmBankLayout.Draw draw : draws)
		{
			int x = base.getX() + draw.x;
			int y = base.getY() + draw.y - scroll;
			AsyncBufferedImage image = itemManager.getImage(draw.itemId);
			if (image != null)
			{
				Composite old = graphics.getComposite();
				if (draw.faded)
				{
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
				}
				graphics.drawImage(image, x, y, null);
				graphics.setComposite(old);
			}
			HerbRunBankOverlay.drawLabel(graphics, draw.label,
				new Rectangle(x, y, ICON_WIDTH, ICON_HEIGHT));
		}
		graphics.setClip(oldClip);
		return null;
	}
}
