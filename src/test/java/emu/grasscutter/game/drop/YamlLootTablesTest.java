package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class YamlLootTablesTest {
    private final YamlLootTables.ItemResolver resolver = name -> switch (name) {
        case "Genshin.Mora", "202" -> 202;
        case "Genshin.HerosWit", "104003" -> 104003;
        case "Genshin.AdventurersExperience", "104002" -> 104002;
        default -> -1;
    };

    private YamlLootTables parse(String yaml) {
        return YamlLootTables.parse(yaml, resolver);
    }

    @Test
    void fixedItemsAndOmittedCount() {
        var tables = parse("""
                common_chest:
                  - {item: Genshin.Mora, count: 300}
                  - {item: Genshin.HerosWit}
                """);
        assertEquals(Map.of(202, 300, 104003, 1), tables.roll("common_chest", new Random(1)));
    }

    @Test
    void quotedCountSupportsLegacyAndReadability() {
        var tables = parse("""
                chest:
                  - {item: 202, count: "2~4"}
                  - {item: Genshin.HerosWit, count: "1:80"}
                  - {item: Genshin.AdventurersExperience, count: "2.2"}
                """);
        for (int i = 0; i < 300; i++) {
            var rewards = tables.roll("chest", new Random(i));
            assertTrue(rewards.get(202) >= 2 && rewards.get(202) <= 4);
            assertTrue(rewards.getOrDefault(104003, 0) <= 1);
            assertTrue(rewards.get(104002) == 2 || rewards.get(104002) == 3);
        }
    }

    @Test
    void mutuallyExclusiveWeightedChoiceAndAlias() {
        var tables = parse("""
                experience: &exp
                  - {item: Genshin.HerosWit, weight: 1}
                  - {item: Genshin.AdventurersExperience, count: 2, weight: 3}
                chest:
                  - {item: 202, count: 300}
                  - {item: *exp, count: "0.7"}
                """);
        int none = 0, big = 0, small = 0;
        Random random = new Random(42);
        for (int i = 0; i < 40_000; i++) {
            var r = tables.roll("chest", random);
            assertEquals(300, r.get(202).intValue());
            if (!r.containsKey(104003) && !r.containsKey(104002)) none++;
            else if (r.containsKey(104003)) {
                assertEquals(1, r.get(104003).intValue());
                assertFalse(r.containsKey(104002));
                big++;
            } else {
                assertEquals(2, r.get(104002).intValue());
                small++;
            }
        }
        assertEquals(0.30, none / 40_000.0, 0.018);
        assertEquals(0.175, big / 40_000.0, 0.018);
        assertEquals(0.525, small / 40_000.0, 0.018);
    }

    @Test
    void nestedCountRepeatsIndependentRolls() {
        var tables = parse("""
                experience: &exp
                  - {item: Genshin.HerosWit, weight: 1}
                  - {item: Genshin.AdventurersExperience, weight: 1}
                chest:
                  - {item: *exp, count: 2}
                """);
        int mixed = 0;
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            var r = tables.roll("chest", random);
            assertEquals(2, r.values().stream().mapToInt(Integer::intValue).sum());
            if (r.containsKey(104003) && r.containsKey(104002)) mixed++;
        }
        assertTrue(mixed > 800 && mixed < 1200, "Must reroll the nested pool twice");
    }

    @Test
    void explicitEmptyChoiceAndNullItem() {
        var tables = parse("""
                chance:
                  - item:
                      - {item: Genshin.HerosWit, weight: 5}
                      - {item: Genshin.AdventurersExperience, weight: 3}
                    count: "1:80"
                empty:
                  - {item: null}
                  - {item: []}
                weighted_empty:
                  - {item: null, weight: 1}
                  - {item: Genshin.Mora, weight: 1}
                """);
        assertEquals(Map.of(), tables.roll("empty", new Random(0)));
        for (int i = 0; i < 100; i++) {
            var rewards = tables.roll("weighted_empty", new Random(i));
            assertTrue(rewards.isEmpty() || rewards.equals(Map.of(202, 1)));
        }
    }

    @Test
    void numericAndNullWeightsCannotMix() {
        var error = assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {item: Genshin.Mora, weight: 0}
                  - {item: Genshin.HerosWit}
                """));
        assertTrue(error.getMessage().contains("cannot mix"));
    }

    @Test
    void explicitlyNumericZeroWeightIsAllowedWhenAnotherIsPositive() {
        var tables = parse("""
                pool:
                  - {item: Genshin.Mora, weight: 0}
                  - {item: Genshin.HerosWit, weight: 1}
                """);
        assertEquals(Map.of(104003, 1), tables.roll("pool", new Random(0)));
    }

    @Test
    void failsClosedOnBrokenContent() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {item: missing_item}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {item: Genshin.Mora, count: "1:60,2:60"}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {item: Genshin.Mora, unknown: true}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {item: Genshin.Mora, weight: 0}
                """));
        assertThrows(RuntimeException.class, () -> parse("""
                recursive: &self
                  - {item: *self}
                """));
    }

    @Test
    void missingCountProbabilityIsFilledAndCanBeZero() {
        var distribution = LootCount.parse("0:40,1,2:18");
        int[] outcome = new int[3];
        var rng = new Random(9876);
        for (int i = 0; i < 50_000; i++) outcome[distribution.sample(rng)]++;
        assertEquals(0.40, outcome[0] / 50_000.0, 0.015);
        assertEquals(0.42, outcome[1] / 50_000.0, 0.015);
        assertEquals(0.18, outcome[2] / 50_000.0, 0.015);

        var nullable = parse("""
                pool:
                  - {item: null, weight: 2}
                  - {item: Genshin.Mora, weight: 3}
                """);
        int empty = 0;
        rng = new Random(123);
        for (int i = 0; i < 5_000; i++) if (nullable.roll("pool", rng).isEmpty()) empty++;
        assertEquals(0.4, empty / 5000.0, 0.04);
    }

    @Test
    void itemClassificationUsesGameResourceTypes() {
        assertEquals("Weapons", LootItemNames.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_WEAPON,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_NONE));
        assertEquals("Artifacts", LootItemNames.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_RELIQUARY,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_NONE));
        assertEquals("Character EXP Materials", LootItemNames.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_EXP_FRUIT));
        assertEquals("Food", LootItemNames.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_FOOD));
        assertEquals(104003, new LootItemNames(java.util.Collections.singletonMap(104003, null)).resolve("Genshin.HerosWit"));
    }

    @Test
    void itemNamesKeepEnglishNamesAndDetectAmbiguity() {
        var names = new LootItemNames(Map.of(
                104003, "Hero's Wit",
                104002, "Adventurer's Experience",
                202, "Mora"));
        assertEquals("Genshin.HerosWit", names.describe(104003).key());
        assertEquals("Character EXP Materials", names.describe(104003).category());
        assertEquals(104003, names.resolve("Hero's Wit"));
        assertEquals(104003, names.resolve("Genshin.HerosWit"));
        assertEquals(202, names.resolve("202"));
        var colliding = new LootItemNames(Map.of(1, "Same", 2, "Same"));
        assertThrows(IllegalArgumentException.class, () -> colliding.resolve("Genshin.Same"));
    }
}
