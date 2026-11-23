package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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
 * A glowing "Farm run" tab with a magic secateurs icon on the right of the bank's title bar.
 * Click it and the bank shows just this run's items (through RuneLite's Bank Tags), so you
 * withdraw them with normal clicks; click again to go back to your normal bank. Each item is
 * labelled with how many to take and whether to take it noted or unnoted (see
 * HerbRunBankOverlay). Anything the run needs that isn't in your bank is listed under the tab.
 * Only draws and switches the bank view: it never withdraws anything itself.
 */
class BankSupplyOverlay extends Overlay implements MouseListener
{
	static final int MAGIC_SECATEURS = 7409;

	private static final Color TAB = new Color(46, 40, 31, 240);
	private static final Color TAB_OPEN = new Color(64, 96, 40, 245);
	private static final Color GLOW = new Color(140, 255, 90);
	private static final Color BORDER = new Color(20, 18, 14);
	private static final Color SHORT = new Color(255, 95, 85);
	private static final Color DONE = new Color(120, 230, 95);
	/** Room left on the right of the title bar for the bank's own buttons (menu and close). */
	private static final int RIGHT_GAP = 54;

	private final Client client;
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;
	private final ItemManager itemManager;

	/** Where the tab was last drawn (canvas coordinates), for clicks. Null when not shown. */
	private volatile Rectangle tabBounds;

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
			return null;
		}
		Rectangle frame = bank.getBounds();
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		Rectangle titleBox = title != null && !title.isHidden() ? title.getBounds() : null;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();

		int done = 0;
		List<HerbRunPlugin.SupplyRow> missing = new ArrayList<>();
		for (HerbRunPlugin.SupplyRow r : rows)
		{
			done += r.carried >= r.need ? 1 : 0;
			if (r.carried + r.inBank < r.need)
			{
				missing.add(r);
			}
		}
		boolean open = plugin.isRunTagOpen();
		boolean allDone = done == rows.size();
		String label = "Farm run " + done + "/" + rows.size();
		int icon = 16;
		int w = icon + 6 + fm.stringWidth(label) + 12;
		int h = 20;
		int x = frame.x + frame.width - RIGHT_GAP - w;
		int y = titleBox != null ? titleBox.y + Math.max(0, (titleBox.height - h) / 2) : frame.y + 5;
		Rectangle tab = new Rectangle(x, y, w, h);
		tabBounds = tab;

		// A soft pulsing glow, so it's easy to spot.
		if (config.bankTabGlow())
		{
			double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 350.0);
			for (int i = 4; i >= 1; i--)
			{
				int alpha = (int) ((open ? 55 : 35 + 45 * pulse) / i);
				g.setColor(new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), Math.min(255, alpha)));
				g.setStroke(new BasicStroke(i * 2f));
				g.drawRoundRect(tab.x, tab.y, tab.width, tab.height, 8, 8);
			}
		}
		g.setColor(open ? TAB_OPEN : TAB);
		g.fillRoundRect(tab.x, tab.y, tab.width, tab.height, 8, 8);
		g.setStroke(new BasicStroke(1));
		g.setColor(BORDER);
		g.drawRoundRect(tab.x, tab.y, tab.width, tab.height, 8, 8);
		BufferedImage img = itemManager.getImage(MAGIC_SECATEURS);
		if (img != null)
		{
			g.drawImage(img, tab.x + 4, tab.y + 1, icon + 2, icon + 1, null);
		}
		g.setColor(allDone ? DONE : Color.WHITE);
		g.drawString(label, tab.x + icon + 9, tab.y + (h + fm.getAscent()) / 2 - 1);

		// Under the tab, while the run's view is open: anything that isn't in your bank.
		if (open && !missing.isEmpty())
		{
			List<String> lines = new ArrayList<>();
			lines.add("Not in your bank:");
			for (HerbRunPlugin.SupplyRow r : missing)
			{
				lines.add((r.need - r.carried - r.inBank) + " x " + r.name);
			}
			int lw = 0;
			for (String l : lines)
			{
				lw = Math.max(lw, fm.stringWidth(l));
			}
			int bw = lw + 12;
			int bh = lines.size() * 13 + 6;
			int bx = tab.x + tab.width - bw;
			int by = tab.y + tab.height + 3;
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
		}
		return null;
	}

	private boolean onTab(MouseEvent e)
	{
		Rectangle t = tabBounds;
		return t != null && t.contains(e.getPoint());
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (SwingUtilities.isLeftMouseButton(e) && onTab(e))
		{
			plugin.toggleRunTag();
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (onTab(e))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		if (onTab(e))
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
