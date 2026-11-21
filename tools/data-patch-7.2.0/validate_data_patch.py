#!/usr/bin/env python3
"""Inspect the 7.2.0 patch or verify its applied state on disk."""

import argparse
import json
import sys
from pathlib import Path

from data_patch import load_json, plan_patch, print_problems


def check_expectations(report, expected):
    if not isinstance(expected, dict) or any(
        not isinstance(fields, dict) for fields in expected.values()
    ):
        raise ValueError("Expected a JSON object with report sections")
    problems = []
    for name in ("avatar", "chapter"):
        current = report[name]
        if current["table"]["already_applied"] != current["fields"]:
            problems.append(f"{name}: not all patched fields and their text are present on disk")
    added = report["textmap_added"]
    if added["already_present"] != added["entries"]:
        problems.append("TextMapCHS.json: not all added entries match on disk")

    for section, fields in expected.items():
        current = report.get(section)
        if not isinstance(current, dict):
            problems.append(f"No such report section: {section}")
            continue
        for field, want in fields.items():
            got = current.get(field)
            if got != want:
                problems.append(f"{section}.{field}: expected {want!r}, got {got!r}")
    return problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resources", type=Path)
    parser.add_argument("--expect", type=Path, help="Verify the applied state and expected report")
    args = parser.parse_args(argv)
    try:
        _, report, problems = plan_patch(args.resources)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        if args.expect is not None:
            expected = load_json(args.expect)
            problems.extend(check_expectations(report, expected))
        print_problems(problems)
        return int(bool(problems))
    except (OSError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
