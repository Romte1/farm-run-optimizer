# Changelog

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
