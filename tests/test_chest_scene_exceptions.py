import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from tools.audit_chest_scene_exceptions import (
    build_report, collect, gadget_entries, row_status,
)


class ChestSceneExceptionsTest(unittest.TestCase):
    def test_brace_parser_ignores_comments_and_nested_tables(self):
        script = '''
        gadgets = {
          -- { config_id = 999, gadget_id = 70211111 }
          { config_id = 1, gadget_id = 70211111,
            pos = { x = 1, y = 2, z = 3 },
            name = "not { an entry }", chest_drop_id = 1000100 },
          { config_id = 2, gadget_id = 70211111,
            pos = { x = 3, y = 2, z = 1 }, chest_drop_id = 1050256 }
        }
        suites = { { gadgets = { 1, 2 } } }
        '''
        entries = list(gadget_entries(script))
        self.assertEqual(2, len(entries))
        self.assertIn("chest_drop_id = 1000100", entries[0])
        self.assertIn("chest_drop_id = 1050256", entries[1])

    def test_empty_sentinel_is_distinct_from_missing(self):
        self.assertEqual("missing-root", row_status(None))
        self.assertEqual("empty-source-type-0",
                         row_status({"sourceType": 0, "dropVec": []}))
        self.assertEqual("empty-other",
                         row_status({"sourceType": 19, "dropVec": []}))
        self.assertEqual("no-positive-entries",
                         row_status({"dropVec": [{"itemId": 202, "weight": 0}]}))
        self.assertEqual("populated",
                         row_status({"dropVec": [{"itemId": 202, "weight": 10000}]}))

    def test_collects_exact_chest_failures_without_fabricating_rewards(self):
        with TemporaryDirectory() as tmp:
            resources = Path(tmp)
            def save(path, obj):
                path = resources / path
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(obj), encoding="utf-8")

            save("ExcelBinOutput/GadgetExcelConfigData.json", [
                {"id": 70211111, "type": "Chest"},
                {"id": 21010101, "type": "Monster"},
            ])
            save("Server/DropTableExcelConfigData.json", [
                {"id": 1000100, "sourceType": 0, "dropVec": []},
                {"id": 1050235, "sourceType": 14, "dropVec": [
                    {"itemId": 101969, "weight": 10000, "countRange": "1"}
                ]},
            ])
            save("Server/DropSubTableExcelConfigData.json", [])
            scene_path = resources / "Scripts/Scene/3/scene3_group123.lua"
            scene_path.parent.mkdir(parents=True, exist_ok=True)
            scene_path.write_text('''
            gadgets = {
              { config_id = 1, gadget_id = 70211111,
                pos = { x = 0 }, chest_drop_id = 1000100,
                isOneoff = true, persistent = true },
              { config_id = 2, gadget_id = 70211111,
                chest_drop_id = 1050256, drop_count = 1,
                autopick = true, isOneoff = true, persistent = true },
              { config_id = 3, gadget_id = 70211111,
                chest_drop_id = 1050235, drop_count = 1 },
              { config_id = 4, gadget_id = 70211111,
                drop_tag = "解谜中级须弥" },
              { config_id = 5, gadget_id = 70211111,
                boss_chest = { monster_config_id = 10 },
                chest_drop_id = 1050256 },
              { config_id = 6, gadget_id = 21010101,
                chest_drop_id = 1050256 }
            }
            ''', encoding="utf-8")
            records, totals = collect(resources)
            report = build_report(records, totals)
            self.assertEqual(4, totals["ordinary_chests"])
            self.assertEqual(3, totals["explicit_id_chests"])
            self.assertEqual(2, report["totals"]["unresolved_explicit_chests"])
            self.assertEqual(
                1, report["summary"]["empty-source-type-0"]["count"])
            self.assertEqual(
                1, report["summary"]["missing-root"]["count"])
            self.assertEqual([1, 2], [r["config_id"] for r in records])
            self.assertEqual(1, report["summary"]["missing-root"]["autopick"])


if __name__ == "__main__":
    unittest.main()
