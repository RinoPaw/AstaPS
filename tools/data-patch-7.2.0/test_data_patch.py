import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import patch

import apply_data_patch
import data_patch

HERE = Path(__file__).resolve().parent
EXPECTED = HERE / "expected-report.json"


class DataPatchTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.avatar = self.root / "ExcelBinOutput" / "AvatarExcelConfigData.json"
        self.chapter = self.root / "ExcelBinOutput" / "ChapterExcelConfigData.json"
        self.text = self.root / "TextMap" / "TextMapCHS.json"
        self.avatar_fix = data_patch.load_json(HERE / "avatar_fix.json")
        self.chapter_fix = data_patch.load_json(HERE / "chapter_fix.json")
        self.added = data_patch.load_json(HERE / "textmap_added.json")
        textmap = {}
        for file, fixes in ((self.avatar, self.avatar_fix), (self.chapter, self.chapter_fix)):
            rows = []
            for row_id, fields in fixes.items():
                rows.append({"id": int(row_id), **{name: change["old"] for name, change in fields.items()}})
                for change in fields.values():
                    key = str(change["new"])
                    if key not in self.added:
                        textmap[key] = f"fixture text for {key}"
            self.write_json(file, rows)
        self.write_json(self.text, textmap)

    @staticmethod
    def write_json(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def run_tool(self, script, *args):
        return subprocess.run(
            [sys.executable, "-B", str(HERE / script), str(self.root), *map(str, args)],
            check=False, capture_output=True, text=True, encoding="utf-8",
        )

    def snapshot(self):
        return {p.relative_to(self.root): (p.read_bytes(), p.stat().st_mtime_ns)
                for p in self.root.rglob("*") if p.is_file()}

    def test_preview_apply_verify_and_repeat(self):
        initial = self.snapshot()
        preview = self.run_tool("apply_data_patch.py", "--dry")
        self.assertEqual(preview.returncode, 0, preview.stderr)
        self.assertEqual(json.loads(preview.stdout)["avatar"]["table"]["would_change"], 154)
        self.assertEqual(self.snapshot(), initial)
        self.assertNotEqual(self.run_tool("validate_data_patch.py", "--expect", EXPECTED).returncode, 0)

        apply = self.run_tool("apply_data_patch.py")
        self.assertEqual(apply.returncode, 0, apply.stdout + apply.stderr)
        verify = self.run_tool("validate_data_patch.py", "--expect", EXPECTED)
        self.assertEqual(verify.returncode, 0, verify.stdout + verify.stderr)
        report = json.loads(verify.stdout)
        self.assertEqual(report["chapter"]["table"]["already_applied"], 92)
        self.assertEqual(report["textmap_added"]["already_present"], 22)

        for file in (self.avatar, self.chapter, self.text):
            backups = list(file.parent.glob(f"{file.name}.bak_*"))
            self.assertEqual(len(backups), 1)
            self.assertEqual(backups[0].read_bytes(), initial[file.relative_to(self.root)][0])
        applied = self.snapshot()
        repeat = self.run_tool("apply_data_patch.py")
        self.assertEqual(repeat.returncode, 0, repeat.stderr)
        self.assertEqual(json.loads(repeat.stdout)["avatar"]["table"]["would_change"], 0)
        self.assertEqual(self.snapshot(), applied)

    def test_keep_already_working_names_on_another_resource_variant(self):
        rows = data_patch.load_json(self.avatar)
        rows[0]["nameTextMapHash"] = 987654321
        self.write_json(self.avatar, rows)
        textmap = data_patch.load_json(self.text)
        for row in rows[:2]:
            textmap[str(row["nameTextMapHash"])] = "already working"
        self.write_json(self.text, textmap)

        apply = self.run_tool("apply_data_patch.py")
        self.assertEqual(apply.returncode, 0, apply.stdout + apply.stderr)
        patched = data_patch.load_json(self.avatar)
        self.assertEqual(patched[:2], rows[:2])
        self.assertNotEqual(patched[2], rows[2])
        self.assertNotEqual(self.run_tool("validate_data_patch.py", "--expect", EXPECTED).returncode, 0)

    def test_empty_target_text_blocks_all_writes(self):
        key = str(self.avatar_fix["10000002"]["nameTextMapHash"]["new"])
        textmap = data_patch.load_json(self.text)
        textmap[key] = "  "
        self.write_json(self.text, textmap)
        before = self.snapshot()

        apply = self.run_tool("apply_data_patch.py")
        self.assertNotEqual(apply.returncode, 0)
        self.assertIn("has no text for " + key, apply.stdout)
        self.assertEqual(self.snapshot(), before)

    def test_preserves_working_translations_in_other_installed_languages(self):
        english = self.text.with_name("TextMapEN.json")
        hashes = self.avatar_fix["10000002"]["nameTextMapHash"]
        self.write_json(english, {str(hashes["old"]): "Ayaka"})
        before = self.snapshot()

        apply = self.run_tool("apply_data_patch.py")
        self.assertNotEqual(apply.returncode, 0)
        self.assertIn("would lose its translation in TextMapEN.json", apply.stdout)
        self.assertEqual(self.snapshot(), before)

        self.write_json(english, {str(hashes["new"]): "Ayaka"})
        apply = self.run_tool("apply_data_patch.py")
        self.assertEqual(apply.returncode, 0, apply.stdout + apply.stderr)

    def test_unrecognized_hash_and_missing_table_fail_before_writes(self):
        rows = data_patch.load_json(self.avatar)
        rows[0]["nameTextMapHash"] = 987654321
        self.write_json(self.avatar, rows)
        before = self.snapshot()
        self.assertNotEqual(self.run_tool("apply_data_patch.py").returncode, 0)
        self.assertEqual(self.snapshot(), before)

        self.chapter.unlink()
        before = self.snapshot()
        self.assertNotEqual(self.run_tool("apply_data_patch.py").returncode, 0)
        self.assertEqual(self.snapshot(), before)

    def test_validator_rejects_wrong_text_missing_tables_and_missing_expect_file(self):
        apply = self.run_tool("apply_data_patch.py")
        self.assertEqual(apply.returncode, 0, apply.stdout + apply.stderr)
        self.assertNotEqual(
            self.run_tool("validate_data_patch.py", "--expect", self.root / "missing.json").returncode,
            0,
        )
        textmap = data_patch.load_json(self.text)
        textmap[next(iter(self.added))] = "different text at an existing key"
        self.write_json(self.text, textmap)
        before = self.snapshot()
        self.assertNotEqual(self.run_tool("validate_data_patch.py", "--expect", EXPECTED).returncode, 0)
        self.assertNotEqual(self.run_tool("apply_data_patch.py").returncode, 0)
        self.assertEqual(self.snapshot(), before)

        textmap.update(self.added)
        self.write_json(self.text, textmap)
        self.avatar.unlink()
        self.chapter.unlink()
        result = self.run_tool("validate_data_patch.py", "--expect", EXPECTED)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing active table", result.stderr)

    def test_uses_the_server_override_and_refuses_active_non_json(self):
        override = self.root / "Server" / self.avatar.name
        self.write_json(override, data_patch.load_json(self.avatar))
        raw_before = self.avatar.read_bytes()
        non_json = override.with_suffix(".tsj")
        non_json.write_text("fixture", encoding="utf-8")
        before = self.snapshot()
        rejected = self.run_tool("apply_data_patch.py")
        self.assertNotEqual(rejected.returncode, 0)
        self.assertIn("is not JSON", rejected.stderr)
        self.assertEqual(self.snapshot(), before)

        non_json.unlink()
        apply = self.run_tool("apply_data_patch.py")
        self.assertEqual(apply.returncode, 0, apply.stdout + apply.stderr)
        self.assertNotEqual(override.read_bytes(), raw_before)
        self.assertEqual(self.avatar.read_bytes(), raw_before)
        self.assertEqual(self.run_tool("validate_data_patch.py", "--expect", EXPECTED).returncode, 0)

    def test_backups_stay_unique_in_the_same_second(self):
        initial = self.avatar.read_bytes()
        fixed = datetime(2026, 11, 19, 12, 34, 56)
        with patch.object(data_patch, "datetime") as clock:
            clock.now.return_value = fixed
            self.assertEqual(apply_data_patch.main([str(self.root)]), 0)
            changed = self.avatar.read_bytes()
            self.assertNotEqual(changed, initial)
            backups = list(self.avatar.parent.glob(f"{self.avatar.name}.bak_*"))
            self.assertEqual(len(backups), 1)
            first = backups[0]
            self.avatar.write_bytes(initial)
            self.assertEqual(apply_data_patch.main([str(self.root)]), 0)
        self.assertEqual(self.avatar.read_bytes(), changed)
        self.assertEqual(first.read_bytes(), initial)
        self.assertEqual(len(list(self.avatar.parent.glob(f"{self.avatar.name}.bak_*"))), 2)

    def test_failed_replace_keeps_original_table_and_retry_completes(self):
        original_avatar = self.avatar.read_bytes()
        original_chapter = self.chapter.read_bytes()
        real_replace = Path.replace

        def fail_avatar(source, target):
            if target == self.avatar:
                raise OSError("fixture write failure")
            return real_replace(source, target)

        with patch.object(Path, "replace", fail_avatar):
            self.assertNotEqual(apply_data_patch.main([str(self.root)]), 0)
        self.assertEqual(self.avatar.read_bytes(), original_avatar)
        self.assertEqual(self.chapter.read_bytes(), original_chapter)
        self.assertTrue(all(data_patch.load_json(self.text)[k] == v for k, v in self.added.items()))
        retry = self.run_tool("apply_data_patch.py")
        self.assertEqual(retry.returncode, 0, retry.stdout + retry.stderr)
        self.assertEqual(self.run_tool("validate_data_patch.py", "--expect", EXPECTED).returncode, 0)


if __name__ == "__main__":
    unittest.main()
