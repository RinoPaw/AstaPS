#!/usr/bin/env python3
"""Apply the 7.2.0 name hash patch to the active JSON resource tables."""

import argparse
import json
import sys
from pathlib import Path

from data_patch import plan_patch, print_problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resources", type=Path)
    parser.add_argument("--dry", action="store_true", help="Preview without writing resource files")
    args = parser.parse_args(argv)
    try:
        files, report, problems = plan_patch(args.resources)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        if problems:
            print_problems(problems)
            return 1
        if args.dry:
            return 0
        for resource in files:
            resource.check_unchanged()
        for resource in files:
            if resource.changes:
                backup = resource.write()
                print(f"Updated {resource.path}. Backup: {backup}")
    except (OSError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
