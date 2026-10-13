#!/usr/bin/env python3
"""Audit level-2 boundary coverage against 7.1 WorldAreaConfigData.json.

No geometry is inferred or generated. A WorldAreaConfigData row without an
outline remains unresolved, not automatically assigned to its parent polygon.

Run from the AstaPS checkout:
  python tools/audit_world_area_level2.py --resources resources --scene 3
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path


def audit(config: list[dict], outlines: dict[int, dict]) -> dict:
    """Report matching scene/areaID1/areaID2 entries and duplicated polygons."""
    configured = defaultdict(dict)
    for row in config:
        if row.get("MBOHIOJBBHO") != "LEVEL_2":
            continue
        scene, area1, area2 = row["sceneID"], row["areaID1"], row["areaID2"]
        if area2 in configured[scene]:
            raise ValueError(f"Duplicate level-2 config: {scene}:{area2}")
        configured[scene][area2] = {
            "areaID1": area1, "configID": row["ID"],
            "nameHash": row["areaNameTextMapHash"],
        }

    report = {}
    for scene, source in sorted(outlines.items()):
        polygon_count = Counter()
        parents = {}
        bounds_with_y = 0
        for parent in source.get("OBKNAJCIHEA", []):
            level1 = parent.get("LKBLPFHOAFB") or {}
            for sub in parent.get("OPHHFKKPLDI") or []:
                area2 = sub.get("EOIFEEDBAFJ")
                if not isinstance(area2, int) or area2 <= 0:
                    continue
                parent_id = level1.get("BBLLBIOLMHP")
                polygon_count[area2] += 1
                if area2 in parents and parents[area2] != parent_id:
                    raise ValueError(f"Level-2 ID {area2} has conflicting parents in scene {scene}")
                parents[area2] = parent_id
                shape = sub.get("JLFEAGHGCLP") or {}
                low = (shape.get("MHLOLALFHHF") or {}).get("_y")
                high = (shape.get("NFFDCMLAAFO") or {}).get("_y")
                if low != -1000 or high != 1000:
                    bounds_with_y += 1

        expected = configured.get(scene, {})
        missing = [
            {"areaID2": area2, **meta}
            for area2, meta in sorted(expected.items())
            if area2 not in polygon_count
        ]
        parent_mismatches = [
            {"areaID2": area2, "configAreaID1": expected[area2]["areaID1"],
             "outlineAreaID1": parent}
            for area2, parent in sorted(parents.items())
            if area2 in expected and expected[area2]["areaID1"] != parent
        ]
        report[str(scene)] = {
            "configured_level2_ids": len(expected),
            "outlined_level2_ids": len(polygon_count),
            "level2_polygons": sum(polygon_count.values()),
            "non_default_y_bounds": bounds_with_y,
            "repeated_polygon_ids": {str(k): count for k, count in sorted(polygon_count.items())
                                     if count > 1},
            "missing_config_ids_with_no_outline": missing,
            "outline_ids_not_in_config": sorted(set(polygon_count) - set(expected)),
            "parent_mismatches": parent_mismatches,
        }
    return {"scenes": report, "scope": "Only supplied scene boundary files; no guessed geometry"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=Path("resources"))
    parser.add_argument("--scene", type=int,
                        help="Audit one scene (default: all scenes with world-area boundary files)")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    base = args.resources
    config = json.loads((base / "ExcelBinOutput/WorldAreaConfigData.json").read_text(encoding="utf-8"))
    paths = base / "BinOutput/Scene/WorldArea"
    names = ([paths / f"scene{args.scene}_worldArea.json"] if args.scene is not None
             else sorted(paths.glob("scene*_worldArea.json")))
    outlines = {}
    for path in names:
        if not path.is_file():
            raise FileNotFoundError(f"Missing required scene boundary file: {path}")
        scene = int(path.name.removeprefix("scene").removesuffix("_worldArea.json"))
        outlines[scene] = json.loads(path.read_text(encoding="utf-8"))
    result = audit(config, outlines)
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
