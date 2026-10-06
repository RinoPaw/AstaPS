#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

COMPILER_VERSION = "0.1.0"
MANIFEST_NAME = ".resource-compiler.json"
REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
KNOWN_TABLES = (
    "DropTableExcelConfigData",
    "DropSubTableExcelConfigData",
    "StatuePromoteExcelConfigData",
    "DropMaterialExcelConfigData",
    "TalkExcelConfigData",
)
TABLE_EXTENSIONS = ("tsj", "json", "tsv")


@dataclass
class DuplicateReport:
    file: str
    duplicate_keys: int
    keys: list[str]


def eprint(*args: object) -> None:
    print(*args, file=sys.stderr)


def require_source(source: Path) -> None:
    missing = [name for name in ("BinOutput", "ExcelBinOutput") if not (source / name).is_dir()]
    if missing:
        noun = "directory" if len(missing) == 1 else "directories"
        raise SystemExit(f"Source {source} is missing required {noun}: " + ", ".join(missing))


def paths_overlap(first: Path, second: Path) -> bool:
    return first == second or first in second.parents or second in first.parents


def validate_build_paths(source: Path, output: Path, overlay: Path | None) -> None:
    if output.parent == output:
        raise SystemExit(f"Output must not be a filesystem root: {output}")

    if output == REPOSITORY_ROOT or output in REPOSITORY_ROOT.parents:
        raise SystemExit(
            f"Output must not be the repository root or one of its ancestors: {output}"
        )

    if paths_overlap(source, output):
        raise SystemExit(f"Source and output paths must not overlap: {source}, {output}")

    if overlay is not None and paths_overlap(overlay, output):
        raise SystemExit(f"Overlay and output paths must not overlap: {overlay}, {output}")

    if overlay is not None and overlay.exists() and not overlay.is_dir():
        raise SystemExit(f"Overlay path is not a directory: {overlay}")


def require_generated_output(output: Path) -> None:
    if not output.is_dir():
        raise SystemExit(
            f"Refusing --force because output is not a generated resource directory: {output}"
        )

    manifest_path = output / MANIFEST_NAME
    try:
        with manifest_path.open("r", encoding="utf-8") as file:
            manifest = json.load(file)
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(
            f"Refusing --force because output is not a generated resource directory: {output} "
            f"({MANIFEST_NAME} is missing or invalid: {exc})"
        ) from exc

    valid_manifest = (
        isinstance(manifest, dict)
        and isinstance(manifest.get("compiler_version"), str)
        and isinstance(manifest.get("game_version"), str)
        and isinstance(manifest.get("generated_at"), str)
        and isinstance(manifest.get("source"), str)
        and isinstance(manifest.get("normalizers"), dict)
        and (manifest.get("overlay") is None or isinstance(manifest.get("overlay"), str))
    )
    if not valid_manifest:
        raise SystemExit(
            f"Refusing --force because output is not a generated resource directory: {output} "
            f"({MANIFEST_NAME} has an unexpected format)"
        )


def load_json_last_wins(path: Path) -> tuple[Any, DuplicateReport | None]:
    duplicate_keys: list[str] = []

    def pairs_hook(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                duplicate_keys.append(key)
            result[key] = value
        return result

    with path.open("r", encoding="utf-8") as file:
        data = json.load(file, object_pairs_hook=pairs_hook)

    if not duplicate_keys:
        return data, None

    unique_keys = list(dict.fromkeys(duplicate_keys))
    return data, DuplicateReport(
        file=str(path),
        duplicate_keys=len(duplicate_keys),
        keys=unique_keys,
    )


def write_json(path: Path, data: Any) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
        file.write("\n")


def normalize_ability_json(output: Path) -> list[DuplicateReport]:
    root = output / "BinOutput" / "Ability" / "Temp"
    reports: list[DuplicateReport] = []

    if not root.is_dir():
        eprint(f"[warn] Ability temp directory is missing: {root}")
        return reports

    for path in root.rglob("*.json"):
        try:
            data, report = load_json_last_wins(path)
        except (OSError, json.JSONDecodeError) as exc:
            eprint(f"[warn] Could not parse {path}: {exc}")
            continue

        if report is None:
            continue

        report.file = str(path.relative_to(output))
        reports.append(report)
        write_json(path, data)

    return reports


def copy_source(source: Path, output: Path) -> None:
    shutil.copytree(
        source,
        output,
        ignore=shutil.ignore_patterns(".git", ".gitignore"),
    )


def overlay_tree(overlay: Path, output: Path) -> None:
    if not overlay.exists():
        return

    if not overlay.is_dir():
        raise SystemExit(f"Overlay path is not a directory: {overlay}")

    shutil.copytree(
        overlay,
        output,
        dirs_exist_ok=True,
        ignore=shutil.ignore_patterns(".git"),
    )


def find_table(root: Path, stem: str) -> Path | None:
    for parent in (root / "Server", root / "ExcelBinOutput"):
        for ext in TABLE_EXTENSIONS:
            candidate = parent / f"{stem}.{ext}"
            if candidate.is_file():
                return candidate
    return None


def inspect_scene_point_shapes(root: Path, limit: int = 10) -> tuple[int, int, list[str]]:
    point_dir = root / "BinOutput" / "Scene" / "Point"
    object_count = 0
    array_count = 0
    array_examples: list[str] = []

    if not point_dir.is_dir():
        return object_count, array_count, array_examples

    for path in point_dir.glob("scene*_point.json"):
        try:
            with path.open("r", encoding="utf-8") as file:
                value = json.load(file)
        except (OSError, json.JSONDecodeError):
            continue

        if isinstance(value, dict):
            object_count += 1
        elif isinstance(value, list):
            array_count += 1
            if len(array_examples) < limit:
                array_examples.append(str(path.relative_to(root)))

    return object_count, array_count, array_examples


def inspect_global_combat(root: Path) -> tuple[bool, list[str]]:
    path = root / "BinOutput" / "Common" / "ConfigGlobalCombat.json"
    if not path.is_file():
        return False, []

    try:
        with path.open("r", encoding="utf-8") as file:
            value = json.load(file)
    except (OSError, json.JSONDecodeError):
        return False, []

    if not isinstance(value, dict):
        return False, []

    keys = list(value.keys())
    return "defaultAbilities" in value, keys[:12]


def doctor(root: Path) -> int:
    print(f"Resource compiler doctor: {root}")

    problems = 0
    for dirname in ("BinOutput", "ExcelBinOutput", "TextMap"):
        present = (root / dirname).is_dir()
        print(f"[{'ok' if present else 'missing'}] {dirname}")
        if not present:
            problems += 1

    for dirname in ("Server", "ScriptSceneData", "Scripts"):
        present = (root / dirname).is_dir()
        print(f"[{'ok' if present else 'optional-missing'}] {dirname}")

    for stem in KNOWN_TABLES:
        table = find_table(root, stem)
        if table is None:
            print(f"[missing-table] {stem}.{{tsj,json,tsv}}")
            problems += 1
        else:
            print(f"[ok-table] {table.relative_to(root)}")

    has_default_abilities, keys = inspect_global_combat(root)
    if has_default_abilities:
        print("[ok] ConfigGlobalCombat.defaultAbilities")
    else:
        print("[needs-mapping] ConfigGlobalCombat.defaultAbilities")
        if keys:
            print("  sample top-level keys:", ", ".join(keys))
        problems += 1

    objects, arrays, examples = inspect_scene_point_shapes(root)
    print(f"[scene-points] object={objects}, array={arrays}")
    if arrays:
        problems += 1
        for example in examples:
            print(f"  [array] {example}")

    return problems


def build(args: argparse.Namespace) -> int:
    source = args.source.resolve()
    output = args.output.resolve()
    overlay = args.overlay.resolve() if args.overlay else None

    validate_build_paths(source, output, overlay)
    require_source(source)

    if output.exists():
        if not args.force:
            raise SystemExit(f"Output already exists: {output}. Use --force to replace it.")
        require_generated_output(output)
        shutil.rmtree(output)

    print(f"Copying raw resources: {source} -> {output}")
    copy_source(source, output)

    if overlay is not None and overlay.exists():
        print(f"Applying custom overlay: {overlay}")
        overlay_tree(overlay, output)

    duplicate_reports = normalize_ability_json(output)
    duplicate_count = sum(report.duplicate_keys for report in duplicate_reports)
    print(
        f"Normalized Ability JSON: {len(duplicate_reports)} file(s), "
        f"{duplicate_count} duplicate key occurrence(s)."
    )

    manifest = {
        "compiler_version": COMPILER_VERSION,
        "game_version": args.game_version,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": str(source),
        "overlay": str(overlay) if overlay and overlay.exists() else None,
        "normalizers": {
            "ability_duplicate_keys": [asdict(report) for report in duplicate_reports],
        },
    }
    write_json(output / MANIFEST_NAME, manifest)

    problems = doctor(output)
    print(f"Build complete with {problems} known compatibility issue(s).")
    return 0


def scan_duplicates(args: argparse.Namespace) -> int:
    root = args.root.resolve()
    target = root / "BinOutput" / "Ability" / "Temp"
    reports: list[DuplicateReport] = []

    if not target.is_dir():
        raise SystemExit(f"Directory does not exist: {target}")

    for path in target.rglob("*.json"):
        try:
            _, report = load_json_last_wins(path)
        except (OSError, json.JSONDecodeError) as exc:
            eprint(f"[warn] Could not parse {path}: {exc}")
            continue

        if report is not None:
            report.file = str(path.relative_to(root))
            reports.append(report)

    if not reports:
        print("No duplicate keys found in Ability/Temp JSON.")
        return 0

    for report in reports:
        print(
            f"{report.file}: {report.duplicate_keys} duplicate occurrence(s) "
            f"({', '.join(report.keys)})"
        )

    print(
        f"Found duplicates in {len(reports)} file(s), "
        f"{sum(report.duplicate_keys for report in reports)} occurrence(s)."
    )
    return 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build and diagnose AstaPS-compatible resources from an upstream game-data dump."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Build generated resources from a raw dump.")
    build_parser.add_argument(
        "--source", type=Path, default=Path("animegamedata2"), help="Raw resource dump."
    )
    build_parser.add_argument(
        "--output", type=Path, default=Path("resources"), help="Generated resource directory."
    )
    build_parser.add_argument(
        "--overlay",
        type=Path,
        default=Path("resources-custom"),
        help="Optional custom Server/Scripts/ScriptSceneData overlay.",
    )
    build_parser.add_argument("--game-version", default="7.1.0")
    build_parser.add_argument(
        "--force", action="store_true", help="Delete and rebuild an existing output directory."
    )
    build_parser.set_defaults(func=build)

    doctor_parser = subparsers.add_parser("doctor", help="Diagnose a resource directory.")
    doctor_parser.add_argument("root", type=Path, nargs="?", default=Path("resources"))
    doctor_parser.set_defaults(func=lambda args: doctor(args.root.resolve()))

    scan_parser = subparsers.add_parser(
        "scan-duplicates", help="Scan Ability/Temp for duplicate JSON object keys."
    )
    scan_parser.add_argument("root", type=Path, nargs="?", default=Path("resources"))
    scan_parser.set_defaults(func=scan_duplicates)

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
