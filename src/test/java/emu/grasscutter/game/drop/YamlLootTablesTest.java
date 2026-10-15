package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.ItemIdRegistry;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class YamlLootTablesTest {
    private final YamlLootTables.ItemResolver resolver = name -> switch (name) {
        case "genshin.item.mora", "202" -> 202;
        case "genshin.item.heros_wit", "104003" -> 104003;
        case "genshin.item.adventurers_experience", "104002" -> 104002;
        default -> -2; // -1 is reserved for the regional-sigil placeholder.
    };

    private YamlLootTables parse(String yaml) {
        return YamlLootTables.parse(yaml, resolver);
    }

    @Test
    void yamlShortKeysBecomeGlobalLootIds() {
        var tables = parse("""
                surprise:
                  - {reward: genshin.item.mora, count: 300}
                经验书:
                  - {reward: genshin.item.heros_wit}
                """);
        assertEquals(java.util.List.of("astaps.loot.surprise", "astaps.loot.经验书"),
                tables.names());
        assertEquals("astaps.loot.surprise", tables.resolveName("surprise"));
        assertEquals("astaps.loot.surprise",
                tables.resolveName("astaps.loot.surprise"));
        assertEquals("astaps.loot.经验书", tables.resolveName("经验书"));
        assertEquals(Map.of(202, 300),
                tables.roll("astaps.loot.surprise", new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> tables.resolveName("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> tables.resolveName("genshin.item.mora"));
        assertThrows(IllegalArgumentException.class,
                () -> tables.roll("surprise", new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                astaps.loot.surprise:
                  - {reward: genshin.item.mora}
                """));
    }

    @Test
    void artifactPropertiesChooseNativeIdsIndependentlyPerAward() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        for (int slot = 1; slot <= 5; slot++) {
            for (int substats = 1; substats <= 2; substats++) {
                variants.add(new ArtifactResolver.Variant(55300 + slot * 10 + substats,
                        "berserker", slot, 3, substats));
            }
        }
        var artifactResolver = new ArtifactResolver(variants);
        var loot = YamlLootTables.parse("""
                chest:
                  - reward: genshin.artifact.berserker
                    properties: {slot: "1~5", rarity: 3, substats: "1:80,2:20"}
                    count: 2
                """, resolver, artifactResolver);
        int one = 0, two = 0;
        var random = new Random(101);
        for (int i = 0; i < 5000; i++) {
            var rewards = loot.roll("astaps.loot.chest", random);
            assertEquals(2, rewards.values().stream().mapToInt(Integer::intValue).sum());
            for (var entry : rewards.entrySet()) {
                int slot = (entry.getKey() / 10) % 10;
                int substats = entry.getKey() % 10;
                assertTrue(slot >= 1 && slot <= 5);
                if (substats == 1) one += entry.getValue();
                else if (substats == 2) two += entry.getValue();
                else fail("Unexpected native artifact variant");
            }
        }
        assertEquals(10_000, one + two);
        assertEquals(0.80, one / 10_000.0, 0.025);
        assertEquals(0.20, two / 10_000.0, 0.025);
    }

    @Test
    void missingArtifactPropertiesSelectOnlyAvailableNativeVariants() {
        var artifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(60140, "adventurer", 1, 1, 0),
                new ArtifactResolver.Variant(60110, "adventurer", 4, 1, 0)));
        var loot = YamlLootTables.parse("""
                chest:
                  - reward: genshin.artifact.adventurer
                    properties: {rarity: 1}
                """, resolver, artifacts);
        for (int i = 0; i < 100; i++) {
            var reward = loot.roll("astaps.loot.chest", new Random(i));
            // The native test inventory has two slots; only their IDs can appear.
            // An explicitly selected unavailable slot fails instead of inventing an ID.
            assertTrue(reward.containsKey(60140) || reward.containsKey(60110));
        }
        assertThrows(IllegalArgumentException.class, () ->
                YamlLootTables.parse("""
                        bad:
                          - reward: genshin.artifact.adventurer
                            properties: {invalid: 1}
                        """, resolver, artifacts));
        assertThrows(IllegalArgumentException.class, () ->
                YamlLootTables.parse("""
                        bad:
                          - reward: genshin.item.mora
                            properties: {rarity: 1}
                        """, resolver, artifacts));
    }

    @Test
    void aDifferentLoaderNamespaceStillUsesShortYamlNames() {
        var other = YamlLootTables.parse("""
                surprise:
                  - {reward: genshin.item.mora, count: 7}
                """, "my_mod", resolver);
        assertEquals(java.util.List.of("my_mod.loot.surprise"), other.names());
        assertEquals("my_mod.loot.surprise",
                other.resolveName("my_mod.loot.surprise"));
        assertEquals(Map.of(202, 7),
                other.roll("my_mod.loot.surprise", new Random(0)));
        assertEquals("my_mod.loot.surprise", other.resolveName("surprise"));
    }

    @Test
    void fixedItemsAndOmittedCount() {
        var tables = parse("""
                common_chest:
                  - {reward: genshin.item.mora, count: 300}
                  - {reward: genshin.item.heros_wit}
                """);
        assertEquals(Map.of(202, 300, 104003, 1), tables.roll("astaps.loot.common_chest", new Random(1)));
    }

    @Test
    void parsedPoolCanProduceAstapsItemParamData() {
        var tables = parse("""
                basic:
                  - {reward: genshin.item.mora, count: 300}
                  - {reward: genshin.item.heros_wit}
                """);
        assertTrue(tables.contains("astaps.loot.basic"));
        assertFalse(tables.contains("astaps.loot.unknown"));
        var reward = tables.rollItems("astaps.loot.basic", new Random(1));
        assertEquals(2, reward.size());
        assertEquals(202, reward.get(0).getId());
        assertEquals(300, reward.get(0).getCount());
        assertEquals(104003, reward.get(1).getId());
        assertEquals(1, reward.get(1).getCount());
    }

    @Test
    void quotedCountSupportsRangesAndPercentages() {
        var tables = parse("""
                chest:
                  - {reward: 202, count: "2~4"}
                  - {reward: genshin.item.heros_wit, count: "1:80"}
                  - {reward: genshin.item.adventurers_experience, count: "2:80,3:20"}
                """);
        for (int i = 0; i < 300; i++) {
            var rewards = tables.roll("astaps.loot.chest", new Random(i));
            assertTrue(rewards.get(202) >= 2 && rewards.get(202) <= 4);
            assertTrue(rewards.getOrDefault(104003, 0) <= 1);
            assertTrue(rewards.get(104002) == 2 || rewards.get(104002) == 3);
        }
    }

    @Test
    void decimalCountsAndWeightsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("0.7"));
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2.2"));
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("1:12.5,2:87.5"));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                bad:
                  - {reward: genshin.item.mora, weight: 1.5}
                """));
    }

    @Test
    void mutuallyExclusiveWeightedChoiceAndAlias() {
        var tables = parse("""
                experience: &exp
                  - {reward: genshin.item.heros_wit, weight: 1}
                  - {reward: genshin.item.adventurers_experience, count: 2, weight: 3}
                chest:
                  - {reward: 202, count: 300}
                  - {reward: *exp, count: "1:70"}
                """);
        int none = 0, big = 0, small = 0;
        Random random = new Random(42);
        for (int i = 0; i < 40_000; i++) {
            var r = tables.roll("astaps.loot.chest", random);
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
                  - {reward: genshin.item.heros_wit, weight: 1}
                  - {reward: genshin.item.adventurers_experience, weight: 1}
                chest:
                  - {reward: *exp, count: 2}
                """);
        int mixed = 0;
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            var r = tables.roll("astaps.loot.chest", random);
            assertEquals(2, r.values().stream().mapToInt(Integer::intValue).sum());
            if (r.containsKey(104003) && r.containsKey(104002)) mixed++;
        }
        assertTrue(mixed > 800 && mixed < 1200, "Must reroll the nested pool twice");
    }

    @Test
    void explicitEmptyChoiceAndNullItem() {
        var tables = parse("""
                chance:
                  - reward:
                      - {reward: genshin.item.heros_wit, weight: 5}
                      - {reward: genshin.item.adventurers_experience, weight: 3}
                    count: "1:80"
                empty:
                  - {reward: null}
                  - {reward: []}
                weighted_empty:
                  - {reward: null, weight: 1}
                  - {reward: genshin.item.mora, weight: 1}
                """);
        assertEquals(Map.of(), tables.roll("astaps.loot.empty", new Random(0)));
        for (int i = 0; i < 100; i++) {
            var rewards = tables.roll("astaps.loot.weighted_empty", new Random(i));
            assertTrue(rewards.isEmpty() || rewards.equals(Map.of(202, 1)));
        }
    }

    @Test
    void numericAndNullWeightsCannotMix() {
        var error = assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.item.mora, weight: 0}
                  - {reward: genshin.item.heros_wit}
                """));
        assertTrue(error.getMessage().contains("cannot mix"));
    }

    @Test
    void explicitlyNumericZeroWeightIsAllowedWhenAnotherIsPositive() {
        var tables = parse("""
                pool:
                  - {reward: genshin.item.mora, weight: 0}
                  - {reward: genshin.item.heros_wit, weight: 1}
                """);
        assertEquals(Map.of(104003, 1), tables.roll("astaps.loot.pool", new Random(0)));
    }

    @Test
    void failsClosedOnBrokenContent() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: missing_item}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.item.mora, count: "1:60,2:60"}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.item.mora, unknown: true}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.item.mora, weight: 0}
                """));
        assertThrows(RuntimeException.class, () -> parse("""
                recursive: &self
                  - {reward: *self}
                """));
    }

    @Test
    void aSingleSpecifiedPercentageImpliesZeroForTheRemainder() {
        var distribution = LootCount.parse("1:20");
        int[] outcomes = new int[2];
        var random = new Random(2026);
        for (int i = 0; i < 20_000; i++) {
            int count = distribution.sample(random);
            assertTrue(count == 0 || count == 1);
            outcomes[count]++;
        }
        assertEquals(0.80, outcomes[0] / 20_000.0, 0.015);
        assertEquals(0.20, outcomes[1] / 20_000.0, 0.015);

        var table = parse("""
                chance:
                  - {reward: genshin.item.mora, count: "1:20"}
                """);
        int awarded = 0;
        for (int i = 0; i < 2_000; i++) {
            awarded += table.roll("astaps.loot.chance", random).getOrDefault(202, 0);
        }
        assertEquals(0.20, awarded / 2_000.0, 0.04);
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
                  - {reward: null, weight: 2}
                  - {reward: genshin.item.mora, weight: 3}
                """);
        int empty = 0;
        rng = new Random(123);
        for (int i = 0; i < 5_000; i++) if (nullable.roll("astaps.loot.pool", rng).isEmpty()) empty++;
        assertEquals(0.4, empty / 5000.0, 0.04);
    }

    @Test
    void sharedItemRegistryIsUsedByYaml() {
        var registry = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        var tables = YamlLootTables.parse("""
                chest:
                  - {reward: genshin.item.mora, count: 300}
                  - {reward: genshin.item.heros_wit}
                """, registry::resolve);
        assertEquals(Map.of(202, 300, 104003, 1), tables.roll("astaps.loot.chest", new Random(1)));

        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                chest:
                  - {reward: genshin.avatar.amber}
                """, registry::resolve));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                chest:
                  - {item: genshin.item.mora}
                """, registry::resolve));
    }

    @Test
    void itemClassificationUsesGameResourceTypes() {
        assertEquals("Weapons", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_WEAPON,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_NONE));
        assertEquals("Artifacts", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_RELIQUARY,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_NONE));
        assertEquals("Character EXP Materials", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_EXP_FRUIT));
        assertEquals("Food", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_FOOD));
        assertEquals(104003, new ItemIdRegistry(java.util.Collections.singletonMap(104003, null)).resolve("genshin.item.heros_wit"));
    }

    @Test
    void itemNamesKeepEnglishNamesAndDetectAmbiguity() {
        var names = new ItemIdRegistry(Map.of(
                104003, "Hero's Wit",
                104002, "Adventurer's Experience",
                202, "Mora"));
        assertEquals("genshin.item.heros_wit", names.describe(104003).key());
        assertEquals("Character EXP Materials", names.describe(104003).category());
        assertEquals(104003, names.resolve("Hero's Wit"));
        assertEquals(104003, names.resolve("genshin.item.heros_wit"));
        assertEquals(202, names.resolve("202"));
        var colliding = new ItemIdRegistry(Map.of(1, "Same", 2, "Same"));
        assertThrows(IllegalArgumentException.class, () -> colliding.resolve("genshin.item.same"));
    }
}
