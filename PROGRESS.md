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

## 2026-11-13 (b4)
- [x] Timers on top, Runs below
- [x] Crop name under each patch in the timers
- [x] Travel tiles dimmed + padlock (not greyscale)
- [x] Item pictures for every jewellery/item teleport and spirit tree
- [x] Dramen staff fairy ring toggle

## 2026-11-13 (b5)
- [x] Plan only ready patches (setting, default on), done patches kept green, auto-replan when ready

## 2026-11-13 (b6)
- [x] Run history + profit
- [x] Seed profit picker (most profitable first)
- [x] Disease/dead alerts + plant cure
- [x] Run-ready overlay
- [x] Farming contract
- [x] LICENSE + Plugin Hub prep (docs/PLUGIN_HUB.md)
- [ ] In-game test run (Todd)
- [ ] Submit to Plugin Hub (Todd, following docs/PLUGIN_HUB.md)

## 2026-11-13 (b7)
- [x] Path line follows the real patch tile (Hosidius / Varlamore were pointing past the patch)
- [x] Lead to the fairy ring first on fairy ring steps

## 2026-11-13 (b8)
- [x] Half-done tree run kept Taverley/Stronghold off (just-planted patches were taking the saplings)
- [x] Spellbook icons for teleport spells

## 2026-11-13 (b9)
- [x] Highlight the gardener to pay

## 2026-11-13 (b10)
- [x] Renamed to Farm Run Optimizer (name clash on the Plugin Hub)
- [ ] New GitHub banner with the new name (Todd)
- [ ] Plugin Hub pull request (Todd)

## 2026-11-13 (b11)
- [x] Plugin Hub review fixes: build=standard, player-focused README, removed testing section

## 2026-11-13 (b12)
- [x] Run history: run in progress saved/restored, shown live in the section

## 2026-11-14 (b13)
- [x] Outline the real fairy ring object
- [x] Profit = harvest value minus the seeds that grew it

## 2026-11-14 - LIVE on the Plugin Hub (first version: commit 5f83a39)
- [x] 1.0.1 update: b12 + b13 fixes, support link, README screenshots
- [ ] Remaining screenshots: ground highlight, fairy ring, gardener, stop cards, run history

## 2026-11-14 (1.0.2)
- [x] Tree run fixes: health check only without sapling, coins only when replanting, no compost for empty stops

## 2026-11-14 (1.1.0)
- [x] Nearest fairy ring outlined + on-screen "Dial XXX" box (also for "any fairy ring" steps)
- [x] Spirit tree outlined + "pick Gnome Stronghold"
- [x] Tree path goes to a walkable tile (gardener spot)
