package com.herbrunhelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * A row of buttons to ToadFx's channels, all the same light grey. Each opens in your web browser.
 * The marks are simple drawings, not the official logos; a channel's own icon can be dropped in
 * as resources/com/herbrunhelper/social/&lt;name&gt;.png (e.g. tiktok.png) and is used instead.
 */
final class SocialLinks
{
	private static final int SIZE = 22;
	private static final Color BUTTON = new Color(215, 215, 215);
	private static final Color MARK = new Color(40, 40, 40);

	enum Social
	{
		YOUTUBE("YouTube", "youtube", "https://www.youtube.com/@toadfxx"),
		TWITCH("Twitch", "twitch", "https://www.twitch.tv/toadfxx"),
		KICK("Kick", "kick", "https://kick.com/toadfx"),
		TIKTOK("TikTok", "tiktok", "https://www.tiktok.com/@toadfx");

		final String name;
		final String file;
		final String url;

		Social(String name, String file, String url)
		{
			this.name = name;
			this.file = file;
			this.url = url;
		}
	}

	private SocialLinks()
	{
	}

	static JPanel row()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		row.setOpaque(false);
		for (Social s : Social.values())
		{
			JLabel b = new JLabel(new ImageIcon(icon(s)));
			b.setToolTipText("ToadFx on " + s.name);
			b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			b.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LinkBrowser.browse(s.url);
				}
			});
			row.add(b);
		}
		return row;
	}

	/** The channel's own icon if one was added to the plugin, else a grey button with a simple mark. */
	static BufferedImage icon(Social s)
	{
		BufferedImage own = resource(s);
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setColor(BUTTON);
		g.fill(new RoundRectangle2D.Float(0, 0, SIZE, SIZE, 7, 7));
		if (own != null)
		{
			g.drawImage(own.getScaledInstance(SIZE - 6, SIZE - 6, Image.SCALE_SMOOTH), 3, 3, null);
			g.dispose();
			return img;
		}
		g.setColor(MARK);
		switch (s)
		{
			case YOUTUBE:
			{
				Path2D p = new Path2D.Float();
				p.moveTo(8, 6);
				p.lineTo(16, 11);
				p.lineTo(8, 16);
				p.closePath();
				g.fill(p);
				break;
			}
			case TWITCH:
			{
				// A chat bubble with two bars.
				g.setStroke(new BasicStroke(1.8f));
				Path2D p = new Path2D.Float();
				p.moveTo(5, 4.5);
				p.lineTo(17, 4.5);
				p.lineTo(17, 13);
				p.lineTo(13, 16);
				p.lineTo(9.5, 16);
				p.lineTo(7, 18.5);
				p.lineTo(7, 16);
				p.lineTo(5, 16);
				p.closePath();
				g.draw(p);
				g.fillRect(9, 8, 2, 4);
				g.fillRect(13, 8, 2, 4);
				break;
			}
			case KICK:
			{
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
				int w = g.getFontMetrics().stringWidth("K");
				g.drawString("K", (SIZE - w) / 2f, 16);
				break;
			}
			default:
			{
				// A music note.
				g.fill(new Ellipse2D.Float(5.5f, 12.5f, 6, 5));
				g.fillRect(10, 4, 2, 11);
				Path2D flag = new Path2D.Float();
				flag.moveTo(12, 4);
				flag.curveTo(13.5, 6.5, 16.5, 6.5, 17, 9);
				flag.lineTo(17, 10.5);
				flag.curveTo(15.5, 8.8, 13.5, 8.5, 12, 7.5);
				flag.closePath();
				g.fill(flag);
				break;
			}
		}
		g.dispose();
		return img;
	}

	private static BufferedImage resource(Social s)
	{
		try (InputStream in = SocialLinks.class.getResourceAsStream("social/" + s.file + ".png"))
		{
			return in == null ? null : ImageIO.read(in);
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
