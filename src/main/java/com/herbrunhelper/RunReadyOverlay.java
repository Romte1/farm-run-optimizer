package com.herbrunhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** A small on-screen box: which runs are ready, diseased patches and your farming contract. */
class RunReadyOverlay extends OverlayPanel
{
	static final class Line
	{
		final String left;
		final String right;
		final Color color;

		Line(String left, String right, Color color)
		{
			this.left = left;
			this.right = right;
			this.color = color;
		}
	}

	private final HerbRunPlugin plugin;

	@Inject
	RunReadyOverlay(HerbRunPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<Line> lines = plugin.getReadyLines();
		if (!plugin.showReadyOverlay() || lines.isEmpty())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Farm runs").color(new Color(255, 200, 60)).build());
		for (Line l : lines)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(l.left).right(l.right).rightColor(l.color).build());
		}
		return super.render(graphics);
	}
}
