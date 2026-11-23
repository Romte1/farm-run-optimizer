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
 * A small square with the magic secateurs, right next to the bank's close button, with Romte1's
 * animated garden glow while it's closed.
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
	/** From the bank frame's right edge to just left of the close button (the old tab ended here, by the X). */
	private static final int RIGHT_GAP = 55;

	private final Client client;
	private final HerbRunPlugin plugin;
	private final HerbRunConfig config;
	private final ItemManager itemManager;

	/** Where the tab was last drawn (canvas coordinates), for clicks. Null when not shown. */
	private volatile Rectangle tabBounds;
	private volatile boolean hovered;

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
			hovered = false;
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
		// A small square button with the magic secateurs, right next to the bank's close button.
		int size = 22;
		int x = frame.x + frame.width - RIGHT_GAP - size;
		int y = titleBox != null ? titleBox.y + Math.max(0, (titleBox.height - size) / 2) : frame.y + 4;
		Rectangle tab = new Rectangle(x, y, size, size);
		tabBounds = tab;

		if (config.bankTabGlow() && !open)
		{
			drawGardenGlow(g, tab);
		}
		else if (open)
		{
			// While the run's view is open: a steady green rim instead of the animation.
			g.setColor(new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 60));
			g.setStroke(new BasicStroke(3f));
			g.drawRoundRect(tab.x - 1, tab.y - 1, tab.width + 2, tab.height + 2, 7, 7);
		}
		g.setColor(open ? TAB_OPEN : TAB);
		g.fillRoundRect(tab.x, tab.y, tab.width, tab.height, 6, 6);
		g.setStroke(new BasicStroke(1));
		g.setColor(open || allDone ? DONE : BORDER);
		g.drawRoundRect(tab.x, tab.y, tab.width, tab.height, 6, 6);
		BufferedImage img = itemManager.getImage(MAGIC_SECATEURS);
		if (img != null)
		{
			// Item sprites are 36x32: fit it in the square, keeping its shape.
			g.drawImage(img, tab.x + 1, tab.y + 2, size - 2, size - 4, null);
		}

		// Hovering: what it is and how many of the run's items you carry.
		if (hovered)
		{
			String tip = "Farm run " + done + "/" + rows.size() + (open ? " - click for your whole bank" : " - click to show the run's items");
			int tw = fm.stringWidth(tip) + 10;
			int tx = Math.max(frame.x, tab.x + tab.width - tw);
			int ty = tab.y + tab.height + 3;
			g.setColor(new Color(30, 26, 22, 240));
			g.fillRoundRect(tx, ty, tw, 16, 5, 5);
			g.setColor(BORDER);
			g.drawRoundRect(tx, ty, tw, 16, 5, 5);
			g.setColor(allDone ? DONE : Color.WHITE);
			g.drawString(tip, tx + 5, ty + 12);
		}

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
			int by = tab.y + tab.height + 3 + (hovered ? 19 : 0);
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

	// ------------------------------------------------------------------ animated button
	// The growing-garden animation below is by Romte1 (from his Farm run bank button, pull request
	// #1 on github.com/triggleman/farm-run-optimizer), used here with his permission: a breathing
	// gold sheen and green halo, a slow ring of tiny leaves, and golden fireflies rising from it.

	private static final Color LEAF = new Color(120, 230, 90);
	private static final Color GOLD = new Color(255, 215, 90);
	private static final Color SPORE = new Color(220, 255, 170);

	private static void drawGardenGlow(Graphics2D graphics, Rectangle bounds)
	{
		long now = System.currentTimeMillis();
		double pulse = 0.5 + 0.5 * Math.sin(now / 420.0);
		int cx = bounds.x + bounds.width / 2;
		int cy = bounds.y + bounds.height / 2;

		// Warm the button itself a little, so it visibly lights up and dims.
		int sheen = (int) (24 + 22 * pulse);
		graphics.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), sheen));
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

		// A soft green halo that breathes around the frame.
		java.awt.Stroke oldStroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1.4f));
		for (int ring = 0; ring < 3; ring++)
		{
			int pad = 2 + ring;
			int alpha = (int) ((18 + 28 * pulse) / (ring + 1));
			graphics.setColor(new Color(LEAF.getRed(), LEAF.getGreen(), LEAF.getBlue(), alpha));
			graphics.drawRoundRect(bounds.x - pad, bounds.y - pad,
				bounds.width + pad * 2, bounds.height + pad * 2, 8, 8);
		}
		graphics.setStroke(oldStroke);

		// A slow ring of tiny leaves, alternating in brightness.
		int leaves = 8;
		for (int i = 0; i < leaves; i++)
		{
			double angle = now / 1250.0 + i * Math.PI * 2 / leaves;
			double orbit = 14 + 1.5 * pulse;
			int x = cx + (int) Math.round(Math.cos(angle) * orbit);
			int y = cy + (int) Math.round(Math.sin(angle) * orbit * 0.78);
			int alpha = (int) (90 + 90 * (0.5 + 0.5 * Math.sin(now / 260.0 + i)));
			drawLeaf(graphics, x, y, alpha);
		}

		// Fireflies rising from the bottom edge and fading out.
		for (int i = 0; i < 4; i++)
		{
			double t = ((now % 1900) / 1900.0 + i / 4.0) % 1.0;
			int x = bounds.x + 3 + (int) Math.round((bounds.width - 6) * ((i * 0.37 + 0.1) % 1.0))
				+ (int) Math.round(Math.sin(now / 260.0 + i * 2) * 2);
			int y = bounds.y + bounds.height - 1 - (int) Math.round(t * 18);
			int alpha = Math.max(0, (int) (210 * Math.sin(Math.PI * t)));
			Color colour = i % 2 == 0 ? GOLD : SPORE;
			graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha));
			graphics.fillOval(x, y, 3, 3);
			graphics.setColor(new Color(255, 255, 240, Math.max(0, alpha / 2)));
			graphics.fillRect(x + 1, y + 1, 1, 1);
		}
	}

	private static void drawLeaf(Graphics2D graphics, int x, int y, int alpha)
	{
		alpha = Math.max(0, Math.min(255, alpha));
		graphics.setColor(new Color(LEAF.getRed(), LEAF.getGreen(), LEAF.getBlue(), alpha));
		graphics.fillRect(x, y - 1, 3, 3);
		graphics.fillRect(x - 1, y, 1, 1);
		graphics.fillRect(x + 3, y, 1, 1);
		graphics.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha / 2));
		graphics.fillRect(x + 1, y - 2, 1, 1);
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
		hovered = false;
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
		hovered = onTab(e);
		return e;
	}
}
