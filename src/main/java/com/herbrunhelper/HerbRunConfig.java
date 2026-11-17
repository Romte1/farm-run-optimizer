package com.herbrunhelper;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(HerbRunConfig.GROUP)
public interface HerbRunConfig extends Config
{
	String GROUP = "herbrunhelper";

	@ConfigSection(name = "Run", description = "What the run includes", position = 0)
	String runSection = "run";

	@ConfigSection(name = "Navigation", description = "Showing where to go", position = 1)
	String navSection = "nav";

	@ConfigSection(name = "Bank", description = "Bank highlighting", position = 2)
	String bankSection = "bank";

	@ConfigItem(keyName = "includeHerbs", name = "Herb run", description = "Include herb patches", position = 1, section = runSection)
	default boolean includeHerbs()
	{
		return true;
	}

	@ConfigItem(keyName = "includeTrees", name = "Tree run", description = "Include tree patches", position = 2, section = runSection)
	default boolean includeTrees()
	{
		return true;
	}

	@ConfigItem(keyName = "includeFruitTrees", name = "Fruit tree run", description = "Include fruit tree patches", position = 3, section = runSection)
	default boolean includeFruitTrees()
	{
		return false;
	}

	@ConfigItem(keyName = "includeAllotments", name = "Allotment run", description = "Include allotment patches (potatoes up to snape grass) - great before you unlock herbs and trees", position = 4, section = runSection)
	default boolean includeAllotments()
	{
		return false;
	}

	@ConfigItem(keyName = "includeHardwood", name = "Hardwood run", description = "Include teak/mahogany patches", position = 3, section = runSection)
	default boolean includeHardwood()
	{
		return false;
	}

	@ConfigItem(keyName = "payGardeners", name = "Pay gardeners", description = "Bring payment so gardeners protect your trees", position = 3, section = runSection)
	default boolean payGardeners()
	{
		return true;
	}

	@ConfigItem(keyName = "useCompost", name = "Bring compost", description = "Include compost in the withdraw list", position = 4, section = runSection)
	default boolean useCompost()
	{
		return true;
	}

	@ConfigItem(keyName = "activeTimersOnly", name = "Timers: planted patches only", description = "Hide empty and weedy patches from the timer list", position = 5, section = runSection)
	default boolean activeTimersOnly()
	{
		return true;
	}

	@ConfigItem(keyName = "onlyReady", name = "Only ready patches", description = "When some patches are ready, leave the still-growing ones off the run", position = 6, section = runSection)
	default boolean onlyReady()
	{
		return true;
	}

	@ConfigItem(keyName = "profitFirst", name = "Most profitable herb first", description = "Plant the herb seed with the best GE profit per patch first, instead of the highest level", position = 7, section = runSection)
	default boolean profitFirst()
	{
		return true;
	}

	@ConfigItem(keyName = "plantContract", name = "Plant farming contract", description = "Put your Farming Guild contract crop in the guild patch", position = 8, section = runSection)
	default boolean plantContract()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyDisease", name = "Notify diseased/dead", description = "Notification when a patch is found diseased or dead", position = 9, section = runSection)
	default boolean notifyDisease()
	{
		return true;
	}

	@ConfigItem(keyName = "showReadyOverlay", name = "Run-ready box", description = "Small on-screen box showing which runs are ready", position = 10, section = runSection)
	default boolean showReadyOverlay()
	{
		return true;
	}

	@ConfigItem(keyName = "showHistory", name = "Show run history", description = "Show the run history and profit section", position = 11, section = runSection)
	default boolean showHistory()
	{
		return true;
	}

	@ConfigItem(keyName = "bringRake", name = "Bring rake", description = "Include a rake for weedy patches", position = 5, section = runSection)
	default boolean bringRake()
	{
		return true;
	}

	@ConfigItem(keyName = "useBoostedLevel", name = "Use boosted level", description = "Plan with your boosted Farming level (e.g. after a garden pie)", position = 6, section = runSection)
	default boolean useBoostedLevel()
	{
		return false;
	}

	@ConfigItem(keyName = "harmonyUnlocked", name = "Morytania Elite done", description = "Tick if you've done the Morytania Elite Diary (unlocks Harmony Island)", position = 7, section = runSection)
	default boolean harmonyUnlocked()
	{
		return false;
	}

	@ConfigItem(keyName = "notifyReady", name = "Notify when ready", description = "Send a notification when a tracked patch is fully grown", position = 8, section = runSection)
	default boolean notifyReady()
	{
		return true;
	}

	@ConfigItem(keyName = "useSpells", name = "Use spells (runes)",
		description = "Use standard spellbook teleports. Used instead of tabs when both are on.",
		position = 0, section = navSection)
	default boolean useSpells()
	{
		return true;
	}

	@ConfigItem(keyName = "useTabs", name = "Use teleport tabs",
		description = "Use teleport tablets (only where you can't cast the spell, if spells are on)",
		position = 0, section = navSection)
	default boolean useTabs()
	{
		return true;
	}

	@ConfigItem(keyName = "chainFairyRings", name = "Chain fairy rings",
		description = "Once you're at a fairy ring, do every fairy-ring patch back to back (e.g. cloak to Ardougne, then BLR > ALQ > AKR > AJP)",
		position = 1, section = navSection)
	default boolean chainFairyRings()
	{
		return true;
	}

	@ConfigItem(keyName = "staffless", name = "Lumbridge Elite done",
		description = "Tick if you've done the Lumbridge Elite Diary (fairy rings without a staff)",
		position = 5, section = navSection)
	default boolean staffless()
	{
		return false;
	}

	@ConfigItem(keyName = "disabledCrops", name = "", description = "", hidden = true)
	default String disabledCrops()
	{
		return "";
	}

	@ConfigItem(keyName = "disabledPatches", name = "", description = "", hidden = true)
	default String disabledPatches()
	{
		return "";
	}

	@ConfigItem(keyName = "disabledTeleports", name = "", description = "", hidden = true)
	default String disabledTeleports()
	{
		return "";
	}

	@ConfigItem(keyName = "importTimeTracking", name = "Import Time Tracking timers",
		description = "Read herb timers RuneLite's built-in Time Tracking plugin has already saved",
		position = 9, section = runSection)
	default boolean importTimeTracking()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightGardener", name = "Highlight gardener to pay", description = "Outline the gardener when the next step is paying them", position = 2, section = navSection)
	default boolean highlightGardener()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightPatch", name = "Highlight next patch", description = "Outline the next patch on the ground", position = 1, section = navSection)
	default boolean highlightPatch()
	{
		return true;
	}

	@ConfigItem(keyName = "pointerArrow", name = "Arrow over target", description = "A steady arrow over the next patch, fairy ring, spirit tree or gardener (drawn by this plugin, not the game's blinking hint arrow)", position = 2, section = navSection)
	default boolean hintArrow()
	{
		return true;
	}

	@ConfigItem(keyName = "guideLine", name = "Guide line",
		description = "Draw this plugin's own walking line to the next patch, fairy ring, spirit tree or gardener (no other plugin needed)",
		position = 3, section = navSection)
	default boolean guideLine()
	{
		return true;
	}

	@ConfigItem(keyName = "guideStyle", name = "Guide line style",
		description = "Draw the guide as a line through the middle of each tile, as highlighted tiles (like Shortest Path's tile mode), or both",
		position = 3, section = navSection)
	default GuideStyle guideStyle()
	{
		return GuideStyle.LINE;
	}

	@ConfigItem(keyName = "guideStatus", name = "Guide line status",
		description = "Show a small status box saying what the guide line is aiming at and, if there's no line, why. Handy for bug reports.",
		position = 3, section = navSection)
	default boolean guideStatus()
	{
		return false;
	}

	@ConfigItem(keyName = "shortestPathLine", name = "Use Shortest Path plugin",
		description = "Also send each stop to the Shortest Path plugin (if installed) for its own walking line",
		position = 3, section = navSection)
	default boolean useShortestPath()
	{
		return false;
	}

	@ConfigItem(keyName = "pathMinigameTeleports", name = "Path: minigame teleports",
		description = "Let the Shortest Path line use minigame teleports (e.g. Rat Pits). Off keeps the line closer to this plugin's plan.",
		position = 4, section = navSection)
	default boolean pathMinigameTeleports()
	{
		return false;
	}

	@ConfigItem(keyName = "patchColor", name = "Patch colour", description = "Colour of the next-patch highlight", position = 4, section = navSection)
	default Color patchColor()
	{
		return new Color(0, 200, 255);
	}

	@ConfigItem(keyName = "highlightInventory", name = "Highlight step items",
		description = "Outline the inventory item your current step needs (seed, then compost, then payment)",
		position = 6, section = navSection)
	default boolean highlightInventory()
	{
		return true;
	}

	@ConfigItem(keyName = "stepColor", name = "Step item colour", description = "Colour of the inventory step highlight",
		position = 7, section = navSection)
	default Color stepColor()
	{
		return new Color(255, 200, 60);
	}

	@ConfigItem(keyName = "showTravel", name = "Show Travel section", description = "Show the Travel section in the panel", hidden = true)
	default boolean showTravel()
	{
		return true;
	}

	@ConfigItem(keyName = "showTimers", name = "Show patch timers", description = "Show the patch timer list in the panel", hidden = true)
	default boolean showTimers()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightBank", name = "Highlight in bank", description = "Outline the items to withdraw when your bank is open", position = 1, section = bankSection)
	default boolean highlightBank()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightColor", name = "Highlight colour", description = "Colour of the bank highlight", position = 2, section = bankSection)
	default Color highlightColor()
	{
		return new Color(80, 220, 80);
	}
}
