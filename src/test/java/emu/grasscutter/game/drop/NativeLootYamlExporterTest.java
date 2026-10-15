package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NativeLootYamlExporterTest {
    private static final Map<Integer, String> TABLES =
            Map.of(100, "mondstadt_combat", 200, "character_experience");

    private static String itemName(int id) {
        return switch (id) {
            case 202 -> "genshin.item.mora";
            case 104003 -> "genshin.item.heros_wit";
            case 104002 -> "genshin.item.adventurers_experience";
            default -> throw new IllegalArgumentException("Unrecognized item");
        };
    }

    private static int itemId(String name) {
        return switch (name) {
            case "genshin.item.mora" -> 202;
            case "genshin.item.heros_wit" -> 104003;
            case "genshin.item.adventurers_experience" -> 104002;
            default -> throw new IllegalArgumentException("Unrecognized qualified item");
        };
    }

    @Test
    void preservesIndependentRootAndExclusiveChildPool() {
        var source = Map.of(
                100, new NativeLootYamlExporter.NativeTable(1, List.of(
                        new NativeLootYamlExporter.NativeEntry(202, "300", 10000),
                        new NativeLootYamlExporter.NativeEntry(200, "1", 7000))),
                200, new NativeLootYamlExporter.NativeTable(0, List.of(
                        new NativeLootYamlExporter.NativeEntry(104003, "1", 1000),
                        new NativeLootYamlExporter.NativeEntry(104002, "2", 1000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100), TABLES,
                NativeLootYamlExporterTest::itemName);
        assertTrue(yaml.indexOf("character_experience:") < yaml.indexOf("mondstadt_combat:"));
        assertTrue(yaml.contains("reward: genshin.item.mora"));
        assertTrue(yaml.contains("reward: *character_experience"));
        assertTrue(yaml.contains("weight: 1"));
        assertFalse(yaml.contains("weight: 1000"));
        assertFalse(yaml.contains("count: \"1\""));

        var loot = YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId);
        int none = 0, big = 0, small = 0;
        var random = new Random(42);
        for (int n = 0; n < 30_000; n++) {
            var reward = loot.roll("astaps.loot.mondstadt_combat", random);
            assertEquals(300, reward.get(202).intValue());
            if (!reward.containsKey(104003) && !reward.containsKey(104002)) none++;
            else if (reward.containsKey(104003)) {
                assertEquals(1, reward.get(104003).intValue());
                big++;
            } else {
                assertEquals(2, reward.get(104002).intValue());
                small++;
            }
        }
        assertEquals(0.30, none / 30_000.0, 0.018);
        assertEquals(0.35, big / 30_000.0, 0.018);
        assertEquals(0.35, small / 30_000.0, 0.018);
    }

    @Test
    void nativeIndependentChanceAndNativeCountAreTwoSeparateRolls() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(104003, "2;3", 5000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100), TABLES,
                NativeLootYamlExporterTest::itemName);
        assertTrue(yaml.contains("reward:\n      - {reward: genshin.item.heros_wit, count: \"2;3\"}"));
        var loot = YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId);
        int zero = 0, two = 0, three = 0;
        var random = new Random(9);
        for (int n = 0; n < 30_000; n++) {
            int count = loot.roll("astaps.loot.mondstadt_combat", random)
                    .getOrDefault(104003, 0);
            switch (count) {
                case 0 -> zero++;
                case 2 -> two++;
                case 3 -> three++;
                default -> fail("Unexpected count: " + count);
            }
        }
        assertEquals(0.5, zero / 30_000.0, 0.02);
        assertEquals(0.25, two / 30_000.0, 0.02);
        assertEquals(0.25, three / 30_000.0, 0.02);
    }

    @Test
    void exporterPreservesNativeArtifactReferences() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(55311, "1", 10000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100),
                Map.of(100, "artifact_chest"), id -> {
                    if (id == 55311) return "artifact:berserker:goblet:3:1";
                    throw new IllegalArgumentException("Unknown item");
                });
        assertTrue(yaml.contains("reward: artifact:berserker:goblet:3:1"));
        var loot = YamlLootTables.parse(yaml, name -> {
            if (name.equals("artifact:berserker:goblet:3:1")) return 55311;
            return -2;
        });
        assertEquals(Map.of(55311, 1),
                loot.roll("astaps.loot.artifact_chest", new Random(1)));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(source, List.of(100),
                        Map.of(100, "artifact_chest"), id -> "artifact:berserker:goblet:3"));
    }

    @Test
    void exporterRejectsMissingOrFakeNamesAndCyclicTables() {
        var empty = Map.of(10, new NativeLootYamlExporter.NativeTable(1, List.of()));
        String yaml = NativeLootYamlExporter.export(empty, List.of(10),
                Map.of(10, "empty_reward"), NativeLootYamlExporterTest::itemName);
        assertEquals(Map.of(), YamlLootTables.parse(yaml, NativeLootYamlExporterTest::itemId)
                .roll("astaps.loot.empty_reward", new Random(0)));

        var unknown = Map.of(10, new NativeLootYamlExporter.NativeTable(1,
                List.of(new NativeLootYamlExporter.NativeEntry(202, "1", 10000))));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(unknown, List.of(10), Map.of(),
                        NativeLootYamlExporterTest::itemName));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(unknown, List.of(10),
                        Map.of(10, "chest"), ignored -> "genshin.item.id_202"));
        var cyclic = Map.of(10, new NativeLootYamlExporter.NativeTable(0,
                List.of(new NativeLootYamlExporter.NativeEntry(10, "1", 1))));
        assertThrows(IllegalArgumentException.class, () ->
                NativeLootYamlExporter.export(cyclic, List.of(10),
                        Map.of(10, "cycle"), NativeLootYamlExporterTest::itemName));
    }
}
