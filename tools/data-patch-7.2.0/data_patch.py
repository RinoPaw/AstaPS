"""Plan resource edits and inspect the same on-disk state for validation."""

from __future__ import annotations

import json
import os
import shutil
import tempfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
TABLE_NAMES = {
    "avatar": "AvatarExcelConfigData",
    "chapter": "ChapterExcelConfigData",
}
EXCEL_SUFFIXES = (".tsj", ".json", ".tsv")


def load_json(path):
    with path.open(encoding="utf-8-sig") as source:
        return json.load(source)


def excel_path(resources, name):
    # Match the server's resource override and extension order.
    for directory in ("Server", "ExcelBinOutput"):
        for suffix in EXCEL_SUFFIXES:
            path = resources / directory / (name + suffix)
            if path.exists():
                if suffix != ".json":
                    raise ValueError(f"Active table {path} is not JSON")
                return path.resolve(strict=True)
    raise FileNotFoundError(f"Missing active table {name} in {resources}")


def has_text(textmap, key):
    text = textmap.get(str(key))
    return isinstance(text, str) and bool(text.strip())


def file_signature(path):
    stat = path.stat()
    return (stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns)


@dataclass
class ResourceFile:
    path: Path
    data: object
    signature: tuple[int, ...]
    changes: int = 0

    @classmethod
    def load(cls, path):
        signature = file_signature(path)
        resource = cls(path, load_json(path), signature)
        resource.check_unchanged()
        return resource

    def check_unchanged(self):
        if file_signature(self.path) != self.signature:
            raise ValueError(f"{self.path} changed during patching. Run the patch again.")

    def write(self):
        temp = None
        backup = None
        try:
            with tempfile.NamedTemporaryFile(
                dir=self.path.parent,
                prefix=f".{self.path.name}.",
                delete=False,
                mode="w",
                encoding="utf-8",
            ) as staged:
                temp = Path(staged.name)
                json.dump(self.data, staged, ensure_ascii=False)
                staged.write("\n")
                staged.flush()
                os.fsync(staged.fileno())
            self.check_unchanged()
            shutil.copymode(self.path, temp)

            stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            with tempfile.NamedTemporaryFile(
                dir=self.path.parent,
                prefix=f"{self.path.name}.bak_{stamp}_",
                delete=False,
            ) as backup_handle:
                backup = Path(backup_handle.name)
            try:
                shutil.copy2(self.path, backup)
            except OSError:
                backup.unlink(missing_ok=True)
                raise

            self.check_unchanged()
            temp.replace(self.path)
            return backup
        finally:
            if temp is not None:
                temp.unlink(missing_ok=True)


def inspect_table(resource, changes, textmap, merged, replacements, problems):
    rows = resource.data
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError(f"Expected an array of rows in {resource.path}")

    by_id = {}
    for row in rows:
        row_id = str(row.get("id"))
        if row_id in changes:
            if row_id in by_id:
                raise ValueError(f"Duplicate id {row_id} in {resource.path}")
            by_id[row_id] = row

    table = {
        "already_applied": 0,
        "would_change": 0,
        "kept_existing": 0,
        "missing_rows": 0,
    }
    result = {
        "fields": 0,
        "old_hash_resolvable_in_textmap": 0,
        "new_hash_resolvable_in_textmap": 0,
        "table": table,
    }
    for row_id, fields in changes.items():
        row = by_id.get(row_id)
        if row is None:
            table["missing_rows"] += 1
            problems.append(f"{resource.path.name}: missing id {row_id}")
        for field, change in fields.items():
            old, new = change["old"], change["new"]
            result["fields"] += 1
            result["old_hash_resolvable_in_textmap"] += has_text(textmap, old)
            result["new_hash_resolvable_in_textmap"] += has_text(textmap, new)
            if row is None:
                continue
            current = row.get(field)
            if current == new:
                if has_text(textmap, new):
                    table["already_applied"] += 1
                elif not has_text(merged, new):
                    problems.append(f"{resource.path.name}: {row_id}.{field} has no text for {new}")
            elif has_text(textmap, current):
                table["kept_existing"] += 1
            elif current != old:
                problems.append(
                    f"{resource.path.name}: {row_id}.{field} expected {old}, found {current!r}"
                )
            elif not has_text(merged, new):
                problems.append(f"{resource.path.name}: {row_id}.{field} has no text for {new}")
            else:
                row[field] = new
                replacements.append((f"{resource.path.name}: {row_id}.{field}", old, new))
                table["would_change"] += 1

    resource.changes = table["would_change"]
    return result


def check_other_translations(resources, replacements, problems):
    if not replacements:
        return
    for path in sorted((resources / "TextMap").glob("TextMap*.json")):
        if path.name == "TextMapCHS.json":
            continue
        texts = load_json(path)
        if not isinstance(texts, dict):
            raise ValueError(f"Expected an object in {path}")
        for field, old, new in replacements:
            if has_text(texts, old) and not has_text(texts, new):
                problems.append(f"{field} would lose its translation in {path.name}")


def plan_patch(resources):
    text = ResourceFile.load((resources / "TextMap" / "TextMapCHS.json").resolve(strict=True))
    if not isinstance(text.data, dict):
        raise ValueError(f"Expected an object in {text.path}")
    files = {
        name: ResourceFile.load(excel_path(resources, stem))
        for name, stem in TABLE_NAMES.items()
    }

    added = load_json(HERE / "textmap_added.json")
    textmap = text.data
    merged = textmap.copy()
    problems = []
    matching = conflicts = 0
    for key, value in added.items():
        if key not in textmap:
            merged[key] = value
        elif textmap[key] == value:
            matching += 1
        else:
            conflicts += 1
            problems.append(f"TextMapCHS.json: existing text differs for hash {key}")
    text.data = merged
    text.changes = len(merged) - len(textmap)

    report = {"textmap_entries": len(textmap)}
    replacements = []
    for name, resource in files.items():
        changes = load_json(HERE / f"{name}_fix.json")
        record = inspect_table(resource, changes, textmap, merged, replacements, problems)
        count_key = "entries" if name == "avatar" else "chapters"
        record[count_key] = len(changes)
        report[name] = record
    if not problems:
        check_other_translations(resources, replacements, problems)
    report["textmap_added"] = {
        "entries": len(added),
        "already_present": matching,
        "would_add": text.changes,
        "conflicts": conflicts,
    }
    # Publish text before tables can start using the new keys.
    return [text, *files.values()], report, problems


def print_problems(problems):
    for problem in problems[:10]:
        print(f"FAIL: {problem}")
    if len(problems) > 10:
        print(f"... and {len(problems) - 10} more")
