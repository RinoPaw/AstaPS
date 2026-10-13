import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from tools.audit_chest_data_alignment import assess, legacy_tag, mora_candidate_tag


class ChestDataAlignmentTest(unittest.TestCase):
    def test_mora_is_distinct_from_puzzle_tiers(self):
        point = {"id": 40001, "kind": "chest_mora"}
        self.assertEqual("解谜低级须弥", legacy_tag(point))
        self.assertEqual("摩拉石箱须弥", mora_candidate_tag(point))
        self.assertEqual(
            "解谜超级至冬",
            legacy_tag({"id": 10001, "kind": "chest_remarkable"}),
        )

    def test_counts_existing_tags_missing_roots_and_double_ownership(self):
        with TemporaryDirectory() as d:
            server = Path(d)
            resources = server / "resources"
            def save(path, payload):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

            save(server / "data/ChestDrop.json", [
                {"index": "摩拉石箱须弥", "dropId": 8100300},
                {"index": "解谜低级须弥", "dropId": 52040000},
                {"index": "摩拉石箱枫丹", "dropId": 8100400},
                {"index": "missing boss", "dropId": 999999},
            ])
            save(resources / "Server/DropTableExcelConfigData.json",
                 [{"id": 8100300}, {"id": 52040000}, {"id": 8100400}])
            save(resources / "Server/DropSubTableExcelConfigData.json", [])
            save(server / "data/nodkrai_explore_points.json",
                 [{"id": 1, "kind": "chest_common", "x": 1500, "z": 9900}])
            save(server / "data/snezhnaya_explore_points.json",
                 [{"id": 10001, "kind": "chest_remarkable", "x": 8000, "z": 7000}])
            save(server / "data/region_explore_points.json", [
                {"id": 40001, "kind": "chest_mora", "x": 0, "z": 5900},
                {"id": 20001, "kind": "chest_mora", "x": 2800, "z": 3900},
            ])
            world = server / "src/main/java/emu/grasscutter/game/world"
            world.mkdir(parents=True)
            (world / "NodKraiExploreSpawnHelper.java").write_text(
                "loadPointsFile(SNEZHNAYA_POINTS_FILE, list)", encoding="utf-8"
            )
            (world / "SnezhnayaExploreSpawnHelper.java").write_text(
                'POINTS_FILE = Path.of("data", "snezhnaya_explore_points.json")',
                encoding="utf-8"
            )
            (world / "Scene.java").write_text(
                "NodKraiExploreSpawnHelper.ensureNearby(this);\n"
                "SnezhnayaExploreSpawnHelper.ensureNearby(this);",
                encoding="utf-8"
            )
            report = assess(server, resources)
            drops = report["chest_drop"]
            self.assertEqual(4, drops["rows"])
            self.assertEqual(1, drops["missing_root_rows"])
            self.assertEqual({"missing boss": 1}, drops["missing_root_tags"])
            synth = report["synthetic"]
            self.assertEqual(4, synth["chests"])
            self.assertEqual(1, synth["legacy_tag_found"])
            self.assertEqual(2, synth["candidate_tag_found"])
            self.assertEqual(2, synth["mora_candidate_count"])
            self.assertEqual(1, synth["newly_found_by_mora_candidate"])
            self.assertEqual(1, synth["remarkable_mapped_as_luxurious"])
            self.assertEqual(2, synth["unresolved_after_candidate"])
            owner = report["ownership"]
            self.assertTrue(owner["snezhnaya_loaded_by_two_spawners"])
            self.assertEqual(1, owner["snezhnaya_chests_at_risk_of_double_spawn"])


if __name__ == "__main__":
    unittest.main()
