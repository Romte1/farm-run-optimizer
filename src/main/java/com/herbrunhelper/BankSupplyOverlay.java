package com.herbrunhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * While the bank shows the Farm run items: a small box under the Farm run button listing anything
 * the run needs that isn't in your bank. (The button itself is Romte1's FarmBankButton.)
 */
class BankSupplyOverlay extends Overlay
{
	private static final Color BORDER = new Color(20, 18, 14);
	private static final Color SHORT = new Color(255, 95, 85);

	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;
	private final FarmBankButton button;

	@Inject
	BankSupplyOverlay(HerbRunPlugin plugin, HerbRunConfig config, FarmBankButton button)
	{
		this.plugin = plugin;
		this.config = config;
		this.button = button;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Rectangle anchor = button.bounds();
		if (!config.bankSupplyTab() || anchor == null || !button.isActive())
		{
			return null;
		}
		List<String> lines = new ArrayList<>();
		for (HerbRunPlugin.SupplyRow r : plugin.getSupplyRows())
		{
			int short_ = r.need - r.carried - r.inBank;
			if (short_ > 0)
			{
				lines.add(short_ + " x " + r.name);
			}
		}
		if (lines.isEmpty())
		{
			return null;
		}
		lines.add(0, "Not in your bank:");
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int lw = 0;
		for (String l : lines)
		{
			lw = Math.max(lw, fm.stringWidth(l));
		}
		int bw = lw + 12;
		int bh = lines.size() * 13 + 6;
		int bx = anchor.x + anchor.width - bw;
		int by = anchor.y + anchor.height + 3;
		g.setColor(new Color(30, 26, 22, 235));
		g.fillRoundRect(bx, by, bw, bh, 6, 6);
		g.setColor(BORDER);
		g.drawRoundRect(bx, by, bw, bh, 6, 6);
		int ly = by + 13;
		for (int i = 0; i < lines.size(); i++)
		{
			g.setColor(i == 0 ? Color.LIGHT_GRAY : SHORT);
			g.drawString(lines.get(i), bx + 6, ly);
			ly += 13;
		}
		return null;
	}
}
