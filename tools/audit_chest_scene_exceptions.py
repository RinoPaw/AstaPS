#!/usr/bin/env python3
"""Locate explicit scene chest IDs with absent or empty drop roots.

Classifies evidence, not legitimacy of zero-drop chests or official 7.1 rates.
Requires a local resource checkout with Scripts/Scene and ExcelBinOutput.
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re


SCENE_RE = re.compile(r"scene(\d+)_group")
ENTRY_RE = re.compile(r"\bgadgets\s*=\s*\{")
TAG_RE = re.compile(r"""\bdrop_tag\s*=\s*["']([^"']+)["']""")
CHEST_EXPLORE_RE = re.compile(r"""\bexplore\s*=\s*\{[^}]*\bname\s*=\s*["']chest["']""")
TRUE_RE = lambda key: re.compile(r"\b" + re.escape(key) + r"\s*=\s*true\b")


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def gadget_entries(source: str):
    """Extract top-level gadgets entries while ignoring braces in strings/comments."""
    for match in ENTRY_RE.finditer(source):
        depth = 0
        start = None
        quote = None
        comment = False
        escaped = False
        for index in range(match.end() - 1, len(source)):
            char = source[index]
            if comment:
                if char == "\n":
                    comment = False
                continue
            if quote is not None:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
                continue
            if source[index:index + 2] == "--":
                comment = True
                continue
            if char in "'\"":
                quote = char
                continue
            if char == "{":
                depth += 1
                if depth == 2:
                    start = index
            elif char == "}":
                if depth == 2 and start is not None:
                    yield source[start:index + 1]
                depth -= 1
                if depth == 0:
                    break


def num(entry: str, name: str) -> int:
    # Equivalent to the original scene coverage collector for numeric fields.
    match = re.search(r"\b" + re.escape(name) + r"\s*=\s*(\d+)", entry)
    return int(match.group(1)) if match else 0


def chest_types(excel_rows: list[dict]) -> set[int]:
    return {
        row["id"] for row in excel_rows
        if isinstance(row, dict) and isinstance(row.get("id"), int)
        and "chest" in str(row.get("type", row.get("gadgetType", ""))).lower()
    }


def row_status(row: dict | None) -> str:
    if row is None:
        return "missing-root"
    entries = row.get("dropVec") or []
    if not entries:
        if row.get("sourceType") == 0:
            return "empty-source-type-0"
        return "empty-other"
    if not any(
        isinstance(e, dict) and (e.get("weight") or 0) > 0
        and (e.get("itemId") or e.get("id") or 0) > 0
        for e in entries
    ):
        return "no-positive-entries"
    return "populated"


def collect(resources: Path):
    gadgets = chest_types(load(resources / "ExcelBinOutput/GadgetExcelConfigData.json"))
    tables = {}
    for filename in ("DropTableExcelConfigData.json", "DropSubTableExcelConfigData.json"):
        for row in load(resources / "Server" / filename):
            tables[row["id"]] = row
    records = []
    chest_count = 0
    explicit_count = 0
    for path in sorted((resources / "Scripts/Scene").rglob("*.lua")):
        match = SCENE_RE.search(path.name)
        scene = int(match.group(1)) if match else 0
        text = path.read_text(encoding="utf-8", errors="replace")
        for entry in gadget_entries(text):
            gadget = num(entry, "gadget_id")
            if gadget not in gadgets or "boss_chest" in entry:
                continue
            chest_count += 1
            drop_id = num(entry, "chest_drop_id")
            if drop_id == 0:
                continue
            explicit_count += 1
            status = row_status(tables.get(drop_id))
            if status == "populated":
                continue
            tag = TAG_RE.search(entry)
            records.append({
                "scene": scene,
                "file": str(path.relative_to(resources)).replace("\\", "/"),
                "gadget_id": gadget,
                "config_id": num(entry, "config_id"),
                "drop_id": drop_id,
                "drop_count": num(entry, "drop_count"),
                "tag": tag.group(1) if tag else "",
                "isOneoff": bool(TRUE_RE("isOneoff").search(entry)),
                "persistent": bool(TRUE_RE("persistent").search(entry)),
                "autopick": bool(TRUE_RE("autopick").search(entry)),
                "explore_chest": bool(CHEST_EXPLORE_RE.search(entry)),
                "status": status,
                "native_row": {
                    "sourceType": tables[drop_id].get("sourceType"),
                    "nodeType": tables[drop_id].get("nodeType"),
                    "randomType": tables[drop_id].get("randomType"),
                } if drop_id in tables else None,
            })
    return records, {"ordinary_chests": chest_count, "explicit_id_chests": explicit_count}


def build_report(records: list[dict], totals: dict) -> dict:
    summary = {}
    for status, recs in sorted(
        ((name, [rec for rec in records if rec["status"] == name])
         for name in sorted({rec["status"] for rec in records}))
    ):
        summary[status] = {
            "count": len(recs),
            "by_drop_id": dict(sorted(Counter(r["drop_id"] for r in recs).items())),
            "by_scene": dict(sorted(Counter(r["scene"] for r in recs).items())),
            "by_gadget": dict(sorted(Counter(r["gadget_id"] for r in recs).items())),
            "isOneoff": sum(r["isOneoff"] for r in recs),
            "persistent": sum(r["persistent"] for r in recs),
            "autopick": sum(r["autopick"] for r in recs),
            "explore_chest": sum(r["explore_chest"] for r in recs),
            "with_tag": sum(bool(r["tag"]) for r in recs),
            "examples": recs[:15],
        }
    return {
        "scope": "static Lua and resource linkage only; no inferred 7.1-native rewards",
        "totals": {**totals, "unresolved_explicit_chests": len(records)},
        "summary": summary,
        "unresolved_records": records,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=Path("resources"))
    parser.add_argument("--output", type=Path, help="write all per-chest evidence to JSON")
    args = parser.parse_args()
    records, totals = collect(args.resources.resolve())
    report = build_report(records, totals)
    if args.output:
        args.output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")
    print(json.dumps(
        {"totals": report["totals"], "summary": report["summary"]},
        ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
