import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from tools.audit_chest_roll_paths import analyze_root, audit, count_bounds


def node(root_id, random_type, *items):
    return {"id": root_id, "randomType": random_type, "dropVec": list(items)}


def edge(item_id, weight=10000, count="1"):
    return {"itemId": item_id, "weight": weight, "countRange": count}


class ChestRollPathAuditTest(unittest.TestCase):
    def test_count_range_is_conservative(self):
        self.assertEqual((True, True), count_bounds("1"))
        self.assertEqual((True, True), count_bounds("1.4"))
        self.assertEqual((True, False), count_bounds("0.5"))
        self.assertEqual((True, False), count_bounds("0;2"))
        self.assertEqual((False, False), count_bounds("0"))
        self.assertIsNone(count_bounds("5;2"))
        self.assertIsNone(count_bounds("invalid"))
        self.assertIsNone(count_bounds("1e1"))
        self.assertIsNone(count_bounds("2147483648"))
        self.assertIsNone(count_bounds("0;2147483647"))
        self.assertIsNone(count_bounds(" 1"))

    def test_independent_mandatory_leaf_survives_empty_optional_child(self):
        graph = {
            1: node(1, 1, edge(202), edge(2, 2500)),
            2: node(2, 0),
        }
        result = analyze_root(1, graph)
        self.assertTrue(result.guaranteed_terminal)
        self.assertTrue(result.possible_terminal)
        self.assertEqual(frozenset({2}), result.empty_nodes)
        self.assertEqual(frozenset({202}), result.terminal_ids)

    def test_weighted_choice_of_empty_child_is_not_guaranteed(self):
        graph = {
            1: node(1, 0, edge(202, 7500), edge(2, 2500)),
            2: node(2, 0),
        }
        result = analyze_root(1, graph)
        self.assertTrue(result.possible_terminal)
        self.assertFalse(result.guaranteed_terminal)
        self.assertEqual(frozenset({2}), result.empty_nodes)

    def test_optional_roll_and_fractional_count_not_guaranteed(self):
        self.assertFalse(analyze_root(
            1, {1: node(1, 1, edge(202, 9999))}).guaranteed_terminal)
        self.assertFalse(analyze_root(
            1, {1: node(1, 0, edge(202, 10000, "0.5"))}).guaranteed_terminal)

    def test_invalid_optional_branch_cannot_claim_runtime_guarantee(self):
        graph = {
            1: node(1, 1, edge(202), edge(2, 5000)),
            2: node(2, 0, edge(203, 10000, "bad")),
        }
        result = analyze_root(1, graph)
        self.assertTrue(result.possible_terminal)
        self.assertFalse(result.guaranteed_terminal)
        self.assertIn(2, result.invalid_nodes)

    def test_bad_count_and_cycle_are_invalid_not_missing_items(self):
        graph = {1: node(1, 0, edge(2)),
                 2: node(2, 0, edge(1))}
        result = analyze_root(1, graph)
        self.assertFalse(result.guaranteed_terminal)
        self.assertIn(1, result.invalid_nodes)
        bad = analyze_root(1, {1: node(1, 0, edge(202, 10000, "1;bad"))})
        self.assertIn(1, bad.invalid_nodes)

    def test_scene_counts_do_not_conflate_partial_and_empty(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            server = base / "server"
            resources = base / "resources"

            def write(relative, data):
                path = base / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(data), encoding="utf-8")

            write("server/data/ChestDrop.json", [
                {"index": "测试宝箱", "dropId": 1, "minLevel": 1},
            ])
            write("resources/Server/DropTableExcelConfigData.json", [
                node(1, 1, edge(202), edge(2, 2500)),
                node(2, 0),
            ])
            write("resources/Server/DropSubTableExcelConfigData.json", [])
            write("resources/ExcelBinOutput/GadgetExcelConfigData.json", [
                {"id": 70001, "type": "Chest"},
            ])
            write("resources/ExcelBinOutput/MaterialExcelConfigData.json", [
                {"id": 202, "itemType": "ITEM_VIRTUAL"},
            ])
            for name in ("WeaponExcelConfigData.json",
                         "ReliquaryExcelConfigData.json",
                         "HomeWorldFurnitureExcelConfigData.json",
                         "TpsWeaponExcelConfigData.json"):
                write("resources/ExcelBinOutput/" + name, [])
            scene = resources / "Scripts/Scene/3/scene3_group100.lua"
            scene.parent.mkdir(parents=True)
            scene.write_text(
                'gadgets = {\n'
                '{ config_id = 1, gadget_id = 70001, drop_tag = "测试宝箱" },\n'
                '{ config_id = 2, gadget_id = 70001, chest_drop_id = 2 },\n'
                '{ config_id = 3, gadget_id = 70001 }\n'
                '}\n', encoding="utf-8")
            report = audit(server, resources)
            self.assertEqual(3, report["totals"]["ordinary_chests"])
            self.assertEqual(1, report["totals"]["chests_with_empty_descendants"])
            self.assertEqual(1, report["totals"]["empty_descendants_but_guaranteed_terminal"])
            self.assertEqual(1, report["path_status"]["guaranteed_terminal"])
            self.assertEqual(1, report["path_status"]["root_empty"])
            self.assertEqual(1, report["path_status"]["no_metadata"])
            self.assertEqual(0, report["non_boss_bridge"]["rows"])
            self.assertEqual(0, report["non_boss_bridge"]["guaranteed_terminal_rows"])
            self.assertEqual(0, report["item_validation"]["scene_chests_with_missing_item_ids"])
            self.assertEqual([], report["item_validation"]["missing_terminal_ids"])

    def test_unregistered_terminal_item_id_is_reported(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            server = base / "server"
            resources = base / "resources"

            def write(path, data):
                target = base / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(json.dumps(data), encoding="utf-8")

            write("server/data/ChestDrop.json", [
                {"index": "测试", "sourceType": 19, "minLevel": 1, "dropId": 1}
            ])
            write("resources/Server/DropTableExcelConfigData.json", [
                node(1, 1, edge(202), edge(99999999, 5000))
            ])
            write("resources/Server/DropSubTableExcelConfigData.json", [])
            write("resources/ExcelBinOutput/GadgetExcelConfigData.json", [
                {"id": 70001, "type": "Chest"}
            ])
            write("resources/ExcelBinOutput/MaterialExcelConfigData.json", [
                {"id": 202}
            ])
            for filename in ("WeaponExcelConfigData.json", "ReliquaryExcelConfigData.json",
                             "HomeWorldFurnitureExcelConfigData.json", "TpsWeaponExcelConfigData.json"):
                write("resources/ExcelBinOutput/" + filename, [])
            target = resources / "Scripts/Scene/3/scene3_group100.lua"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(
                'gadgets = { { config_id = 1, gadget_id = 70001, drop_tag = "测试" } }',
                encoding="utf-8")
            report = audit(server, resources)
            self.assertEqual([99999999], report["item_validation"]["missing_terminal_ids"])
            self.assertEqual(1, report["item_validation"]["scene_chests_with_missing_item_ids"])
            self.assertEqual(1, report["non_boss_bridge"]["rows"])
            self.assertEqual(1, report["non_boss_bridge"]["guaranteed_terminal_rows"])
            self.assertEqual([99999999], report["non_boss_bridge"]["missing_terminal_ids"])



if __name__ == "__main__":
    unittest.main()
