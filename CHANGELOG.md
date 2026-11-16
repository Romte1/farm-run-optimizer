# Changelog

## 1.5.0 (2026-11-16)
- **Guide line reworked.** It now plans with a walking map of the whole game (from the Shortest Path plugin, credited in NOTICE) instead of only the area around you, so it no longer heads off the wrong way (into the sea at Morytania, away from the patch in Varlamore) when the stop isn't loaded yet.
- **Straighter, steadier line.** Routes use real distances and are smoothed into straight stretches instead of zigzagging, and the line stays the same while you follow it instead of jumping between equally short routes.
- Stops beside the patch on a side you can actually reach (not behind a fence), or in the fairy ring.
- **Route on the minimap** too, and the minimap arrow now points along the route.
- Map squares with no map data are never walked on.

## 1.4.0 (2026-11-15)
- **Built-in guide line.** The plugin now draws its own walking line, worked out from the game's collision map over the loaded area: to the patch (stopping beside it), the fairy ring or spirit tree first, or the gardener when it's time to pay. It goes round walls, fences and gates, and towards the patch when it's beyond the loaded area. No other plugin needed. Setting: "Guide line" (on by default).
- The Shortest Path plugin is now optional and off by default ("Shortest Path plugin line" in Settings). It kept saying "Destination could not be reached" at tree patches, because you can't stand on a 3x3 tree patch.

## 1.3.3 (2026-11-15)
- **Minimap arrow:** a marker on the minimap at the next patch, fairy ring or spirit tree, or an arrow near the minimap's edge pointing towards it when it's further away. Works without the Shortest Path plugin. Uses the "Arrow over target" setting.

## 1.3.2 (2026-11-15)
- **New arrow:** the game's blinking hint arrow is no longer used. The plugin draws its own steady arrow right above the label of the real thing on screen: the gardener to pay, the fairy ring or spirit tree to use, or the patch once it's loaded. Setting "Arrow over target" (on by default).
- Fairy rings: only real rings at known ring spots are outlined (scenery that shares the name was being picked up near Morytania).

## 1.3.1 (2026-11-15)
- **Gardeners found by their id** (from RuneLite's Time Tracking), so every patch's gardener is highlighted, including Lumbridge and Taverley.
- **Walking line for trees and allotments** aims at where the patch's gardener stands (always a walkable spot beside the patch), learned the first time you see them.
- **Gnome Stronghold via the Grand Exchange spirit tree:** "Varrock Teleport, spirit tree at the Grand Exchange to Gnome Stronghold". Varrock Teleport is highlighted (and its runes or tab added to the bring list), then the GE spirit tree is marked with what to pick.
- **Hint arrow pinned to real things only:** the gardener you need to pay, the fairy ring or spirit tree to use, or the patch itself once it's loaded. No more arrow on a guessed tile or jumping around.

## 1.3.0 (2026-11-14)
- **XP before a run:** "This run: about 12k Farming XP", from what you'll harvest or health-check (using what's growing in each patch and your average herbs per patch), planting and compost. XP values from the OSRS Wiki.
- **XP after a run:** Farming XP gained at the patches is logged with each run: "So far: 4 patches • +80k gp • +6.2k XP", and in Run history with the average XP per run.
- Fix: huasca needs 65 Farming (was 58).

## 1.2.5 (2026-11-14)
- Grown trees with no model outline (seen at Lumbridge and Gnome Stronghold) are still marked: the plugin falls back to the click area, then the patch's floor area.

## 1.2.4 (2026-11-14)
- The walking line is asked for again right after you teleport, from where you land. Before, a route worked out from the previous stop (e.g. Nemus Retreat to Varrock) could fail and wouldn't come back until you were next to the patch.

## 1.2.3 (2026-11-14)
- Tree and allotment patches: Shortest Path is given a ring of tiles around the patch (plus the learned walkable spot) and walks to whichever it can reach, so it no longer stops with "Destination could not be reached" before you've ever visited the patch.

## 1.2.2 (2026-11-14)
- Timers only list patches with something in them (growing, ready, diseased, dead, stump); empty and weedy patches are hidden with a "+6 empty patches hidden" note. Setting: "Timers: planted patches only" (on by default).

## 1.2.1 (2026-11-14)
- New setting "Path: minigame teleports" (off by default): the Shortest Path line no longer routes through minigame teleports like Rat Pits. Applies only to the routes this plugin asks for; your own Shortest Path settings are untouched.

## 1.2.0 (2026-11-14)
- **New: Allotment run.** 14 allotments (2 each at Falador, Catherby, Ardougne, Morytania, Hosidius, Varlamore and the Farming Guild at 45 Farming), all 8 crops from potatoes (level 1) to snape grass (61), 3 seeds per patch, compost, and the gardener's protection payment. Great for new players before herbs and trees unlock. Combines with the herb run (the allotments are right next to the herb patches).
- Farming Guild allotment contracts plant in the guild allotment.
- The two allotments side by side are told apart by their real position when you plant.
- Same item used two ways (e.g. compost to use and to pay for potatoes) is one line in the bring list.

## 1.1.4 (2026-11-14)
- Fix: after planting, the compost (and the gardener payment on tree runs) is highlighted again. Since 1.0.2 a just-planted stop lost its compost/payment step.
- Tree patches: the walking path aims at a walkable tile right beside the patch, worked out from the game's collision map (fixes "Destination could not be reached" at Varrock, Falador park, Nemus Retreat and others).
- The blinking yellow hint arrow is off by default (turn it back on in Settings if you like it).

## 1.1.3 (2026-11-14)
- Shortest Path is aimed at the patch again (not the fairy ring), so it plans and draws the whole trip including the ring, like before. The ring itself is still outlined with the code.
- "Dial AKR for Hosidius herb" box shows more reliably when the fairy ring interface is open, and is wide enough for the full text.
- Fairy rings are found whichever way the game sends them (ground objects, decorations, impostors).
- Directions say which way to walk to the ring: "Walk south (about 30 tiles) to fairy ring ALQ, dial AKR".

## 1.1.2 (2026-11-14)
- On the way to the next stop, the teleport is highlighted: the spell in your spellbook (e.g. Falador Teleport), or the teleport item/tab in your inventory. The NOW banner says "Cast Falador Teleport" / "Teleport".

## 1.1.1 (2026-11-14)
- Spirit tree travel tile and stop cards show a spirit tree instead of a spirit seed.

## 1.1.0 (2026-11-14) - big update
Everything since the first Plugin Hub version (1.0.1 + 1.0.2), plus:
- **Fairy rings everywhere**: on any fairy ring step ("From any fairy ring, dial AIS" too), the nearest ring is outlined with the code, and a big "Dial AIS for Nemus Retreat tree" box shows at the top of the screen while the fairy ring interface is open.
- **Spirit trees**: on spirit tree steps the nearest spirit tree is outlined ("Spirit tree: Gnome Stronghold"), the path and arrow lead to it, and the top-of-screen box says which destination to pick.
- **Tree patches**: the walking path goes to the tile where the gardener stands (learned the first time you visit), since the patch itself can't be walked on. Fixes "Destination could not be reached".

## 1.0.2 (2026-11-14)
Tree run fixes:
- No sapling for a grown tree: just check its health (XP) and leave it standing, instead of paying 200 coins to chop it with nothing to replant.
- Growing trees with no sapling are left off the run.
- Coins (200 each) only for trees you're replacing.
- Compost is no longer packed for stops where nothing is planted (no sapling, or already done this run). Also applies to herbs.
- Clearer sapling shortage warning.

## 1.0.1 (2026-11-14) - first Plugin Hub update
- Includes b12 and b13: run in progress saved, live "This run" in history, real fairy ring outline, fairer run profit.
- Support link on the Plugin Hub page; screenshots in the README.

## 1.0.0 b13 (2026-11-14)
- Fairy ring stops outline the real fairy ring (like patches), and the path and arrow go to it.
- Run profit now counts the seed that grew what you harvested, instead of the seed you planted for next time, so a run is no longer negative just because you planted pricier seeds.

## 1.0.0 b12 (2026-11-13)
- Fix: the run in progress is saved as you go, so closing the client mid-run no longer loses it.
- Run history shows "This run" straight away instead of only after the run ends.
- Harvests are picked up from a bit further from the patch.

## 1.0.0 b11 (2026-11-13)
- Plugin Hub review: added `build=standard`, rewrote the README for players and removed the developer testing section.

## 1.0.0 b10 (2026-11-13)
- Renamed to **Farm Run Optimizer**: "Farm Run Planner" is already taken on the Plugin Hub by another author.

## 1.0.0 b9 (2026-11-13)
- The gardener you need to pay is outlined in gold, with what to give them ("Pay: Oranges(5)" or "Pay 200 coins to chop it down"). Setting: "Highlight gardener to pay".

## 1.0.0 b8 (2026-11-13)
- Fix: patches you just planted no longer use up saplings in the plan, so the rest of the run (e.g. Taverley, Gnome Stronghold) stays on the list.
- Teleport spells show their spellbook picture (Varrock, Camelot, Ardougne...) instead of a law rune, on stop cards and in Travel.

## 1.0.0 b7 (2026-11-13)
- Walking path now goes to the real patch: it's re-sent whenever the target tile changes (e.g. when the patch loads in), instead of once per patch using the rough map tile.
- Fairy ring steps lead you to the ring first ("Fairy ring: dial AKR" on the ring, path and arrow), then on to the patch after you teleport.

## 1.0.0 b6 (2026-11-13)
- Run history & profit: harvests logged at GE prices, profit per run, average herbs per patch.
- Herb profit per patch, and "Most profitable herb first" planting (setting, on by default).
- Diseased/dead notifications; plant cure or secateurs added to the bank list for diseased patches.
- On-screen run-ready box (ready counts, next timer, diseased patches, contract).
- Farming Guild contract card; contract crop planted in the guild patch.
- BSD 2-Clause LICENSE and Plugin Hub submission guide (docs/PLUGIN_HUB.md).

## 1.0.0 b5 (2026-11-13)
- Only ready patches are planned: if 1 herb is ready, the run is just that patch (and only 1 seed to bring). Growing patches are left off with a "next up" tip.
- When nothing is ready, the full run is shown so you can prep.
- Patches you just did stay on the list (green) for 30 minutes.
- Replans automatically when a patch becomes ready.
- New setting: "Only ready patches" (on by default).

## 1.0.0 b4 (2026-11-13)
- Timers moved to the top, Runs underneath.
- Timers show what's growing in each patch (e.g. "Catherby / Ranarr weed").
- Travel tiles are faded with a padlock instead of greyed out.
- Pictures for the spirit tree, Kharedst's memoirs, quetzal whistle, hunter cape, pendant of ates, teleport crystal, digsite pendant and the Trollheim/Fenkenstrain/Brimhaven tabs.
- Dramen staff tile under Other turns all fairy rings on/off.

## 1.0.0 b3 (2026-11-13)
- Runs list moved to the top of the panel, with an on/off box per run; active runs open automatically.
- Tree runs: reminders to plant tree seeds in plant pots and water the seedlings when you have no saplings.
- Hint arrow now points at the real patch object (matches the outline).
- Version shown in the footer so you can tell which build is running.

## Unreleased

- Seeds and saplings go to the patches you use first; patches with nothing to plant and nothing of
  yours growing are left off the route and the bank list.
- Patch outline matches the real patch object by its farming varbit (works while crops grow).
- Exact patch tiles learned from the game; built-in tiles from RuneLite's world map.
- Patch data generated from RuneLite's Time Tracking source; all patches verified.
- New logo, GitHub banner, build check, issue templates, progress file.

## 1.0.0 - 2026-11-13

First release of Farm Run Planner.

- Herb, tree, fruit tree and hardwood runs, combined into one route.
- Fastest teleport you own per patch; fairy rings chained with the code to dial; spells and tabs toggles.
- "Take with you" list with bank stops when it won't fit in one inventory; bank items highlighted.
- A card for every stop with its to-do list; finished stops turn green; next stop in gold.
- Current step's item highlighted in your inventory (seed, compost, payment).
- Exact timers read from the game, growth-tick offset learning, Time Tracking import, notifications.
- Crop and patch pickers, Settings section, ground highlight, hint arrow, Shortest Path support.
