#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

TOOL_VERSION = "0.1.0"
DEFAULT_PROTO_ROOT = Path("proto")
DEFAULT_TRANSLATIONS = Path("local/nameTranslation.txt")
DEFAULT_PACKET_OPCODES = Path("src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java")
DEFAULT_REPORT = Path("local/protocol-report.json")

CMD_PATTERNS = (
    re.compile(r"\bCmdId\s*[:=]\s*(\d+)\b", re.IGNORECASE),
    re.compile(r"\bcmd[_-]?id\s*[:=]\s*(\d+)\b", re.IGNORECASE),
    re.compile(r"\boption\s*\(\s*cmd[_-]?id\s*\)\s*=\s*(\d+)\s*;", re.IGNORECASE),
)
MESSAGE_PATTERN = re.compile(r"\bmessage\s+([A-Za-z_][A-Za-z0-9_]*)\b")
OPCODE_PATTERN = re.compile(
    r"public\s+static\s+final\s+int\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(-?\d+)\s*;"
)


@dataclass(frozen=True)
class ProtoEntry:
    source: str
    raw_name: str
    semantic_name: str
    cmd_id: int


@dataclass(frozen=True)
class OpcodeEntry:
    name: str
    opcode: int


def parse_translation_line(line: str) -> tuple[str, str] | None:
    line = line.strip()
    if not line or line.startswith("#") or line.startswith("//"):
        return None

    for separator in ("->", "=>", "=", ":", "\t"):
        if separator in line:
            left, right = (part.strip() for part in line.split(separator, 1))
            if left and right and " " not in left:
                return left, right.split()[0]

    parts = line.split()
    if len(parts) >= 2:
        return parts[0], parts[1]
    return None


def load_translations(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}

    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        parsed = parse_translation_line(line)
        if parsed is not None:
            result[parsed[0]] = parsed[1]
    return result


def extract_cmd_ids(text: str) -> list[int]:
    ids: list[int] = []
    for pattern in CMD_PATTERNS:
        ids.extend(int(match.group(1)) for match in pattern.finditer(text))
    return list(dict.fromkeys(ids))


def infer_raw_names(path: Path, text: str) -> list[str]:
    names = list(dict.fromkeys(MESSAGE_PATTERN.findall(text)))
    if names:
        return names
    return [path.stem]


def scan_proto_root(root: Path, translations: dict[str, str]) -> tuple[list[ProtoEntry], list[str]]:
    entries: list[ProtoEntry] = []
    warnings: list[str] = []

    if not root.is_dir():
        warnings.append(f"proto root does not exist: {root}")
        return entries, warnings

    files = sorted(root.rglob("*.proto"))
    if not files:
        warnings.append(f"no .proto files found under: {root}")
        return entries, warnings

    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        cmd_ids = extract_cmd_ids(text)
        if not cmd_ids:
            continue

        names = infer_raw_names(path, text)
        # Most game dumps are one top-level packet per file. If several messages share one file and
        # there is only one CmdId annotation, the file stem is the least ambiguous identity.
        if len(names) != 1:
            names = [path.stem]

        if len(cmd_ids) > 1:
            warnings.append(
                f"multiple CmdIds in {path}: {', '.join(map(str, cmd_ids))}; skipped as ambiguous"
            )
            continue

        raw_name = names[0]
        semantic_name = translations.get(raw_name, translations.get(path.stem, raw_name))
        entries.append(
            ProtoEntry(
                source=str(path),
                raw_name=raw_name,
                semantic_name=semantic_name,
                cmd_id=cmd_ids[0],
            )
        )

    return entries, warnings


def parse_packet_opcodes(path: Path) -> tuple[dict[str, int], list[str]]:
    if not path.is_file():
        return {}, [f"PacketOpcodes.java does not exist: {path}"]

    text = path.read_text(encoding="utf-8", errors="replace")
    opcodes = {match.group(1): int(match.group(2)) for match in OPCODE_PATTERN.finditer(text)}
    warnings: list[str] = []
    if not opcodes:
        warnings.append(f"no opcode constants parsed from: {path}")
    return opcodes, warnings


def duplicates_by_cmd(entries: Iterable[ProtoEntry]) -> dict[int, list[str]]:
    grouped: dict[int, list[str]] = {}
    for entry in entries:
        grouped.setdefault(entry.cmd_id, []).append(entry.semantic_name)
    return {
        cmd_id: sorted(set(names))
        for cmd_id, names in grouped.items()
        if len(set(names)) > 1
    }


def conflicts_by_name(entries: Iterable[ProtoEntry]) -> dict[str, list[int]]:
    grouped: dict[str, set[int]] = {}
    for entry in entries:
        grouped.setdefault(entry.semantic_name, set()).add(entry.cmd_id)
    return {
        name: sorted(ids)
        for name, ids in grouped.items()
        if len(ids) > 1
    }


def build_report(
    proto_root: Path,
    translation_path: Path,
    packet_opcodes_path: Path,
) -> dict[str, object]:
    translations = load_translations(translation_path)
    proto_entries, proto_warnings = scan_proto_root(proto_root, translations)
    packet_opcodes, opcode_warnings = parse_packet_opcodes(packet_opcodes_path)

    extracted_by_name: dict[str, int] = {}
    for entry in proto_entries:
        extracted_by_name.setdefault(entry.semantic_name, entry.cmd_id)

    fills: list[dict[str, object]] = []
    mismatches: list[dict[str, object]] = []
    confirmed: list[dict[str, object]] = []
    absent_from_server: list[dict[str, object]] = []

    conflicted_names = set(conflicts_by_name(proto_entries))
    for name, cmd_id in sorted(extracted_by_name.items()):
        if name in conflicted_names:
            continue
        current = packet_opcodes.get(name)
        if current is None:
            absent_from_server.append({"name": name, "cmd_id": cmd_id})
        elif current == 0 and cmd_id > 0:
            fills.append({"name": name, "current": current, "cmd_id": cmd_id})
        elif current == cmd_id:
            confirmed.append({"name": name, "cmd_id": cmd_id})
        elif current != cmd_id:
            mismatches.append({"name": name, "current": current, "cmd_id": cmd_id})

    unresolved = sorted(name for name, value in packet_opcodes.items() if value == 0)

    return {
        "tool_version": TOOL_VERSION,
        "inputs": {
            "proto_root": str(proto_root),
            "translations": str(translation_path),
            "packet_opcodes": str(packet_opcodes_path),
        },
        "counts": {
            "translation_entries": len(translations),
            "proto_cmdids": len(proto_entries),
            "packet_opcode_constants": len(packet_opcodes),
            "confirmed": len(confirmed),
            "safe_fill_candidates": len(fills),
            "mismatches": len(mismatches),
            "unresolved_server_opcodes": len(unresolved),
        },
        "warnings": proto_warnings + opcode_warnings,
        "duplicate_cmd_ids": duplicates_by_cmd(proto_entries),
        "conflicting_names": conflicts_by_name(proto_entries),
        "confirmed": confirmed,
        "safe_fill_candidates": fills,
        "mismatches": mismatches,
        "proto_messages_absent_from_server_table": absent_from_server,
        "unresolved_server_opcodes": unresolved,
        "proto_entries": [asdict(entry) for entry in proto_entries],
    }


def print_report(report: dict[str, object]) -> None:
    counts = report["counts"]
    print(f"Protocol compiler {TOOL_VERSION}")
    print(f"proto CmdIds:            {counts['proto_cmdids']}")
    print(f"translations:            {counts['translation_entries']}")
    print(f"server opcode constants: {counts['packet_opcode_constants']}")
    print(f"confirmed:               {counts['confirmed']}")
    print(f"safe fill candidates:    {counts['safe_fill_candidates']}")
    print(f"mismatches:              {counts['mismatches']}")
    print(f"unresolved in server:    {counts['unresolved_server_opcodes']}")

    warnings = report["warnings"]
    if warnings:
        print("\nWarnings:")
        for warning in warnings:
            print(f"  - {warning}")

    conflicts = report["conflicting_names"]
    duplicate_ids = report["duplicate_cmd_ids"]
    if conflicts:
        print("\nConflicting semantic names (manual review required):")
        for name, ids in sorted(conflicts.items()):
            print(f"  {name}: {ids}")
    if duplicate_ids:
        print("\nCmdIds claimed by multiple semantic messages:")
        for cmd_id, names in sorted(duplicate_ids.items(), key=lambda item: int(item[0])):
            print(f"  {cmd_id}: {', '.join(names)}")

    fills = report["safe_fill_candidates"]
    if fills:
        print("\nSafe fill candidates (proposal only; source is not modified):")
        for item in fills:
            print(f"  {item['name']} = {item['cmd_id']}")

    mismatches = report["mismatches"]
    if mismatches:
        print("\nMismatches (do not auto-fix):")
        for item in mismatches:
            print(f"  {item['name']}: server={item['current']} dump={item['cmd_id']}")


def write_report(report: dict[str, object], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as file:
        json.dump(report, file, ensure_ascii=False, indent=2)
        file.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Audit a Genshin proto dump against AstaPS PacketOpcodes without modifying source."
    )
    parser.add_argument("command", choices=("doctor", "report"), nargs="?", default="doctor")
    parser.add_argument("--proto", type=Path, default=DEFAULT_PROTO_ROOT)
    parser.add_argument("--translations", type=Path, default=DEFAULT_TRANSLATIONS)
    parser.add_argument("--packet-opcodes", type=Path, default=DEFAULT_PACKET_OPCODES)
    parser.add_argument("--output", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()

    report = build_report(args.proto, args.translations, args.packet_opcodes)
    print_report(report)

    if args.command == "report":
        write_report(report, args.output)
        print(f"\nWrote: {args.output}")

    # Ambiguity is a warning, not a hard failure. Missing proto input is the one condition that
    # makes the audit unusable and should fail CI/scripts clearly.
    if any(str(warning).startswith("proto root does not exist") for warning in report["warnings"]):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
