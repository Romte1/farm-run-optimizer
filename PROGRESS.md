# Farm Run Planner - Progress

Made by ToadFx. Last updated: 2026-11-13.

## Status

Version **1.0.0** in development. Runs in the RuneLite developer client; not on the Plugin Hub yet.

## Done

### Planning
- [x] Herb, tree, fruit tree and hardwood runs, combined into one route
- [x] Reads Farming level, Magic level, quests and spellbook automatically
- [x] Fastest teleport you own per patch; Ardougne cloak 2+ only; ectophial for Morytania
- [x] Fairy rings chained back to back from the nearest ring, with the code to dial
- [x] Spells / tabs checkboxes; click any teleport item to stop using it
- [x] Pick which herbs and trees to plant, and which patches to visit
- [x] Seeds go to the patches you use first; patches you can't plant (and don't use) are left off
- [x] "Take with you" list, with bank stops when it won't fit in 28 slots

### During the run
- [x] A card for every stop with its to-do list; next stop in gold, finished stops in green
- [x] "Rake the weeds" only when the patch actually has weeds
- [x] NOW banner and the current step's item highlighted in the inventory (seed, compost, payment)
- [x] Next patch outlined on the ground (exact patch object), hint arrow, Shortest Path line
- [x] Exact patch tiles learned from the game; built-in tiles from RuneLite's world map
- [x] Items to withdraw highlighted in the bank

### Timers
- [x] Patch state read from the game (growing stage, ready, chop down, stump, diseased, dead, weeds)
- [x] Learns your account's growth-tick offset for exact countdowns
- [x] Imports RuneLite Time Tracking timers; notifications when ready

### Panel and project
- [x] Runs, Travel and Settings sections; lock icons; new logo; "Made by ToadFx"
- [x] GitHub repo, README banner, build check, issue templates, changelog
- [x] Patch data generated from RuneLite source (`scripts/`), all 28 patches verified

## Recent fixes
- Tree run no longer sends you to patches you don't use when you're short on saplings
- Patch outline finds the real patch even when something is growing on it
- Corrected tiles for Nemus Retreat, Varlamore and others
- Ardougne cloak 1 no longer counts as the farm teleport

## Next up
- [ ] In-game screenshots for the README
- [ ] Add a LICENSE (BSD 2-Clause, needed for the Plugin Hub)
- [ ] Count seed vault / tool leprechaun storage
- [ ] On-screen "run ready" box
- [ ] Profit estimate per run
- [ ] Submit to the RuneLite Plugin Hub

## 2026-11-13 (b3)
- [x] Runs list at top, on/off per run, auto-expanded
- [x] Seed pot / watering reminders for tree runs
- [x] Hint arrow uses the loaded patch object
- [x] Build version in footer
