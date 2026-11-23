package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small "Farm run" tab on the bank's title bar. Click it to open a quick supply list for the
 * run: each item with how many you carry out of how many you need (green when you have them,
 * white when the rest is in the bank, red when you're short). Click the tab again to close it.
 * Only draws: it never withdraws anything or changes the bank.
 */
class BankSupplyOverlay extends Overlay implements MouseListener
{
	private static final Color TAB = new Color(52, 46, 36, 235);
	private static final Color TAB_OPEN = new Color(74, 92, 48, 240);
	private static final Color BORDER = new Color(20, 18, 14);
	private static final Color PANEL = new Color(30, 28, 24, 235);
	private static final Color HAVE = new Color(110, 220, 90);
	private static final Color IN_BANK = new Color(235, 235, 235);
	private static final Color SHORT = new Color(255, 90, 80);
	private static final Color MUTED = new Color(170, 170, 160);
	private static final int ROW = 22;
	private static final int PANEL_WIDTH = 230;

	private final Client client;
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;
	private final ItemManager itemManager;

	/** Where the tab was last drawn (canvas coordinates), for clicks. Null when not shown. */
	private volatile Rectangle tabBounds;
	/** The open list (clicks on it are swallowed, so you can't withdraw through it by accident). */
	private volatile Rectangle panelBounds;

	@Inject
	BankSupplyOverlay(Client client, HerbRunPlugin plugin, HerbRunConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		List<HerbRunPlugin.SupplyRow> rows = plugin.getSupplyRows();
		if (!config.bankSupplyTab() || bank == null || bank.isHidden() || rows.isEmpty())
		{
			tabBounds = null;
			panelBounds = null;
			return null;
		}
		Rectangle frame = bank.getBounds();
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		Rectangle titleBox = title != null && !title.isHidden() ? title.getBounds() : null;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();

		int done = 0;
		for (HerbRunPlugin.SupplyRow r : rows)
		{
			done += r.carried >= r.need ? 1 : 0;
		}
		boolean open = plugin.isBankSupplyOpen();
		String label = (open ? "▾ " : "▸ ") + "Farm run " + done + "/" + rows.size();
		int w = fm.stringWidth(label) + 14;
		int h = 18;
		int x = frame.x + 8;
		int y = titleBox != null ? titleBox.y + Math.max(0, (titleBox.height - h) / 2) : frame.y + 6;
		Rectangle tab = new Rectangle(x, y, w, h);
		tabBounds = tab;
		g.setColor(open ? TAB_OPEN : TAB);
		g.fillRoundRect(tab.x, tab.y, tab.width, tab.height, 6, 6);
		g.setColor(BORDER);
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(tab.x, tab.y, tab.width, tab.height, 6, 6);
		g.setColor(done == rows.size() ? HAVE : Color.WHITE);
		g.drawString(label, tab.x + 7, tab.y + (h + fm.getAscent()) / 2 - 1);

		if (!open)
		{
			panelBounds = null;
			return null;
		}
		int stops = plugin.getSupplyBankStops();
		int ph = 22 + rows.size() * ROW + (stops > 0 ? 16 : 0) + 6;
		int px = tab.x;
		int py = tab.y + tab.height + 3;
		panelBounds = new Rectangle(px, py, PANEL_WIDTH, ph);
		g.setColor(PANEL);
		g.fillRoundRect(px, py, PANEL_WIDTH, ph, 8, 8);
		g.setColor(BORDER);
		g.drawRoundRect(px, py, PANEL_WIDTH, ph, 8, 8);
		g.setColor(MUTED);
		g.drawString("Bring for this run", px + 8, py + 15);
		int ry = py + 22;
		for (HerbRunPlugin.SupplyRow r : rows)
		{
			if (r.itemId > 0)
			{
				BufferedImage img = itemManager.getImage(r.itemId);
				if (img != null)
				{
					g.drawImage(img, px + 6, ry, 22, 20, null);
				}
			}
			Color c = r.carried >= r.need ? HAVE : r.carried + r.inBank >= r.need ? IN_BANK : SHORT;
			String count = Math.min(r.carried, r.need) + "/" + r.need;
			int cw = fm.stringWidth(count);
			g.setColor(c);
			g.drawString(count, px + PANEL_WIDTH - 8 - cw, ry + 14);
			String name = r.name;
			int room = PANEL_WIDTH - 40 - cw - 12;
			while (name.length() > 3 && fm.stringWidth(name) > room)
			{
				name = name.substring(0, name.length() - 2);
			}
			if (!name.equals(r.name))
			{
				name = name + "…";
			}
			g.drawString(name, px + 34, ry + 14);
			ry += ROW;
		}
		if (stops > 0)
		{
			g.setColor(MUTED);
			g.drawString("+ " + stops + " bank stop" + (stops == 1 ? "" : "s") + " later in the run", px + 8, ry + 12);
		}
		return null;
	}

	private boolean onTab(MouseEvent e)
	{
		Rectangle t = tabBounds;
		return t != null && t.contains(e.getPoint());
	}

	private boolean onPanel(MouseEvent e)
	{
		Rectangle p = panelBounds;
		return p != null && p.contains(e.getPoint());
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (SwingUtilities.isLeftMouseButton(e) && onTab(e))
		{
			plugin.toggleBankSupplyOpen();
			e.consume();
		}
		else if (onPanel(e))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (onTab(e) || onPanel(e))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		if (onTab(e) || onPanel(e))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return e;
	}
}
