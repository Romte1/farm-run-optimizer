#!/usr/bin/env python3
"""Builds src/main/resources/com/herbrunhelper/walk-links.tsv: doors, gates and agility shortcuts
for the guide line, from the Shortest Path plugin's transport lists (BSD 2-Clause, see NOTICE).

Usage: build_walk_links.py <shortest-path>/src/main/resources/transports

Keeps short same-floor links (doors, gates, stiles, shortcuts) that need nothing but, at most, an
Agility level: no items, quests or game state checks. Output columns:
originX originY plane destX destY agilityLevel durationTicks
"""
import os
import re
import sys

MAX_SPAN = 15


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
            yield dict(zip(header, cols))


def main():
    src = sys.argv[1]
    out = []
    for name in ("transports.tsv", "agility_shortcuts.tsv"):
        for r in rows(os.path.join(src, name)):
            try:
                ox, oy, oz = map(int, r["Origin"].split())
                dx, dy, dz = map(int, r["Destination"].split())
            except (KeyError, ValueError):
                continue
            if oz != dz or max(abs(ox - dx), abs(oy - dy)) > MAX_SPAN:
                continue
            if any(r.get(k, "").strip() for k in ("Items", "Quests", "Varbits", "VarPlayers")):
                continue
            agility = 0
            skills = r.get("Skills", "").strip()
            if skills:
                m = re.fullmatch(r"(\d+) Agility", skills)
                if not m:
                    continue
                agility = int(m.group(1))
            try:
                duration = int(r.get("Duration", "").strip() or 1)
            except ValueError:
                duration = 1
            out.append((ox, oy, oz, dx, dy, agility, duration))
    out = sorted(set(out))
    dest = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "herbrunhelper", "walk-links.tsv")
    with open(dest, "w", encoding="utf-8") as f:
        f.write("# Doors, gates and agility shortcuts for the guide line, from Shortest Path (see NOTICE).\n")
        f.write("# originX originY plane destX destY agility duration\n")
        for o in out:
            f.write("\t".join(map(str, o)) + "\n")
    print(len(out), "links")


if __name__ == "__main__":
    main()
