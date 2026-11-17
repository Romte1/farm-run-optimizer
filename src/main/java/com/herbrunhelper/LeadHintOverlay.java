package com.herbrunhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A big "Dial AIS for Nemus Retreat tree" box at the top of the screen while you're using the
 * fairy ring (or "Spirit tree: pick Gnome Stronghold" at a spirit tree), so you don't need the sidebar.
 */
class LeadHintOverlay extends OverlayPanel
{
	private final HerbRunPlugin plugin;

	@Inject
	LeadHintOverlay(HerbRunPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String hint = plugin.getOnScreenLeadHint();
		String status = plugin.getGuideStatus();
		if (hint == null && status == null)
		{
			return null;
		}
		int width = 150;
		if (hint != null)
		{
			width = Math.max(width, graphics.getFontMetrics().stringWidth(hint) + 20);
			panelComponent.getChildren().add(TitleComponent.builder().text(hint).color(new Color(127, 212, 255)).build());
		}
		if (status != null)
		{
			width = Math.max(width, Math.min(520, graphics.getFontMetrics().stringWidth(status) + 20));
			panelComponent.getChildren().add(net.runelite.client.ui.overlay.components.LineComponent.builder()
				.left("Line: " + status).leftColor(Color.WHITE).build());
		}
		panelComponent.setPreferredSize(new Dimension(width, 0));
		return super.render(graphics);
	}
}
