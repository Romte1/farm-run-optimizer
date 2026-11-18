#!/usr/bin/env python3
"""Builds the guide line's doors / stairs / shortcuts data from the Shortest Path plugin's transport
lists (BSD 2-Clause, see NOTICE):

  src/main/resources/com/herbrunhelper/walk-links.tsv  one line per link
  src/main/resources/com/herbrunhelper/walk-reqs.tsv   the requirements those links need

Usage: build_walk_links.py <shortest-path>/src/main/resources

Keeps on-foot links (transports.tsv and agility_shortcuts.tsv): doors, gates, stiles, stairs,
ladders, shortcuts, short crossings. Same floor: up to MAX_SPAN tiles. Changing floor: up to
MAX_FLOOR_SPAN tiles sideways (stairs and ladders, not dungeon entrances far away on the map).
Requirements are kept as Shortest Path writes them (skills, quests, varbits, varplayers), with
item groups (AXE, PICKAXE, ...) turned into item ids from item-variations.tsv, so the plugin can
check them in game.

walk-links.tsv columns: originX originY plane destX destY destPlane durationTicks reqId
walk-reqs.tsv columns:  reqId skills items quests varbits varplayers
  skills:   "70 Agility;50 Strength"           (all needed)
  items:    "1351,1349:1&2347:1|1755:1"        (& = all of, | = any of, ids:quantity)
  quests:   "Fairytale I - Growing Pains;..."   (all finished)
  varbits / varplayers: "538=1;451>0"           (= > < & checks; all needed)
Requirements the plugin can't check (unlock tokens, cooldowns) make a link unusable, never assumed.
"""
import os
import sys

MAX_SPAN = 15
MAX_FLOOR_SPAN = 6
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "com", "herbrunhelper")


def rows(path):
    header = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("# Origin"):
                header = [h.strip() for h in line[2:].split("\t")]
                continue
            if not line or line.startswith("#") or header is None:
                continue
            cols = line.split("\t")
            cols += [""] * (len(header) - len(cols))
            yield {k: v.strip() for k, v in zip(header, cols)}


def load_variations(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            cols = line.rstrip("\n").split("\t")
            ids = [c.strip() for c in cols[1].split(",") if c.strip()]
            staves = [c.strip() for c in cols[2].split(",") if len(cols) > 2 and c.strip()]
            offhands = [c.strip() for c in cols[3].split(",") if len(cols) > 3 and c.strip()]
            out[cols[0].strip().upper()] = (ids, staves + offhands)
    return out


def items(value, variations):
    """Shortest Path item text -> our id form, or None if it can't be checked."""
    if not value:
        return ""
    value = value.replace(" ", "").upper()
    ands = []
    for part in value.split("&"):
        if not part:
            continue
        ors = []
        for branch in part.split("|"):
            if "=" not in branch:
                return None
            name, qty = branch.split("=", 1)
            if not qty.isdigit():
                return None
            if name.startswith("UNLOCK_"):
                return None
            if name in variations:
                ids, alts = variations[name]
                ors.append(",".join(ids) + ":" + qty)
                if alts:
                    ors.append(",".join(alts) + ":1")  # a staff/tome gives unlimited runes
            elif name.isdigit():
                ors.append(name + ":" + qty)
            else:
                return None
        ands.append("|".join(ors))
    return "&".join(ands)


def checkable_vars(value):
    if not value:
        return ""
    for v in value.split(";"):
        if v and "@" in v:
            return None  # cooldowns: can't check
    return value


def main():
    res = sys.argv[1]
    variations = load_variations(os.path.join(res, "item-variations.tsv"))
    links = set()
    reqs = {}
    skipped = 0
    for name in ("transports.tsv", "agility_shortcuts.tsv"):
        for r in rows(os.path.join(res, "transports", name)):
            try:
                ox, oy, oz = map(int, r["Origin"].split())
                dx, dy, dz = map(int, r["Destination"].split())
            except (KeyError, ValueError):
                continue
            span = max(abs(ox - dx), abs(oy - dy))
            if (oz == dz and span > MAX_SPAN) or (oz != dz and span > MAX_FLOOR_SPAN):
                continue
            it = items(r.get("Items", ""), variations)
            vb = checkable_vars(r.get("Varbits", ""))
            vp = checkable_vars(r.get("VarPlayers", ""))
            if it is None or vb is None or vp is None:
                skipped += 1
                continue
            key = (r.get("Skills", ""), it, r.get("Quests", ""), vb, vp)
            rid = 0
            if any(key):
                rid = reqs.setdefault(key, len(reqs) + 1)
            try:
                duration = int(r.get("Duration") or 1)
            except ValueError:
                duration = 1
            links.add((ox, oy, oz, dx, dy, dz, max(1, duration), rid))
    with open(os.path.join(OUT, "walk-links.tsv"), "w", encoding="utf-8") as f:
        f.write("# Doors, stairs and shortcuts for the guide line, from Shortest Path (see NOTICE).\n")
        f.write("# originX originY plane destX destY destPlane duration reqId (0 = none; see walk-reqs.tsv)\n")
        for l in sorted(links):
            f.write("\t".join(map(str, l)) + "\n")
    with open(os.path.join(OUT, "walk-reqs.tsv"), "w", encoding="utf-8") as f:
        f.write("# Requirements for walk-links.tsv, from Shortest Path (see NOTICE).\n")
        f.write("# reqId skills items quests varbits varplayers\n")
        for key, rid in sorted(reqs.items(), key=lambda kv: kv[1]):
            f.write(str(rid) + "\t" + "\t".join(key) + "\n")
    print(len(links), "links,", len(reqs), "requirement sets,", skipped, "skipped (unlock tokens / cooldowns / unknown items)")


if __name__ == "__main__":
    main()
