#!/usr/bin/env python3
"""Audit AstaPS chest reward references without inventing missing 7.1 drops.

Run from any directory:
  python tools/audit_chest_data_alignment.py --server . --resources resources
Only reads files. "Resolved" means a configured root exists, NOT verified native
7.1 content, a complete subtree, or successful in-game payout.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path


SYNTH_FILES = (
    "nodkrai_explore_points.json",
    "snezhnaya_explore_points.json",
    "region_explore_points.json",
)
REGIONS = ("挪德卡莱", "至冬", "枫丹", "纳塔", "须弥")


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def region_for(point: dict) -> str:
    point_id = point["id"]
    if 40000 <= point_id < 50000:
        return "须弥"
    if 30000 <= point_id < 40000:
        return "纳塔"
    if 20000 <= point_id < 30000:
        return "枫丹"
    if 10000 <= point_id < 20000:
        return "至冬"
    return "挪德卡莱"


def legacy_tag(point: dict) -> str:
    kind = point.get("kind")
    tier = {
        "chest_luxurious": "超级",
        "chest_remarkable": "超级",  # existing code, NOT validated loot
        "chest_precious": "高级",
        "chest_exquisite": "中级",
        "chest_puzzle": "中级",
    }.get(kind, "低级")
    return "解谜" + tier + region_for(point)


def mora_candidate_tag(point: dict) -> str:
    """Proposed mapping to an EXISTING pool, not a claim of 7.1 native rates."""
    if point.get("kind") == "chest_mora" and region_for(point) != "挪德卡莱":
        return "摩拉石箱" + region_for(point)
    return legacy_tag(point)


def assess(server: Path, resources: Path) -> dict:
    chest = load_json(server / "data" / "ChestDrop.json")
    root_rows = load_json(resources / "Server" / "DropTableExcelConfigData.json")
    sub_rows = load_json(resources / "Server" / "DropSubTableExcelConfigData.json")
    roots = {row["id"] for row in root_rows}
    subroots = {row["id"] for row in sub_rows}
    known = roots | subroots
    tags = {row["index"] for row in chest}
    missing = [row for row in chest if row["dropId"] not in known]
    missing_tags = Counter(row["index"] for row in missing)
    by_source = {}
    synth = []
    for filename in SYNTH_FILES:
        points = load_json(server / "data" / filename)
        chests = [point for point in points if point.get("kind", "").startswith("chest")]
        by_source[filename] = {
            "total_points": len(points),
            "chests": len(chests),
            "with_reward_reference": sum(
                bool(point.get("drop_tag") or point.get("chest_drop_id"))
                for point in chests
            ),
            "kinds": dict(sorted(Counter(p["kind"] for p in chests).items())),
        }
        synth.extend(chests)
    by_region = defaultdict(lambda: {"total": 0, "legacy_tag_found": 0,
                                      "mora_candidate_tag_found": 0,
                                      "unresolved_after_mora_candidate": 0})
    proposed_mora = 0
    newly_resolved_mora = 0
    remarkable_as_luxurious = 0
    for p in synth:
        data = by_region[region_for(p)]
        data["total"] += 1
        legacy = legacy_tag(p)
        candidate = mora_candidate_tag(p)
        previous = legacy in tags
        now = candidate in tags
        data["legacy_tag_found"] += previous
        data["mora_candidate_tag_found"] += now
        data["unresolved_after_mora_candidate"] += not now
        if p.get("kind") == "chest_mora" and candidate != legacy and now:
            proposed_mora += 1
            newly_resolved_mora += not previous
        if p.get("kind") == "chest_remarkable" and "超级" in legacy:
            remarkable_as_luxurious += 1

    nod_source = (server / "src/main/java/emu/grasscutter/game/world/"
                  "NodKraiExploreSpawnHelper.java").read_text(encoding="utf-8")
    snezh_source = (server / "src/main/java/emu/grasscutter/game/world/"
                    "SnezhnayaExploreSpawnHelper.java").read_text(encoding="utf-8")
    scene_source = (server / "src/main/java/emu/grasscutter/game/world/"
                    "Scene.java").read_text(encoding="utf-8")
    double_load = (
        "loadPointsFile(SNEZHNAYA_POINTS_FILE, list)" in nod_source
        and 'POINTS_FILE = Path.of("data", "snezhnaya_explore_points.json")'
        in snezh_source
        and "NodKraiExploreSpawnHelper.ensureNearby(this)" in scene_source
        and "SnezhnayaExploreSpawnHelper.ensureNearby(this)" in scene_source
    )
    snezh_points = load_json(server / "data" / SYNTH_FILES[1])
    in_shared_band = [
        p for p in snezh_points
        if 7000 <= p["x"] <= 11000 and 5200 <= p["z"] <= 8800
    ]
    return {
        "scope": "static source and drop-root reference alignment; not live/native-rate validation",
        "chest_drop": {
            "rows": len(chest),
            "unique_tags": len(tags),
            "root_table_rows": len(root_rows),
            "sub_table_rows": len(sub_rows),
            "missing_root_rows": len(missing),
            "missing_root_tags": dict(sorted(missing_tags.items())),
        },
        "synthetic": {
            "source_files": by_source,
            "chests": len(synth),
            "legacy_tag_found": sum(v["legacy_tag_found"] for v in by_region.values()),
            "mora_candidate_count": proposed_mora,
            "newly_found_by_mora_candidate": newly_resolved_mora,
            "candidate_tag_found": sum(v["mora_candidate_tag_found"] for v in by_region.values()),
            "unresolved_after_candidate": sum(
                v["unresolved_after_mora_candidate"] for v in by_region.values()),
            "remarkable_mapped_as_luxurious": remarkable_as_luxurious,
            "by_region": {key: by_region[key] for key in REGIONS},
        },
        "ownership": {
            "snezhnaya_loaded_by_two_spawners": double_load,
            "snezhnaya_points_within_both_runtime_bands": len(in_shared_band),
            "snezhnaya_chests_at_risk_of_double_spawn": (
                sum(p.get("kind", "").startswith("chest") for p in in_shared_band)
                if double_load else 0),
            "note": "Source-level duplicate candidates; claimed-state behavior is separate.",
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--resources", type=Path, default=Path("resources"))
    parser.add_argument("--output", type=Path, help="write an audit JSON snapshot")
    args = parser.parse_args()
    report = assess(args.server.resolve(), args.resources.resolve())
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
