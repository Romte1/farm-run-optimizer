package com.herbrunhelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.GrayFilter;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Sidebar panel: run toggles, ready summary, collapsible patch timers, the "take with you"
 * grid, wear tips, a card per stop (gold = next) with its to-do list, bank stop cards,
 * the Travel section and a footer credit.
 */
public class HerbRunPanel extends PluginPanel
{
	private static final String CREDIT = "Made by ToadFx";

	private static final Color GREEN = new Color(90, 200, 90);
	private static final Color YELLOW = new Color(255, 210, 70);
	private static final Color GOLD = new Color(255, 200, 60);
	private static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	private static final Color MUTED = new Color(150, 150, 150);
	private static final Color RED = new Color(230, 80, 70);
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;

	private static final int DRAMEN_STAFF_ICON = 772;
	private static final int LAW_RUNE_ICON = 563;

	/** Items worth wearing instead of carrying, with the tip to show. */
	private static final String[][] WEARABLE_TIPS = {
		{"Magic secateurs", "Wield your magic secateurs for +10% herbs."},
		{"Skills necklace", "Wear your skills necklace to save an inventory slot."},
		{"Farming cape", "Wear your farming cape to save an inventory slot."},
		{"Explorer's ring", "Wear your Explorer's ring to save an inventory slot."},
		{"Ardougne cloak", "Wear your Ardougne cloak to save an inventory slot."},
		{"Dramen staff", "Wield your dramen staff to use fairy rings."},
		{"Lunar staff", "Wield your lunar staff to use fairy rings."},
	};

	private final ItemManager itemManager;
	private final PatchTimers timers;
	private final Runnable onResetTimers;
	private final Consumer<Teleport> onToggleTeleport;
	private final Consumer<Boolean> onUseSpells;
	private final Consumer<Boolean> onUseTabs;
	private final Consumer<Crop> onToggleCrop;
	private final Consumer<FarmPatch> onTogglePatch;
	private final BiConsumer<String, Boolean> onSetting;
	private Map<String, Boolean> settings = Collections.emptyMap();
	private boolean settingsExpanded;
	private FarmPatch hintPatch;
	private String hintAction;
	private String hintItem;

	private final Map<PatchType, JCheckBox> runBoxes = new EnumMap<>(PatchType.class);
	private final JPanel liveSection = new JPanel();
	private final JPanel planSection = new JPanel();
	private final Timer refresh;
	private boolean updatingBoxes;

	private RunPlan plan;
	private Map<String, Integer> carried = Collections.emptyMap();
	private Set<String> worn = Collections.emptySet();
	private Map<String, Integer> nameToId = Collections.emptyMap();
	private FarmPatch nextPatch;
	private Set<Teleport> disabled = EnumSet.noneOf(Teleport.class);
	private boolean useSpells = true;
	private boolean useTabs = true;

	private final Map<PatchType, Boolean> expanded = new EnumMap<>(PatchType.class);
	private boolean travelExpanded;
	private boolean showAllTravel;
	private final Map<PatchType, Boolean> runExpanded = new EnumMap<>(PatchType.class);
	private Set<Crop> offCrops = EnumSet.noneOf(Crop.class);
	private Set<FarmPatch> offPatches = EnumSet.noneOf(FarmPatch.class);
	private Set<PatchType> runs = EnumSet.noneOf(PatchType.class);

	public HerbRunPanel(ItemManager itemManager, PatchTimers timers, Runnable onResetTimers,
		BiConsumer<PatchType, Boolean> onRunChanged, Consumer<Teleport> onToggleTeleport,
		Consumer<Boolean> onUseSpells, Consumer<Boolean> onUseTabs, Consumer<Crop> onToggleCrop,
		Consumer<FarmPatch> onTogglePatch, BiConsumer<String, Boolean> onSetting)
	{
		this.onSetting = onSetting;
		this.onToggleCrop = onToggleCrop;
		this.onTogglePatch = onTogglePatch;
		this.itemManager = itemManager;
		this.timers = timers;
		this.onResetTimers = onResetTimers;
		this.onToggleTeleport = onToggleTeleport;
		this.onUseSpells = onUseSpells;
		this.onUseTabs = onUseTabs;
		for (PatchType t : PatchType.values())
		{
			expanded.put(t, true);
			runExpanded.put(t, false);
		}

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 6, 6, 6));

		Column root = new Column();

		JLabel title = new JLabel("Farm Run Planner");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setIcon(new ImageIcon(ImageUtil.loadImageResource(HerbRunPanel.class, "header.png")));
		title.setIconTextGap(6);
		root.addRow(title, 2);

		JPanel toggles = new JPanel(new GridLayout(2, 2, 0, 0));
		toggles.setOpaque(false);
		String[] labels = {"Herbs", "Trees", "Fruit trees", "Hardwood"};
		for (PatchType t : PatchType.values())
		{
			JCheckBox box = new JCheckBox(labels[t.ordinal()]);
			box.setOpaque(false);
			box.setFocusPainted(false);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.addActionListener(e ->
			{
				if (!updatingBoxes)
				{
					onRunChanged.accept(t, box.isSelected());
				}
			});
			runBoxes.put(t, box);
			toggles.add(box);
		}
		root.addRow(toggles, 6);

		liveSection.setOpaque(false);
		planSection.setOpaque(false);
		root.addRow(liveSection, 0);
		root.addRow(planSection, 0);

		root.addRow(new JSeparator(), 4);
		JLabel credit = new JLabel(CREDIT, SwingConstants.CENTER);
		credit.setFont(FontManager.getRunescapeSmallFont());
		credit.setForeground(MUTED);
		root.addRow(credit, 0);

		add(root, BorderLayout.NORTH);

		Column msg = new Column();
		msg.addRow(wrapped("Log in to see your farm run.", MUTED), 0);
		replace(liveSection, msg);
		refresh = new Timer(1000, e -> rebuildLive());
		refresh.start();
	}

	// ================================================================ called by the plugin

	void stop()
	{
		refresh.stop();
	}

	void setSettings(Map<String, Boolean> settings)
	{
		this.settings = settings;
	}

	/** What the stop you're at needs right now, shown in a banner above the stops. */
	void setStepHint(FarmPatch patch, String action, String item)
	{
		this.hintPatch = patch;
		this.hintAction = action;
		this.hintItem = item;
		rebuildPlan();
	}

	private boolean setting(String key)
	{
		return settings.getOrDefault(key, true);
	}

	void setPicks(Set<Crop> offCrops, Set<FarmPatch> offPatches)
	{
		this.offCrops = offCrops;
		this.offPatches = offPatches;
	}

	void setRuns(Set<PatchType> runs)
	{
		this.runs = runs;
		updatingBoxes = true;
		runBoxes.forEach((t, box) -> box.setSelected(runs.contains(t)));
		updatingBoxes = false;
	}

	void setPlan(RunPlan plan, Map<String, Integer> carried, Set<String> worn, Map<String, Integer> nameToId,
		Set<Teleport> disabled, boolean useSpells, boolean useTabs)
	{
		this.plan = plan;
		this.carried = carried;
		this.worn = worn;
		this.nameToId = nameToId;
		this.disabled = disabled;
		this.useSpells = useSpells;
		this.useTabs = useTabs;
		rebuildLive();
		rebuildPlan();
	}

	void setNextPatch(FarmPatch next)
	{
		this.nextPatch = next;
		rebuildPlan();
	}

	// ================================================================ live section (every second)

	private void rebuildLive()
	{
		if (plan == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		Map<FarmPatch, PatchTimers.Planting> planted = timers.snapshot();
		Column col = new Column();

		Map<PatchType, List<FarmPatch>> byType = new EnumMap<>(PatchType.class);
		for (PatchType type : PatchType.values())
		{
			List<FarmPatch> patches = patchesOfType(type, planted);
			if (!patches.isEmpty())
			{
				byType.put(type, patches);
			}
		}
		byType.forEach((type, patches) -> col.addRow(summaryRow(type, patches, planted, now), 2));

		if (!byType.isEmpty() && setting("showTimers"))
		{
			col.addRow(sectionTitle("Patch timers"), 2);
			byType.forEach((type, patches) -> col.addRow(timerGroup(type, patches, planted, now), 4));
		}

		if (timers.hasAnything())
		{
			JButton reset = new JButton("Reset timers");
			reset.setFont(FontManager.getRunescapeSmallFont());
			reset.setFocusPainted(false);
			reset.addActionListener(e ->
			{
				if (JOptionPane.showConfirmDialog(this, "Clear every patch timer?", "Reset timers",
					JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
				{
					onResetTimers.run();
					rebuildLive();
				}
			});
			col.addRow(reset, 6);
		}

		replace(liveSection, col);
	}

	private List<FarmPatch> patchesOfType(PatchType type, Map<FarmPatch, PatchTimers.Planting> planted)
	{
		List<FarmPatch> out = new ArrayList<>();
		for (FarmPatch p : FarmPatch.values())
		{
			if (p.getType() != type)
			{
				continue;
			}
			boolean inRoute = plan.route.stream().anyMatch(s -> s.patch == p);
			if (inRoute || planted.containsKey(p) || timers.getStatus(p) != null)
			{
				out.add(p);
			}
		}
		return out;
	}

	private JComponent summaryRow(PatchType type, List<FarmPatch> patches,
		Map<FarmPatch, PatchTimers.Planting> planted, long now)
	{
		int ready = 0;
		long soonest = Long.MAX_VALUE;
		int tracked = 0;
		for (FarmPatch p : patches)
		{
			PatchTimers.Planting pl = planted.get(p);
			if (pl == null)
			{
				PatchTimers.Status st = timers.getStatus(p);
				if (st != null)
				{
					tracked++;
					if (st != PatchTimers.Status.GROWING_UNKNOWN)
					{
						ready++;
					}
				}
				continue;
			}
			tracked++;
			if (pl.isReady(now))
			{
				ready++;
			}
			else
			{
				soonest = Math.min(soonest, pl.millisLeft(now));
			}
		}
		String status;
		Color color;
		if (ready > 0)
		{
			status = ready + " need you";
			color = GREEN;
		}
		else if (soonest != Long.MAX_VALUE)
		{
			status = "ready in " + PatchTimers.formatDuration(soonest);
			color = YELLOW;
		}
		else
		{
			status = tracked > 0 ? "growing" : "not planted yet";
			color = MUTED;
		}
		return row(label(type.getDisplayName() + ":", Color.WHITE, false), label(status, color, false));
	}

	private JComponent timerGroup(PatchType type, List<FarmPatch> patches,
		Map<FarmPatch, PatchTimers.Planting> planted, long now)
	{
		int due = 0;
		for (FarmPatch p : patches)
		{
			if (timers.needsVisit(p, now))
			{
				due++;
			}
		}
		boolean open = expanded.get(type);

		Column group = new Column();
		JLabel left = label((open ? "▼  " : "▶  ") + type.getDisplayName(), Color.WHITE, true);
		JLabel right = label(due + " of " + patches.size() + " due", due > 0 ? GREEN : MUTED, false);
		JPanel header = row(left, right);
		header.setOpaque(true);
		header.setBackground(CARD);
		header.setBorder(new EmptyBorder(4, 6, 4, 6));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				expanded.put(type, !open);
				rebuildLive();
			}
		});
		group.addRow(header, 0);

		if (open)
		{
			Column rows = new Column();
			rows.setBorder(new EmptyBorder(3, 8, 3, 6));
			for (FarmPatch p : patches)
			{
				rows.addRow(row(label(shortName(p), p == nextPatch ? GOLD : Color.WHITE, false),
					patchStatus(p, planted.get(p), now)), 1);
			}
			group.addRow(rows, 0);
		}
		return group;
	}

	private JLabel patchStatus(FarmPatch p, PatchTimers.Planting pl, long now)
	{
		if (pl != null)
		{
			JLabel l;
			if (pl.isReady(now))
			{
				l = label(p.getType() == PatchType.HERB ? "ready to pick" : "fully grown", GREEN, false);
			}
			else
			{
				l = label("ready in " + PatchTimers.formatDuration(pl.millisLeft(now)), Color.WHITE, false);
			}
			l.setToolTipText(pl.crop.getDisplayName());
			return l;
		}
		PatchTimers.Status st = timers.getStatus(p);
		if (st == null)
		{
			JLabel l = label("not tracked", MUTED, false);
			l.setToolTipText("Walk past this patch or plant it to start tracking");
			return l;
		}
		Color c = st == PatchTimers.Status.DISEASED || st == PatchTimers.Status.DEAD ? RED
			: st == PatchTimers.Status.GROWING_UNKNOWN ? Color.WHITE : ORANGE;
		JLabel l = label(st.getLabel(), c, false);
		Crop sc = timers.getStatusCrop(p);
		if (sc != null)
		{
			l.setToolTipText(sc.getDisplayName());
		}
		return l;
	}

	// ================================================================ plan section (on change)

	private void rebuildPlan()
	{
		if (plan == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		Column col = new Column();
		col.setBorder(new EmptyBorder(6, 0, 4, 0));

		StringBuilder unlocks = new StringBuilder("Farming " + plan.farmingLevel);
		plan.next.forEach((t, c) ->
		{
			if (plan.route.stream().anyMatch(s -> s.patch.getType() == t))
			{
				unlocks.append(" • ").append(c.getDisplayName()).append(" at ").append(c.getLevel());
			}
		});
		col.addRow(wrapped(unlocks.toString(), MUTED), 6);

		for (String w : plan.warnings)
		{
			col.addRow(wrapped("⚠ " + esc(w), ORANGE), 2);
		}

		// Take with you
		if (!plan.withdrawals.isEmpty())
		{
			col.addRow(sectionTitle("Take with you"), 2);
			col.addRow(itemGrid(plan.withdrawals, true), 2);
			Color fit = plan.slotsNeeded <= 28 ? MUTED : ORANGE;
			col.addRow(wrapped("About " + plan.slotsNeeded + "/28 inventory slots"
				+ (plan.bankStops.isEmpty() ? "" : " • " + plan.bankStops.size() + " bank stop"
				+ (plan.bankStops.size() > 1 ? "s" : "") + " on the way"), fit), 8);
		}

		// Wear tips
		List<JComponent> tips = new ArrayList<>();
		for (RunPlan.Withdrawal w : plan.withdrawals)
		{
			if (worn.contains(w.itemName))
			{
				continue;
			}
			for (String[] tip : WEARABLE_TIPS)
			{
				if (w.itemName.startsWith(tip[0]))
				{
					tips.add(iconCard(idOf(w.itemName), esc(tip[1]), null, YELLOW));
					break;
				}
			}
		}
		if (!tips.isEmpty())
		{
			col.addRow(sectionTitle("Wear"), 2);
			for (JComponent t : tips)
			{
				col.addRow(t, 3);
			}
			col.addRow(spacer(), 4);
		}

		if (hintPatch != null && hintAction != null)
		{
			col.addRow(nowBanner(), 6);
		}

		// Every stop in order: done ones as green ticked rows, the rest as full cards
		// (gold = next), with bank stops where the inventory runs out.
		boolean anyTodo = false;
		int number = 0;
		boolean nextShown = false;
		boolean thenShown = false;
		for (int idx = 0; idx < plan.route.size(); idx++)
		{
			RunPlan.RouteStep step = plan.route.get(idx);
			number++;
			for (RunPlan.BankStop b : plan.bankStops)
			{
				if (b.beforeStep == idx)
				{
					col.addRow(bankCard(b), 4);
				}
			}
			if (!timers.needsVisit(step.patch, now))
			{
				col.addRow(doneCard(step, number, now), 3);
				continue;
			}
			if (!nextShown)
			{
				col.addRow(sectionTitle("Next stop"), 2);
			}
			else if (!thenShown)
			{
				col.addRow(sectionTitle("Then"), 2);
				thenShown = true;
			}
			col.addRow(stopCard(step, number, !nextShown, now), 4);
			nextShown = true;
			anyTodo = true;
		}
		if (!plan.route.isEmpty() && !anyTodo)
		{
			col.addRow(wrapped("Everything is planted and growing. Check back when a timer says ready.", GREEN), 6);
		}
		col.addRow(spacer(), 4);

		col.addRow(sectionTitle("Runs"), 2);
		for (PatchType t : PatchType.values())
		{
			col.addRow(runSection(t, now), 4);
		}
		col.addRow(spacer(), 4);

		if (setting("showTravel"))
		{
			col.addRow(travelSection(), 6);
		}

		col.addRow(settingsSection(), 6);

		replace(planSection, col);
	}

	private JComponent stopCard(RunPlan.RouteStep step, int number, boolean first, long now)
	{
		String travel = esc(step.teleportLabel);
		if (step.teleport != null && step.teleport.getFairyCode() != null)
		{
			String code = step.teleport.getFairyCode();
			travel = travel.replace(code, "<b><font color='#7fd4ff'>" + code + "</font></b>");
		}
		StringBuilder html = new StringBuilder();
		html.append("<b>").append(number).append(". ").append(esc(step.patch.getDisplayName())).append("</b><br>");
		html.append("<font color='#c8c8c8'>").append(travel).append("</font>");
		for (String line : StopWork.describe(step, timers.get(step.patch), timers.getStatus(step.patch), now))
		{
			html.append("<br><font color='#ffb040'>• ").append(esc(line)).append("</font>");
		}

		JPanel card = (JPanel) iconCard(iconIdFor(step.teleport, step.teleportItem), html.toString(),
			first ? GOLD : null, Color.WHITE);

		// Items used at this stop
		List<RunPlan.Withdrawal> items = new ArrayList<>();
		if (step.crop != null)
		{
			items.add(new RunPlan.Withdrawal(step.crop.getSeedName(), 1, ""));
		}
		if (step.compost != null && !step.compost.startsWith("Bottomless"))
		{
			items.add(new RunPlan.Withdrawal(step.compost, 1, ""));
		}
		if (step.paymentItem != null && step.crop != null)
		{
			items.add(new RunPlan.Withdrawal(step.paymentItem, step.paymentQuantity, "gardener payment"));
		}
		if (!items.isEmpty())
		{
			JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
			strip.setOpaque(false);
			for (RunPlan.Withdrawal w : items)
			{
				strip.add(smallIcon(w));
			}
			card.add(strip, BorderLayout.SOUTH);
		}
		return card;
	}

	private JComponent nowBanner()
	{
		int id = idOf(hintItem);
		String html = "<b>NOW \u2022 " + esc(hintPatch.getDisplayName()) + "</b><br>"
			+ "<font color='#ffc83c'>" + esc(hintAction) + (hintItem == null ? "" : ": " + esc(hintItem)) + "</font>"
			+ (hintItem == null ? "" : "<br><font color='#a0a0a0'>Highlighted in your inventory</font>");
		JComponent card = iconCard(id, html, GOLD, Color.WHITE);
		card.setBackground(new Color(58, 50, 28));
		return card;
	}

	private static final String[][] SETTINGS = {
		{"useShortestPath", "Walking path line", "Draw the route to the next patch (needs the Shortest Path plugin)"},
		{"highlightPatch", "Highlight next patch", "Outline the next patch on the ground"},
		{"hintArrow", "Hint arrow", "Yellow arrow over the next patch when it's nearby"},
		{"highlightInventory", "Highlight step items", "Outline the seed / compost / payment your current step needs"},
		{"highlightBank", "Highlight in bank", "Outline what to withdraw when your bank is open"},
		{"notifyReady", "Notify when ready", "Notification when a patch finishes growing"},
		{"chainFairyRings", "Chain fairy rings", "Do fairy-ring patches back to back"},
		{"payGardeners", "Pay gardeners", "Bring payments so gardeners protect your trees"},
		{"useCompost", "Bring compost", "Add compost to the bank list"},
		{"bringRake", "Bring a rake", "Add a rake to the bank list"},
		{"importTimeTracking", "Import Time Tracking", "Use timers RuneLite's Time Tracking already saved"},
		{"useBoostedLevel", "Use boosted Farming", "Plan with your boosted level (e.g. garden pie)"},
		{"staffless", "Lumbridge Elite done", "Fairy rings without a staff"},
		{"harmonyUnlocked", "Morytania Elite done", "Unlocks the Harmony Island herb patch"},
		{"showTimers", "Show patch timers", "Show the timer list at the top"},
		{"showTravel", "Show Travel section", "Show your teleports section"},
	};

	private JComponent settingsSection()
	{
		Column col = new Column();
		JLabel toggle = label((settingsExpanded ? "\u25BC  " : "\u25B6  ") + "Settings", Color.WHITE, true);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				settingsExpanded = !settingsExpanded;
				rebuildPlan();
			}
		});
		col.addRow(toggle, 3);
		if (!settingsExpanded)
		{
			return col;
		}
		Column body = new Column();
		body.setOpaque(true);
		body.setBackground(new Color(34, 34, 34));
		body.setBorder(new EmptyBorder(6, 3, 6, 2));
		for (String[] st : SETTINGS)
		{
			String key = st[0];
			boolean on = setting(key);
			JCheckBox box = new JCheckBox(st[1], on);
			box.setOpaque(false);
			box.setFocusPainted(false);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setForeground(on ? GREEN : MUTED);
			box.setToolTipText(st[2]);
			box.addActionListener(e -> onSetting.accept(key, box.isSelected()));
			body.addRow(box, 1);
		}
		body.addRow(wrapped("Colours and more are in RuneLite's settings for the plugin (wrench icon).", MUTED), 0);
		col.addRow(body, 0);
		return col;
	}

	/** A finished stop: green, ticked, with its countdown. */
	private JComponent doneCard(RunPlan.RouteStep step, int number, long now)
	{
		PatchTimers.Planting pl = timers.get(step.patch);
		String when = pl == null ? "growing" : pl.crop.getDisplayName() + ", ready in "
			+ PatchTimers.formatDuration(pl.millisLeft(now));
		JLabel l = new JLabel("<html><body style='width:140px'>\u2713 <b>" + number + ". "
			+ esc(step.patch.getDisplayName()) + "</b> <font color='#a8e0a8'>" + esc(when) + "</font></body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(GREEN);
		l.setOpaque(true);
		l.setBackground(new Color(36, 58, 36));
		l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, GREEN),
			new EmptyBorder(4, 6, 4, 6)));
		return l;
	}

	/** One run: what you can plant (click to pick) and its patches (tick to include). */
	private JComponent runSection(PatchType type, long now)
	{
		Column col = new Column();
		boolean open = runExpanded.get(type);
		boolean on = runs.contains(type);
		Crop first = null;
		for (Crop c : Crop.values())
		{
			if (c.getType() == type)
			{
				first = c;
				break;
			}
		}
		boolean levelOk = first != null && plan.farmingLevel >= first.getLevel();
		String status = !levelOk ? "needs " + first.getLevel() + " Farming" : on ? "on" : "off";

		JLabel left = label((open ? "\u25BC  " : "\u25B6  ") + type.getDisplayName() + " run", on ? Color.WHITE : MUTED, true);
		JLabel right = label(status, !levelOk ? MUTED : on ? GREEN : MUTED, false);
		JPanel header = row(left, right);
		header.setOpaque(true);
		header.setBackground(CARD);
		header.setBorder(new EmptyBorder(6, 6, 6, 6));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				runExpanded.put(type, !open);
				rebuildPlan();
			}
		});
		col.addRow(header, 0);
		if (!open)
		{
			return col;
		}

		Column body = new Column();
		body.setOpaque(true);
		body.setBackground(new Color(34, 34, 34));
		body.setBorder(new EmptyBorder(8, 3, 10, 2));
		body.addRow(sectionTitle(type == PatchType.HERB ? "Herbs to plant" : "Trees to plant"), 4);
		JPanel crops = new JPanel(new GridLayout(0, 4, 6, 6));
		crops.setOpaque(false);
		for (Crop c : Crop.values())
		{
			if (c.getType() == type)
			{
				crops.add(cropTile(c));
			}
		}
		body.addRow(crops, 8);
		body.addRow(wrapped("Click to pick. The best picked seed you own is planted first.", MUTED), 10);

		body.addRow(sectionTitle("Patches"), 4);
		JPanel patches = new JPanel(new GridLayout(0, 2, 4, 4));
		patches.setOpaque(false);
		for (FarmPatch p : FarmPatch.values())
		{
			if (p.getType() != type)
			{
				continue;
			}
			boolean locked = !plan.unlocked.contains(p) || !levelOk;
			if (locked)
			{
				JLabel l = new JLabel(pickerName(p), new ImageIcon(lockIcon()), SwingConstants.LEFT);
				l.setIconTextGap(4);
				l.setFont(FontManager.getRunescapeSmallFont());
				l.setForeground(new Color(110, 110, 110));
				l.setToolTipText("Locked: needs " + (levelOk ? p.getRequirement().getDescription()
					: first.getLevel() + " Farming"));
				patches.add(l);
				continue;
			}
			boolean picked = !offPatches.contains(p);
			JCheckBox box = new JCheckBox(pickerName(p), picked);
			box.setOpaque(false);
			box.setFocusPainted(false);
			box.setIconTextGap(3);
			box.setBorder(new EmptyBorder(1, 0, 1, 0));
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setForeground(picked ? GREEN : MUTED);
			box.setToolTipText(picked ? "Click to skip this patch" : "Click to include this patch");
			box.addActionListener(e -> onTogglePatch.accept(p));
			patches.add(box);
		}
		body.addRow(patches, 0);
		col.addRow(body, 0);
		return col;
	}

	/** Picked = full colour + green border; not picked = dimmed; locked = dimmed + lock + level. */
	private JComponent cropTile(Crop c)
	{
		boolean locked = plan.farmingLevel < c.getLevel();
		boolean picked = !locked && !offCrops.contains(c);

		JLabel tile = new JLabel();
		tile.setPreferredSize(new Dimension(42, 42));
		tile.setHorizontalAlignment(SwingConstants.CENTER);
		tile.setOpaque(true);
		tile.setBackground(picked ? new Color(40, 56, 40) : CARD);
		tile.setBorder(BorderFactory.createLineBorder(picked ? GREEN : new Color(64, 64, 64), picked ? 2 : 1));

		AsyncBufferedImage img = itemManager.getImage(c.getIconItemId());
		float alpha = picked ? 1f : locked ? 0.3f : 0.45f;
		Runnable paint = () -> tile.setIcon(new ImageIcon(cropIcon(img, alpha, locked ? c.getLevel() : -1)));
		paint.run();
		img.onLoaded(paint);

		if (locked)
		{
			tile.setToolTipText(c.getDisplayName() + " - locked, needs " + c.getLevel() + " Farming");
		}
		else
		{
			tile.setToolTipText(c.getDisplayName() + " (level " + c.getLevel() + ") - "
				+ (picked ? "picked, click to stop planting it" : "click to plant it"));
			tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tile.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onToggleCrop.accept(c);
				}
			});
		}
		return tile;
	}

	/** The crop picture at the given opacity; locked crops get a padlock and their level. */
	private static java.awt.image.BufferedImage cropIcon(java.awt.image.BufferedImage img, float alpha, int lockedLevel)
	{
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(36, 36, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
		g.drawImage(img, (36 - img.getWidth()) / 2, (36 - img.getHeight()) / 2, null);
		g.setComposite(java.awt.AlphaComposite.SrcOver);
		if (lockedLevel > 0)
		{
			g.drawImage(lockIcon(), 24, 0, null);
			g.setFont(FontManager.getRunescapeSmallFont());
			String lvl = String.valueOf(lockedLevel);
			g.setColor(Color.BLACK);
			g.drawString(lvl, 2, 35);
			g.setColor(YELLOW);
			g.drawString(lvl, 1, 34);
		}
		g.dispose();
		return out;
	}

	private static java.awt.image.BufferedImage lockImage;

	/** A small gold padlock, drawn once. */
	private static java.awt.image.BufferedImage lockIcon()
	{
		if (lockImage == null)
		{
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(11, 12, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = img.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new java.awt.BasicStroke(1.6f));
			g.setColor(new Color(40, 30, 10));
			g.drawArc(2, 0, 7, 8, 0, 180);
			g.setColor(new Color(200, 170, 80));
			g.drawArc(2, 1, 7, 7, 0, 180);
			g.setColor(new Color(40, 30, 10));
			g.fillRoundRect(0, 5, 11, 7, 2, 2);
			g.setColor(new Color(230, 190, 70));
			g.fillRoundRect(1, 6, 9, 5, 2, 2);
			g.setColor(new Color(60, 45, 10));
			g.fillRect(5, 7, 1, 3);
			g.dispose();
			lockImage = img;
		}
		return lockImage;
	}

	private JComponent bankCard(RunPlan.BankStop b)
	{
		JPanel card = new JPanel(new BorderLayout(0, 3));
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, ORANGE),
			new EmptyBorder(5, 6, 5, 6)));
		JLabel text = new JLabel("<html><body style='width:140px'><b>Bank at " + esc(b.bank) + "</b><br>"
			+ "<font color='#c8c8c8'>Your inventory is full by here. Withdraw:</font></body></html>");
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(Color.WHITE);
		card.add(text, BorderLayout.NORTH);
		card.add(itemGrid(b.withdrawals, false), BorderLayout.CENTER);
		return card;
	}

	private JComponent itemGrid(List<RunPlan.Withdrawal> list, boolean showCarried)
	{
		JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
		grid.setOpaque(false);
		for (RunPlan.Withdrawal w : list)
		{
			grid.add(itemTile(w, showCarried));
		}
		return grid;
	}

	private JComponent itemTile(RunPlan.Withdrawal w, boolean showCarried)
	{
		boolean done = showCarried && carried.getOrDefault(w.itemName, 0) >= w.quantity;
		int id = idOf(w.itemName);

		JLabel tile = new JLabel();
		tile.setPreferredSize(new Dimension(42, 38));
		tile.setHorizontalAlignment(SwingConstants.CENTER);
		tile.setOpaque(true);
		tile.setBackground(CARD);
		Color border = done ? GREEN : id > 0 ? ORANGE : RED;
		tile.setBorder(BorderFactory.createLineBorder(border, done ? 2 : 1));

		String tip = w.quantity + " x " + w.itemName + (w.note.isEmpty() ? "" : " (" + w.note + ")");
		if (done)
		{
			tip += " - carrying";
		}
		else if (id <= 0)
		{
			tip += " - not in your bank";
		}
		tile.setToolTipText(tip);

		if (id > 0)
		{
			itemManager.getImage(id, w.quantity, w.quantity > 1).addTo(tile);
		}
		else
		{
			tile.setText("<html><center>" + w.quantity + "<br>?</center></html>");
			tile.setFont(FontManager.getRunescapeSmallFont());
			tile.setForeground(MUTED);
		}
		return tile;
	}

	private JComponent smallIcon(RunPlan.Withdrawal w)
	{
		int id = idOf(w.itemName);
		JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(34, 30));
		l.setToolTipText(w.quantity + " x " + w.itemName + (w.note.isEmpty() ? "" : " (" + w.note + ")"));
		if (id > 0)
		{
			itemManager.getImage(id, w.quantity, w.quantity > 1).addTo(l);
		}
		else
		{
			l.setText(w.quantity + "x");
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(MUTED);
		}
		return l;
	}

	private int idOf(String itemName)
	{
		Integer id = itemName == null ? null : nameToId.get(itemName);
		return id == null ? -1 : id;
	}

	/** Picture for a teleport: the owned item, else a known icon, else a rune/staff. */
	private int iconIdFor(Teleport t, String ownedItem)
	{
		if (ownedItem != null && nameToId.containsKey(ownedItem))
		{
			return nameToId.get(ownedItem);
		}
		if (t == null)
		{
			return -1;
		}
		if (t.getKind() == Teleport.Kind.FAIRY_RING)
		{
			return DRAMEN_STAFF_ICON;
		}
		if (t.getKind() == Teleport.Kind.SPELL)
		{
			return LAW_RUNE_ICON;
		}
		return t.getIconItemId();
	}

	// ================================================================ travel

	private JComponent travelSection()
	{
		Column col = new Column();
		JLabel toggle = label((travelExpanded ? "▼  " : "▶  ") + "Travel", Color.WHITE, true);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				travelExpanded = !travelExpanded;
				rebuildPlan();
			}
		});
		col.addRow(toggle, 3);
		if (!travelExpanded)
		{
			return col;
		}

		col.addRow(optionBox("Use spells (runes)", useSpells,
			"Teleport with standard spellbook spells. Runes get added to the bank list.", onUseSpells), 0);
		col.addRow(optionBox("Use teleport tabs", useTabs,
			"Use teleport tablets. If spells are on too, tabs are only used where you can't cast the spell.", onUseTabs), 4);
		JCheckBox all = new JCheckBox("Show all travel methods", showAllTravel);
		all.setOpaque(false);
		all.setFocusPainted(false);
		all.setFont(FontManager.getRunescapeSmallFont());
		all.setToolTipText("Also list tabs, spells and fairy rings, not just your items");
		all.addActionListener(e ->
		{
			showAllTravel = all.isSelected();
			rebuildPlan();
		});
		col.addRow(all, 4);
		col.addRow(wrapped("Bright green = used on this run. Dim green = available. Grey = not unlocked/owned yet "
			+ "(hover to see why). Red = turned off. Click to turn off/on.", MUTED), 4);

		Teleport.Kind[] kinds = showAllTravel ? Teleport.Kind.values()
			: new Teleport.Kind[]{Teleport.Kind.ITEM, Teleport.Kind.OTHER};
		for (Teleport.Kind kind : kinds)
		{
			JPanel grid = new JPanel(new GridLayout(0, 4, 4, 4));
			grid.setOpaque(false);
			for (Teleport t : Teleport.values())
			{
				if (t.getKind() == kind)
				{
					grid.add(teleportTile(t));
				}
			}
			col.addRow(sectionTitle(kind.getTitle()), 1);
			col.addRow(grid, 4);
		}
		return col;
	}

	private JCheckBox optionBox(String text, boolean selected, String tooltip, Consumer<Boolean> onChange)
	{
		JCheckBox box = new JCheckBox(text, selected);
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setToolTipText(tooltip);
		box.addActionListener(e -> onChange.accept(box.isSelected()));
		return box;
	}

	private JComponent teleportTile(Teleport t)
	{
		Teleport.Availability av = plan.availability.get(t);
		boolean available = av != null && av.available;
		boolean off = disabled.contains(t);
		boolean usable = available && !off;
		boolean used = plan.route.stream().anyMatch(s -> s.teleport == t);

		JLabel tile = new JLabel();
		tile.setPreferredSize(new Dimension(42, 38));
		tile.setHorizontalAlignment(SwingConstants.CENTER);
		tile.setOpaque(true);
		tile.setBackground(usable ? CARD : ColorScheme.DARK_GRAY_COLOR);
		Color border = used ? GREEN : usable ? new Color(70, 110, 70) : off && available ? RED : new Color(70, 70, 70);
		tile.setBorder(BorderFactory.createLineBorder(border, used ? 2 : 1));
		tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		boolean textOnly = t.getKind() == Teleport.Kind.FAIRY_RING || t.getKind() == Teleport.Kind.SPELL;
		int iconId = textOnly ? -1 : iconIdFor(t, av == null ? null : av.ownedItem);
		if (iconId > 0)
		{
			AsyncBufferedImage img = itemManager.getImage(iconId);
			if (usable)
			{
				img.addTo(tile);
			}
			else
			{
				Runnable grey = () -> tile.setIcon(new ImageIcon(GrayFilter.createDisabledImage(img)));
				grey.run();
				img.onLoaded(grey);
			}
		}
		else
		{
			boolean code = t.getFairyCode() != null;
			tile.setText(t.getShortLabel());
			tile.setFont(code ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
			tile.setForeground(!usable ? MUTED : code ? new Color(127, 212, 255) : Color.WHITE);
		}

		String reason = av == null ? "" : av.reason;
		tile.setToolTipText("<html><b>" + esc(t.getLabel()) + "</b><br>"
			+ (available ? esc(reason) : "<font color='#e65046'>" + esc(reason) + "</font>") + "<br>"
			+ (used ? "<font color='#5ac85a'>Used on this run</font><br>" : "")
			+ (off ? "Turned OFF - click to turn on" : "Click to turn off") + "</html>");
		tile.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onToggleTeleport.accept(t);
			}
		});
		return tile;
	}

	// ================================================================ small helpers

	/** A dark card with an optional item icon on the left and HTML text. */
	private JComponent iconCard(int iconId, String html, Color accent, Color textColor)
	{
		JPanel card = new JPanel(new BorderLayout(6, 2));
		card.setBackground(CARD);
		EmptyBorder pad = new EmptyBorder(5, 6, 5, 6);
		card.setBorder(accent == null ? pad
			: BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, accent), pad));

		if (iconId > 0)
		{
			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(36, 32));
			icon.setVerticalAlignment(SwingConstants.TOP);
			itemManager.getImage(iconId).addTo(icon);
			card.add(icon, BorderLayout.WEST);
		}

		JLabel text = new JLabel("<html><body style='width:" + (iconId > 0 ? 105 : 145) + "px'>"
			+ html + "</body></html>");
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(textColor);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private static void replace(JPanel holder, JComponent content)
	{
		holder.removeAll();
		holder.setLayout(new BorderLayout());
		holder.add(content, BorderLayout.CENTER);
		holder.revalidate();
		holder.repaint();
	}

	private static JPanel row(JComponent left, JComponent right)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(left, BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static JLabel label(String text, Color color, boolean bold)
	{
		JLabel l = new JLabel(text);
		l.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	private static JLabel wrapped(String html, Color color)
	{
		JLabel l = new JLabel("<html><body style='width:150px'>" + html + "</body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(MUTED);
		l.setBorder(new EmptyBorder(4, 0, 2, 0));
		return l;
	}

	private static JComponent spacer()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, 2));
		return p;
	}

	/** Compact names for the two-column patch pickers. */
	private static String pickerName(FarmPatch p)
	{
		return shortName(p).replace("Troll Stronghold", "Trollheim").replace("Harmony Island", "Harmony")
			.replace("Tree Gnome Village", "Gnome Village").replace("Gnome Stronghold", "Stronghold")
			.replace("Farming Guild", "Guild").replace("Fossil Island", "Fossil");
	}

	/** Shorter names inside a group that's already labelled by type. */
	private static String shortName(FarmPatch p)
	{
		return p.getDisplayName().replace(" fruit tree", "").replace(" hardwood", "").replace(" herb", "")
			.replace(" tree", "").replace(" (Ortus Farm)", "");
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Vertical stack that stretches children to full width. */
	private static final class Column extends JPanel
	{
		private int rowIndex;

		Column()
		{
			super(new GridBagLayout());
			setOpaque(false);
		}

		void addRow(JComponent c, int gapBelow)
		{
			GridBagConstraints g = new GridBagConstraints();
			g.gridx = 0;
			g.gridy = rowIndex++;
			g.weightx = 1;
			g.fill = GridBagConstraints.HORIZONTAL;
			g.anchor = GridBagConstraints.NORTHWEST;
			g.insets = new Insets(0, 0, gapBelow, 0);
			super.add(c, g);
		}
	}
}
