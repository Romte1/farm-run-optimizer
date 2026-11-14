#!/usr/bin/env python3
"""
Checks FarmPatch.java against RuneLite's Time Tracking FarmingWorld.java: every patch's
type, varbit and regions must match a RuneLite farming region.

Usage: python3 scripts/verify_patches.py <path to runelite/.../timetracking/farming>
"""
import re
import sys
from pathlib import Path

LETTER = {c: 4771 + i for i, c in enumerate("ABCDE")}
LETTER.update({c: 7904 + i for i, c in enumerate("FGHIJKLMN")})
TYPE = {"ALLOTMENT": "ALLOTMENT", "HERB": "HERB", "TREE": "TREE", "FRUIT_TREE": "FRUIT_TREE", "HARDWOOD": "HARDWOOD_TREE"}


def runelite_patches(world):
    out = []
    starts = [m.start() for m in re.finditer(r"add\((?:\w+ = )?new FarmingRegion\(", world)]
    for k, st in enumerate(starts):
        chunk = world[st:starts[k + 1] if k + 1 < len(starts) else len(world)]
        head = re.match(r"add\((?:\w+ = )?new FarmingRegion\(\"([^\"]+)\",\s*(\d+)", chunk)
        regions = [int(head.group(2))]
        tail = re.findall(r",\s*(\d+(?:\s*,\s*\d+)*)\);", chunk)
        if tail:
            regions += [int(x) for x in re.findall(r"\d+", tail[-1])]
        for p in re.finditer(r"FARMING_TRANSMIT_([A-N]),\s*PatchImplementation\.(\w+)", chunk):
            out.append((head.group(1), set(regions), LETTER[p.group(1)], p.group(2)))
    return out


def our_patches(java):
    out = []
    for m in re.finditer(r"^\t([A-Z_]+)\(\"([^\"]+)\", PatchType\.(\w+), Requirement\.\w+, \d+, \d+,\s*r\(([\d,\s]+)\), (\d+),", java, re.M | re.S):
        out.append((m.group(1), m.group(3), [int(x) for x in m.group(4).split(",")], int(m.group(5))))
    return out


def main(folder):
    world = (Path(folder) / "FarmingWorld.java").read_text()
    ours = our_patches((Path(__file__).resolve().parent.parent / "src/main/java/com/herbrunhelper/FarmPatch.java").read_text())
    rl = runelite_patches(world)
    bad = 0
    for name, ptype, regions, varbit in ours:
        hits = [r for r in rl if r[3] == TYPE[ptype] and r[2] == varbit and regions[0] in r[1]]
        if not hits:
            bad += 1
            print("MISMATCH", name, ptype, regions, varbit)
        elif set(regions) - hits[0][1]:
            print("extra regions", name, set(regions) - hits[0][1], "runelite:", sorted(hits[0][1]))
        else:
            print("ok", name, "->", hits[0][0])
    print(len(ours), "patches checked,", bad, "mismatches")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main(sys.argv[1])
