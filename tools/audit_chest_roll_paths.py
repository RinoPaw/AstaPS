#!/usr/bin/env python3
"""Read-only audit of terminal drop paths in 7.1 scene chests.

A guaranteed terminal ID is NOT proof of an existing item, grant success, or
native 7.1 probabilities. This models only DropSystem.processDrop's randomType
0 (weighted choice) and 1 (independent 1/10000 rolls) as currently implemented.

Run from the AstaPS checkout:
  python -m tools.audit_chest_roll_paths --server . --resources resources
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
import json
from pathlib import Path

from tools.audit_chest_scene_exceptions import (
    SCENE_RE, TAG_RE, chest_types, gadget_entries, load, num,
)

# The ItemData @ResourceType list on the AstaPS server (7.1).
ITEM_TABLES = (
    "MaterialExcelConfigData.json",
    "WeaponExcelConfigData.json",
    "ReliquaryExcelConfigData.json",
    "HomeWorldFurnitureExcelConfigData.json",
    "TpsWeaponExcelConfigData.json",
)
NON_BOSS_SOURCES = frozenset({15, 17, 19, 24, 2001, 2104, 2501})


@dataclass(frozen=True)
class PathResult:
    possible_terminal: bool = False
    guaranteed_terminal: bool = False
    empty_nodes: frozenset[int] = frozenset()
    invalid_nodes: frozenset[int] = frozenset()
    terminal_ids: frozenset[int] = frozenset()


def count_bounds(value: object) -> tuple[bool, bool] | None:
    """Can count be positive? Is it always >= 1? None means unsupported."""
    try:
        if not isinstance(value, str):
            return None
        if ";" in value:
            parts = value.split(";")
            if len(parts) != 2:
                return None
            low, high = (int(s) for s in parts)
            if low < 0 or high < low:
                return None
            return high >= 1, low >= 1
        # Match DropSystem.calculateDropAmount: only dotted strings use
        # Double.parseDouble; other scalar strings use Integer.parseInt.
        # For example, Java rejects "1e1" (which Python float accepts).
        amount = float(value) if "." in value else int(value)
        if not (0 <= amount < float("inf")):
            return None
        return amount > 0, amount >= 1
    except (TypeError, ValueError, OverflowError):
        return None


def analyze_root(root_id: int, table: dict[int, dict]) -> PathResult:
    """Conservative graph analysis. Terminal IDs are not validated as items."""
    def walk(node_id: int, stack: frozenset[int]) -> PathResult:
        if node_id not in table or node_id in stack or len(stack) >= 80:
            return PathResult(invalid_nodes=frozenset({node_id}))
        node = table[node_id]
        entries = node.get("dropVec") or []
        if not isinstance(entries, list):
            return PathResult(invalid_nodes=frozenset({node_id}))
        eligible = []
        invalid = set()
        for entry in entries:
            if not isinstance(entry, dict):
                invalid.add(node_id)
                continue
            child_id, weight = entry.get("itemId", 0), entry.get("weight", 0)
            if not isinstance(child_id, int) or not isinstance(weight, int):
                invalid.add(node_id)
                continue
            if child_id <= 0:
                continue
            if weight < 0:
                invalid.add(node_id)
            if weight > 0:
                eligible.append((child_id, weight, entry.get("countRange")))
        if not eligible:
            return PathResult(
                empty_nodes=frozenset({node_id}), invalid_nodes=frozenset(invalid))
        rtype = node.get("randomType", 0)
        if rtype not in (0, 1):
            invalid.add(node_id)
        choices = []
        empty, terminals = set(), set()
        for child_id, weight, count in eligible:
            bounds = count_bounds(count)
            if bounds is None:
                invalid.add(node_id)
                continue
            can_count, certain_count = bounds
            child = (walk(child_id, stack | {node_id})
                     if child_id in table
                     else PathResult(True, True,
                                     terminal_ids=frozenset({child_id})))
            empty.update(child.empty_nodes)
            invalid.update(child.invalid_nodes)
            terminals.update(child.terminal_ids)
            choices.append((weight, can_count and child.possible_terminal,
                            certain_count and child.guaranteed_terminal))
        possible = any(v[1] for v in choices)
        # randomType 0 always chooses one positive-weight entry if its weights
        # are nonnegative; randomType 1 only guarantees entries with weight >= 10000.
        if rtype == 0:
            guaranteed = (bool(choices) and len(choices) == len(eligible)
                          and all(v[2] for v in choices)
                          and not invalid)
        elif rtype == 1:
            guaranteed = any(v[0] >= 10000 and v[2] for v in choices)
        else:
            guaranteed = False
        return PathResult(possible, guaranteed, frozenset(empty),
                          frozenset(invalid), frozenset(terminals))

    return walk(root_id, frozenset())


def audit(server: Path, resources: Path) -> dict:
    nodes = {}
    for filename in ("DropTableExcelConfigData.json", "DropSubTableExcelConfigData.json"):
        for row in load(resources / "Server" / filename):
            node_id = row["id"]
            if node_id in nodes:
                raise ValueError(f"Duplicate drop node {node_id}")
            nodes[node_id] = row
    tags = defaultdict(list)
    chest_rows = load(server / "data/ChestDrop.json")
    for row in chest_rows:
        tags[row["index"]].append(row)
    item_ids = set()
    for filename in ITEM_TABLES:
        for row in load(resources / "ExcelBinOutput" / filename):
            if isinstance(row, dict) and isinstance(row.get("id"), int):
                item_ids.add(row["id"])
    for rows in tags.values():
        rows.sort(key=lambda row: row.get("minLevel", 0))
    gadget_ids = chest_types(load(resources / "ExcelBinOutput/GadgetExcelConfigData.json"))
    cache = {}
    missing_terminals = {}
    totals, by_source, by_path = Counter(), Counter(), Counter()
    examples = defaultdict(list)
    missing_item_examples = []
    def evaluate(drop_id: int) -> PathResult:
        if drop_id not in cache:
            cache[drop_id] = analyze_root(drop_id, nodes)
            missing_terminals[drop_id] = sorted(cache[drop_id].terminal_ids - item_ids)
        return cache[drop_id]
    for path in sorted((resources / "Scripts/Scene").rglob("*.lua")):
        scene_match = SCENE_RE.search(path.name)
        scene_id = int(scene_match.group(1)) if scene_match else 0
        source = path.read_text(encoding="utf-8", errors="replace")
        totals["lua_files"] += 1
        for entry in gadget_entries(source):
            if num(entry, "gadget_id") not in gadget_ids or "boss_chest" in entry:
                continue
            totals["ordinary_chests"] += 1
            drop_id = num(entry, "chest_drop_id")
            tag_match = TAG_RE.search(entry)
            if drop_id:
                source_kind = "explicit_id"
            elif tag_match:
                source_kind = "drop_tag"
                level = max(1, num(entry, "level"))
                matches = [r for r in tags.get(tag_match.group(1), ())
                           if 0 < r.get("minLevel", 0) <= level]
                drop_id = matches[-1]["dropId"] if matches else 0
            else:
                source_kind = "no_metadata"
            by_source[source_kind] += 1
            if not drop_id:
                status = "no_metadata" if source_kind == "no_metadata" else "tag_unmatched"
            elif drop_id not in nodes:
                status = "root_missing"
            else:
                outcome = evaluate(drop_id)
                if missing_terminals[drop_id]:
                    totals["chests_with_missing_item_ids"] += 1
                    if len(missing_item_examples) < 15:
                        missing_item_examples.append({
                            "root_id": drop_id,
                            "scene": scene_id,
                            "missing_item_ids": missing_terminals[drop_id][:20],
                        })
                if drop_id in outcome.empty_nodes:
                    status = "root_empty"
                elif outcome.invalid_nodes:
                    status = "invalid_graph"
                elif outcome.guaranteed_terminal:
                    status = "guaranteed_terminal"
                elif outcome.possible_terminal:
                    status = "terminal_possible_not_guaranteed"
                else:
                    status = "no_positive_terminal_path"
                if outcome.empty_nodes and drop_id not in outcome.empty_nodes:
                    totals["chests_with_empty_descendants"] += 1
                    if outcome.guaranteed_terminal:
                        totals["empty_descendants_but_guaranteed_terminal"] += 1
                if outcome.invalid_nodes:
                    totals["chests_with_invalid_nodes"] += 1
            by_path[status] += 1
            if len(examples[status]) < 12:
                examples[status].append({
                    "scene": scene_id,
                    "file": str(path.relative_to(resources)).replace("\\", "/"),
                    "config_id": num(entry, "config_id"),
                    "root_id": drop_id,
                    "source": source_kind,
                })
    # Independently audit every configured ordinary/non-Boss tag, including
    # tags unused by scenes. Unknown item IDs are reported, never substituted.
    bridge = [r for r in chest_rows if r.get("sourceType") in NON_BOSS_SOURCES]
    for row in bridge:
        if row["dropId"] in nodes:
            evaluate(row["dropId"])
    bridge_results = [evaluate(row["dropId"]) for row in bridge
                      if row["dropId"] in nodes]
    bridge_missing_ids = sorted({item_id for row in bridge
                                 for item_id in missing_terminals.get(row["dropId"], ())})
    all_missing_ids = sorted({item_id for values in missing_terminals.values()
                              for item_id in values})
    all_terminal_ids = set().union(*(res.terminal_ids for res in cache.values()))
    return {
        "scope": "Static DropSystem roll-path approximation with 7.1 ItemData ID cross-check; no inventory/grant verification",
        "totals": dict(totals),
        "sources": dict(by_source),
        "path_status": dict(by_path),
        "unique_roots_analyzed": len(cache),
        "item_validation": {
            "item_ids_loaded": len(item_ids),
            "distinct_terminal_ids": len(all_terminal_ids),
            "missing_terminal_ids": all_missing_ids,
            "scene_chests_with_missing_item_ids": totals["chests_with_missing_item_ids"],
            "scene_missing_item_examples": missing_item_examples,
        },
        "non_boss_bridge": {
            "rows": len(bridge),
            "existing_roots": len(bridge_results),
            "guaranteed_terminal_rows": sum(r.guaranteed_terminal for r in bridge_results),
            "rows_with_empty_descendants": sum(bool(r.empty_nodes - {row["dropId"]})
                for row, r in zip((row for row in bridge if row["dropId"] in nodes),
                                  bridge_results)),
            "missing_terminal_ids": bridge_missing_ids,
        },
        "examples": dict(examples),
        "notes": [
            "All checked terminal IDs are compared with the five ItemData resource files; presence does not guarantee inventory acceptance.",
            "A reachable empty descendant does not imply missing rewards.",
            "This audit does not establish native 7.1 rates or chest claim semantics.",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, default=Path("."))
    parser.add_argument("--resources", type=Path, default=Path("resources"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = audit(args.server.resolve(), args.resources.resolve())
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
