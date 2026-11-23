package com.herbrunhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A small box at the top of the screen: what to do right now (dial this code, pay the gardener,
 * plant this seed...) and the stop after that, so the sidebar can stay closed. Thanks to Romte1, whose
 * on-screen step box (pull request #1) gave the idea.
 */
class NowNextOverlay extends OverlayPanel
{
	private static final Color TITLE = new Color(120, 200, 90);
	private static final Color NOW = new Color(255, 214, 90);
	private static final Color NEXT = new Color(190, 190, 190);
	private static final int WIDTH = 240;

	private final Client client;
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;

	@Inject
	NowNextOverlay(Client client, HerbRunPlugin plugin, HerbRunConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** Hidden while the bank is open, so it doesn't cover it; back as soon as it closes. */
	private boolean bankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String now = plugin.getNowText();
		if (!config.showNowNext() || now == null || bankOpen())
		{
			return null;
		}
		String stop = plugin.getStopCountText();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Farm run" + (stop == null ? "" : "  " + stop)).color(TITLE).build());
		panelComponent.getChildren().add(LineComponent.builder().left("Now: " + now).leftColor(NOW).build());
		String next = plugin.getNextText();
		if (next != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Next: " + next).leftColor(NEXT).build());
		}
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		return super.render(graphics);
	}
}
