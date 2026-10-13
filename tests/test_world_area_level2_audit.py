import unittest

from tools.audit_world_area_level2 import audit


class WorldAreaLevel2AuditTest(unittest.TestCase):
    def test_missing_duplicates_vertical_bounds_and_parent_mismatch(self):
        config = [
            {"ID": 1101, "sceneID": 3, "areaID1": 1, "areaID2": 101,
             "areaNameTextMapHash": 123, "MBOHIOJBBHO": "LEVEL_2"},
            {"ID": 1102, "sceneID": 3, "areaID1": 1, "areaID2": 102,
             "areaNameTextMapHash": 124, "MBOHIOJBBHO": "LEVEL_2"},
            {"ID": 1103, "sceneID": 3, "areaID1": 1, "areaID2": 103,
             "areaNameTextMapHash": 125, "MBOHIOJBBHO": "LEVEL_2"},
            {"ID": 1100, "sceneID": 3, "areaID1": 1,
             "areaNameTextMapHash": 126, "MBOHIOJBBHO": "LEVEL_1"},
        ]
        polygons = {
            3: {
                "OBKNAJCIHEA": [
                    {"LKBLPFHOAFB": {"BBLLBIOLMHP": 1},
                     "OPHHFKKPLDI": [
                         {"EOIFEEDBAFJ": 101, "JLFEAGHGCLP": {}},
                         {"EOIFEEDBAFJ": 101, "JLFEAGHGCLP": {
                             "MHLOLALFHHF": {"_y": 5},
                             "NFFDCMLAAFO": {"_y": 10}}},
                         {"EOIFEEDBAFJ": 104, "JLFEAGHGCLP": {}},
                     ]},
                    {"LKBLPFHOAFB": {"BBLLBIOLMHP": 9},
                     "OPHHFKKPLDI": [
                         {"EOIFEEDBAFJ": 103, "JLFEAGHGCLP": {}},
                     ]},
                ]
            },
        }
        result = audit(config, polygons)["scenes"]["3"]
        self.assertEqual(3, result["configured_level2_ids"])
        self.assertEqual(3, result["outlined_level2_ids"])
        self.assertEqual(4, result["level2_polygons"])
        self.assertEqual(4, result["non_default_y_bounds"])
        self.assertEqual({"101": 2}, result["repeated_polygon_ids"])
        self.assertEqual([{"areaID2": 102, "areaID1": 1, "configID": 1102, "nameHash": 124}],
                         result["missing_config_ids_with_no_outline"])
        self.assertEqual([104], result["outline_ids_not_in_config"])
        self.assertEqual([{"areaID2": 103, "configAreaID1": 1, "outlineAreaID1": 9}],
                         result["parent_mismatches"])

    def test_duplicate_configuration_is_rejected(self):
        row = {"ID": 10, "sceneID": 3, "areaID1": 1, "areaID2": 101,
               "areaNameTextMapHash": 12, "MBOHIOJBBHO": "LEVEL_2"}
        with self.assertRaisesRegex(ValueError, "Duplicate level-2"):
            audit([row, row], {3: {"OBKNAJCIHEA": []}})


if __name__ == "__main__":
    unittest.main()
