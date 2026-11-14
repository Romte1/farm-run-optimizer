# Submitting Farm Run Optimizer to the RuneLite Plugin Hub

## Checklist (done in this repo)

- [x] Public GitHub repo: https://github.com/triggleman/farm-run-planner
- [x] `runelite-plugin.properties` with displayName, author, description, tags, plugins
- [x] `icon.png` at the repo root (48x48, the limit is 48x72)
- [x] `README.md` describing the features
- [x] `LICENSE` (BSD 2-Clause)
- [x] No third-party dependencies (only RuneLite + Lombok, which the Hub provides)
- [x] Resources loaded from the jar (`ImageUtil.loadImageResource`), no files on disk
- [x] No automation: the plugin only shows information, highlights and notifications
- [x] No network calls of its own (prices come from RuneLite's ItemManager)

## Before you submit

1. Do one full herb run and one tree run in game with the latest build.
2. Make sure GitHub Actions shows a green build on the latest commit.
3. Copy the full 40-character hash of the latest commit
   (GitHub > Commits > copy icon next to the newest one).

## Submitting

1. Go to https://github.com/runelite/plugin-hub and click **Fork**.
2. In your fork, click **Add file > Create new file**.
3. Name it `plugins/farm-run-optimizer` (no extension) with exactly these two lines:

   ```
   repository=https://github.com/triggleman/farm-run-planner.git
   commit=PASTE_THE_40_CHARACTER_HASH_HERE
   ```

4. Choose **Create a new branch** (e.g. `farm-run-optimizer`) and commit.
5. Click **Contribute > Open pull request** (base: `runelite/plugin-hub` master).
   Title: `Add Farm Run Optimizer`. Description: one or two lines on what it does.
6. Wait for the checks:
   - **build (pull_request)**: if it fails, click Details and send me the log.
   - **RuneLite Plugin Hub Checks**: if it says changes are needed, send me the list.
7. To update after a fix: push to this repo, then edit `plugins/farm-run-optimizer` in the
   same pull request with the new commit hash. Don't open a new pull request.

## Updating later (after it's live)

Every new version is a small pull request to plugin-hub that only changes the `commit=`
line to the newest hash.
