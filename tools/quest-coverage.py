#!/usr/bin/env python3
"""Count quest types against registered handlers; registration is not an end-to-end test."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re


def audit(table, source):
    raw = table.read_bytes()
    rows = json.loads(raw)
    result = {"quest_rows": len(rows), "table_sha256": hashlib.sha256(raw).hexdigest()}
    for kind, slots, annotation, prefix, folder in (
        ("conditions", ("finishCond", "failCond"), "QuestValueContent", "QUEST_CONTENT_", "content"),
        ("execs", ("beginExec", "finishExec", "failExec"), "QuestValueExec", "QUEST_EXEC_", "exec"),
    ):
        counts = Counter()
        for row in rows:
            for slot in slots:
                for entry in row.get(slot, []):
                    name = entry.get("type", entry.get("_type"))
                    if name:
                        counts[name] += 1
        registered = set()
        for path in (source / "src/main/java/emu/grasscutter/game/quest" / folder).glob("*.java"):
            for block in re.findall(r"@" + annotation + r"\s*\((.*?)\)", path.read_text(), re.S):
                registered.update(re.findall(prefix + r"[A-Z0-9_]+", block))
        missing = {name: count for name, count in counts.most_common() if name not in registered}
        result[kind] = {
            "total": sum(counts.values()),
            "with_registered_handler": sum(count for name, count in counts.items() if name in registered),
            "without_registered_handler": sum(missing.values()),
            "missing_types": missing,
        }
    result["limitation"] = "A registered handler may still lack an event source, state persistence, or client validation."
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("quest_table", type=Path)
    parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    print(json.dumps(audit(args.quest_table, args.source_root), indent=2, ensure_ascii=False))
