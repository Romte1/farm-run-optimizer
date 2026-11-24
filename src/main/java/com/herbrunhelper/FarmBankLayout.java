package com.herbrunhelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/**
 * Spaces out the bank tag's own item widgets while the Farm run tag is open. Only the widget
 * positions are changed: the item, name, quantity and noted state stay exactly as the bank built
 * them, so withdrawing and the right-click menu behave like the normal bank. Icons for items that
 * are already carried but no longer in the bank are drawn by {@link FarmBankViewOverlay}.
 */
@Singleton
class FarmBankLayout
{
	static final int PER_ROW = 7;
	static final int X_SPACING = 56;
	static final int Y_SPACING = 54;
	static final int TOP = 8;
	static final int START_X = 51;

	/** A carried item that is no longer in the bank, drawn as a check beside its icon. */
	static final class Draw
	{
		final int x;
		final int y;
		final int itemId;
		final String label;
		final boolean faded;

		Draw(int x, int y, int itemId, String label, boolean faded)
		{
			this.x = x;
			this.y = y;
			this.itemId = itemId;
			this.label = label;
			this.faded = faded;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final FarmBankButton button;
	private final HerbRunPlugin plugin;
	private volatile List<Draw> draws = Collections.emptyList();
	/** Scroll position captured before a bank rebuild, restored after it. */
	private int savedScroll = -1;
	/** Whether the Farm run view was already showing before this bank build. */
	private boolean activeLastBuild;
	/** Skip rebuilding the widget grid when the plan and carried counts are unchanged. */
	private long lastSignature = Long.MIN_VALUE;
	/** Coalesce the bank rebuild and plan refresh into one layout per tick. */
	private boolean layoutScheduled;
	private boolean scheduledAfterBuild;
	/** Stable display order for this bank visit, so withdrawing never reshuffles the list. */
	private final List<String> displayOrder = new ArrayList<>();
	/** The last grid actually applied; a plan refresh with the same grid does nothing. */
	private long lastPlacementSignature = Long.MIN_VALUE;

	@Inject
	FarmBankLayout(Client client, ClientThread clientThread, FarmBankButton button, HerbRunPlugin plugin)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.button = button;
		this.plugin = plugin;
	}

	/** Captures the list position before a withdrawal/deposit rebuilds the bank. */
	void saveScroll()
	{
		if (!activeLastBuild)
		{
			return; // opening the view: start at the top
		}
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (items != null && !items.isHidden())
		{
			savedScroll = items.getScrollY();
		}
	}

	/** The bank finished building (including the Bank Tags tab). */
	void onBankBuilt()
	{
		boolean active = button.isActive();
		if (!active)
		{
			draws = Collections.emptyList();
			savedScroll = -1;
			activeLastBuild = false;
			return;
		}
		if (!activeLastBuild)
		{
			savedScroll = -1; // opening the view: start at the top
		}
		activeLastBuild = true;
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (items == null)
		{
			return;
		}
		lastSignature = Long.MIN_VALUE; // the bank rebuilt its widgets
		scheduleLayout(true);
	}

	/** The plan changed while the tab is open. */
	void refresh()
	{
		boolean active = button.isActive();
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		boolean hidden = items == null || items.isHidden();
		long signature = signature();
		if (!active)
		{
			draws = Collections.emptyList();
			lastSignature = Long.MIN_VALUE;
			return;
		}
		if (hidden)
		{
			return;
		}
		if (signature == lastSignature)
		{
			return;
		}
		lastSignature = signature;
		scheduleLayout(false);
	}

	private void scheduleLayout(boolean afterBuild)
	{
		scheduledAfterBuild |= afterBuild;
		if (layoutScheduled)
		{
			return;
		}
		layoutScheduled = true;
		clientThread.invokeAtTickEnd(() ->
		{
			layoutScheduled = false;
			boolean after = scheduledAfterBuild;
			scheduledAfterBuild = false;
			Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
			if (items == null || items.isHidden())
			{
				return;
			}
			layOut(items, items.getDynamicChildren(), after);
		});
	}

	void shutDown()
	{
		draws = Collections.emptyList();
		lastSignature = Long.MIN_VALUE;
		lastPlacementSignature = Long.MIN_VALUE;
		displayOrder.clear();
	}

	private long signature()
	{
		long hash = 17;
		for (RunPlan.Withdrawal line : plugin.getBankLines())
		{
			hash = hash * 31 + (line.itemName == null ? 0 : line.itemName.hashCode());
			hash = hash * 31 + line.quantity;
			hash = hash * 31 + line.toWithdraw;
			hash = hash * 31 + (line.noted ? 1 : 0);
			hash = hash * 31 + (line.group == null ? 0 : line.group.ordinal());
			hash = hash * 31 + (plugin.isWearItem(line.itemName) ? 1 : 0);
		}
		for (Map.Entry<String, Integer> e : plugin.getCarriedForBank().entrySet())
		{
			hash = hash * 31 + e.getKey().hashCode();
			hash = hash * 31 + e.getValue();
		}
		for (Map.Entry<String, Integer> e : plugin.getLeprechaunItems().entrySet())
		{
			hash = hash * 31 + e.getKey().hashCode();
			hash = hash * 31 + e.getValue();
		}
		for (Map.Entry<String, Integer> e : plugin.getSeedVaultItems().entrySet())
		{
			hash = hash * 31 + e.getKey().hashCode();
			hash = hash * 31 + e.getValue();
		}
		return hash;
	}

	List<Draw> getDraws()
	{
		return draws;
	}

	private static long placementSignature(List<RunPlan.Withdrawal> lines, List<Draw> ghost,
		int slots, int height)
	{
		long hash = 17;
		for (RunPlan.Withdrawal line : lines)
		{
			hash = hash * 31 + key(line).hashCode();
			hash = hash * 31 + (line.noted ? 1 : 0);
			hash = hash * 31 + line.quantity;
		}
		for (Draw draw : ghost)
		{
			hash = hash * 31 + draw.itemId;
			hash = hash * 31 + draw.x;
			hash = hash * 31 + draw.y;
			hash = hash * 31 + (draw.faded ? 1 : 0);
		}
		hash = hash * 31 + slots;
		hash = hash * 31 + height;
		return hash;
	}

	private void layOut(Widget items, Widget[] children, boolean afterBuild)
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null || children == null)
		{
			return;
		}
		// Read this before setScrollHeight/revalidate, which can reset the widget's scroll.
		int beforeScroll = items.getScrollY();
		Set<Integer> bankIds = bankIds(bank);
		Map<String, Widget> visibleByName = new HashMap<>();
		List<Widget> visible = new ArrayList<>();
		for (Widget child : children)
		{
			if (child == null)
			{
				continue;
			}
			if (child.getSpriteId() == SpriteID.TRADEBACKING_DARK)
			{
				child.setHidden(true); // the line between bank tabs
				continue;
			}
			int id = child.getItemId();
			if (id > 0 && !child.isHidden())
			{
				visible.add(child);
				String name = itemName(id);
				if (name != null)
				{
					visibleByName.putIfAbsent(name, child);
				}
			}
		}
		Map<RunPlan.Withdrawal, Integer> idByLine = new HashMap<>();
		plugin.getBankLinesById().forEach((id, line) -> idByLine.put(line, id));
		List<RunPlan.Withdrawal> ordered = stableOrder(plugin.getBankLines());

		List<Draw> out = new ArrayList<>();
		Set<Widget> placed = Collections.newSetFromMap(new IdentityHashMap<>());
		int slots = 0;
		for (RunPlan.Withdrawal line : ordered)
		{
			Integer id = idByLine.get(line);
			Widget widget = visibleByName.get(line.itemName);
			int carried = plugin.getCarriedForBank().getOrDefault(line.itemName, 0);
			if (widget != null && bankIds.contains(widget.getItemId()))
			{
				if (placed.add(widget))
				{
					place(widget, slots, TOP);
					slots++;
				}
			}
			else if (id != null && id > 0 && !bankIds.contains(id) && carried > 0 && !line.noted)
			{
				// The item was withdrawn: keep a check in its slot, but don't fake a bank widget.
				// Noted withdrawals are left out: the bank must behave as if they were taken.
				int x = START_X + (slots % PER_ROW) * X_SPACING;
				int y = TOP + (slots / PER_ROW) * Y_SPACING;
				out.add(new Draw(x, y, id, "\u2713", carried < line.quantity));
				slots++;
			}
		}

		// Any other visible bank-tag item keeps the bank's own order, so nothing overlaps.
		visible.sort(Comparator.comparingInt(Widget::getOriginalY).thenComparingInt(Widget::getOriginalX));
		for (Widget widget : visible)
		{
			if (placed.add(widget))
			{
				place(widget, slots, TOP);
				slots++;
			}
		}

		int rows = (slots + PER_ROW - 1) / PER_ROW;
		int height = Math.max(items.getHeight(), TOP + rows * Y_SPACING);
		long placement = placementSignature(ordered, out, slots, height);
		if (!afterBuild && placement == lastPlacementSignature)
		{
			lastSignature = signature();
			return;
		}
		lastPlacementSignature = placement;
		draws = out;
		lastSignature = signature();
		boolean heightChanged = items.getScrollHeight() != height;
		if (heightChanged)
		{
			items.setScrollHeight(height);
		}
		// After a bank rebuild the game may have jumped to the top: restore the position the
		// player had before it. A plain refresh must keep exactly what is on screen now, unless
		// the rebuild already reset it to 0 while a saved position is waiting.
		int current = beforeScroll;
		int scroll = afterBuild
			? (savedScroll >= 0 ? savedScroll : current)
			: (current == 0 && savedScroll > 0 ? savedScroll : current);
		boolean scrollChanged = items.getScrollY() != scroll;
		savedScroll = scroll;
		if (scrollChanged)
		{
			items.setScrollY(scroll);
			client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, scroll);
		}
		if (afterBuild || scrollChanged || heightChanged)
		{
			clientThread.invokeLater(() ->
			{
				// Reapply once more after the bank's own rebuild/render finishes.
				items.setScrollY(scroll);
				client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, scroll);
				client.runScript(ScriptID.UPDATE_SCROLLBAR,
					InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scroll);
			});
		}
	}

	private List<RunPlan.Withdrawal> stableOrder(List<RunPlan.Withdrawal> lines)
	{
		Map<String, RunPlan.Withdrawal> current = new LinkedHashMap<>();
		for (RunPlan.Withdrawal line : lines)
		{
			current.put(key(line), line);
		}
		List<RunPlan.Withdrawal> ordered = new ArrayList<>();
		for (String key : displayOrder)
		{
			RunPlan.Withdrawal line = current.remove(key);
			if (line != null)
			{
				ordered.add(line);
			}
		}
		for (RunPlan.Withdrawal line : lines)
		{
			if (current.remove(key(line)) != null)
			{
				ordered.add(line);
			}
		}
		// Stable: wearables first, everything else keeps the position it already had.
		ordered.sort(Comparator.comparingInt(
			(RunPlan.Withdrawal line) -> plugin.isWearItem(line.itemName) ? 0 : 1));
		// Remember names even when they leave the plan for a moment, so they return to the
		// same slot instead of jumping to the end after a withdrawal/deposit.
		for (RunPlan.Withdrawal line : ordered)
		{
			String key = key(line);
			if (!displayOrder.contains(key))
			{
				displayOrder.add(key);
			}
		}
		return ordered;
	}

	private static String key(RunPlan.Withdrawal line)
	{
		return line.itemName == null ? "" : line.itemName;
	}

	/** The bank name, with any "(noted)"/"(unnoted)" suffix removed. */
	private String itemName(int itemId)
	{
		ItemComposition composition = client.getItemDefinition(itemId);
		if (composition == null || composition.getName() == null)
		{
			return null;
		}
		String name = composition.getName();
		int suffix = name.indexOf(" (noted)");
		if (suffix > 0)
		{
			name = name.substring(0, suffix);
		}
		suffix = name.indexOf(" (unnoted)");
		if (suffix > 0)
		{
			name = name.substring(0, suffix);
		}
		return name;
	}

	private static void place(Widget widget, int index, int top)
	{
		int x = START_X + (index % PER_ROW) * X_SPACING;
		int y = top + (index / PER_ROW) * Y_SPACING;
		if (widget.getOriginalX() == x && widget.getOriginalY() == y)
		{
			return;
		}
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.revalidate();
	}

	/** Item ids that have real stock in the bank (placeholders are excluded by quantity). */
	private static Set<Integer> bankIds(ItemContainer bank)
	{
		Set<Integer> ids = new HashSet<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				ids.add(item.getId());
			}
		}
		return ids;
	}
}
