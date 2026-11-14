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

	@ConfigItem(keyName = "onlyReady", name = "Only ready patches", description = "When some patches are ready, leave the still-growing ones off the run", position = 6, section = runSection)
	default boolean onlyReady()
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

	@ConfigItem(keyName = "highlightPatch", name = "Highlight next patch", description = "Outline the next patch on the ground", position = 1, section = navSection)
	default boolean highlightPatch()
	{
		return true;
	}

	@ConfigItem(keyName = "hintArrow", name = "Hint arrow", description = "Show the yellow hint arrow over the next patch when it's nearby", position = 2, section = navSection)
	default boolean hintArrow()
	{
		return true;
	}

	@ConfigItem(keyName = "useShortestPath", name = "Use Shortest Path plugin",
		description = "If the Shortest Path plugin (Plugin Hub) is installed, draw the walking path to the next patch",
		position = 3, section = navSection)
	default boolean useShortestPath()
	{
		return true;
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
