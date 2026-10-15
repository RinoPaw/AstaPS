package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NativeLootYamlExporterTest {
    @Test
    void preservesIndependentRootAndExclusiveChildPool() {
        var source = Map.of(
                100, new NativeLootYamlExporter.NativeTable(1, List.of(
                        new NativeLootYamlExporter.NativeEntry(202, "300", 10000),
                        new NativeLootYamlExporter.NativeEntry(200, "1", 7000))),
                200, new NativeLootYamlExporter.NativeTable(0, List.of(
                        new NativeLootYamlExporter.NativeEntry(104003, "1", 1),
                        new NativeLootYamlExporter.NativeEntry(104002, "2", 1))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100));
        assertTrue(yaml.indexOf("native_200:") < yaml.indexOf("native_100:"),
                "Anchors must be defined before being referenced");
        var loot = YamlLootTables.parse(yaml, key -> Integer.parseInt(key));
        int none = 0, big = 0, small = 0;
        var random = new Random(42);
        for (int n = 0; n < 30000; n++) {
            var reward = loot.roll("astaps.loot.native_100", random);
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
        assertEquals(0.30, none / 30000.0, 0.018);
        assertEquals(0.35, big / 30000.0, 0.018);
        assertEquals(0.35, small / 30000.0, 0.018);
    }

    @Test
    void nativeIndependentChanceAndNativeCountAreTwoSeparateRolls() {
        var source = Map.of(100, new NativeLootYamlExporter.NativeTable(1, List.of(
                new NativeLootYamlExporter.NativeEntry(104003, "2;3", 5000))));
        var yaml = NativeLootYamlExporter.export(source, List.of(100));
        assertTrue(yaml.contains("reward:\n      - {reward: 104003, count: \"2;3\"}"));
        var loot = YamlLootTables.parse(yaml, Integer::parseInt);
        int zero = 0, two = 0, three = 0;
        var random = new Random(9);
        for (int n = 0; n < 30000; n++) {
            int count = loot.roll("astaps.loot.native_100", random).getOrDefault(104003, 0);
            switch (count) {
                case 0 -> zero++;
                case 2 -> two++;
                case 3 -> three++;
                default -> fail("Unexpected count: " + count);
            }
        }
        assertEquals(0.5, zero / 30000.0, 0.02);
        assertEquals(0.25, two / 30000.0, 0.02);
        assertEquals(0.25, three / 30000.0, 0.02);
    }

    @Test
    void nativeEmptyTableAndCyclicGraph() {
        var empty = Map.of(10, new NativeLootYamlExporter.NativeTable(1, List.of()));
        assertEquals(Map.of(), YamlLootTables.parse(
                NativeLootYamlExporter.export(empty, List.of(10)), Integer::parseInt)
                .roll("astaps.loot.native_10", new Random(0)));
        var cyclic = Map.of(10, new NativeLootYamlExporter.NativeTable(0,
                List.of(new NativeLootYamlExporter.NativeEntry(10, "1", 1))));
        assertThrows(IllegalArgumentException.class,
                () -> NativeLootYamlExporter.export(cyclic, List.of(10)));
    }
}
