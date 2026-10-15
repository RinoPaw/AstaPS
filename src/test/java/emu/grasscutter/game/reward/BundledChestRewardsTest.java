package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BundledChestRewardsTest {
    private static String resource(String path) throws Exception {
        try (var stream = BundledChestRewardsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ItemIdRegistry items() {
        var names = new java.util.HashMap<Integer, String>(ItemIdRegistry.chestDropNames());
        names.put(102, "Adventure EXP");
        names.put(201, "Primogem");
        names.put(202, "Mora");
        names.put(104001, "Wanderer's Advice");
        names.put(104002, "Adventurer's Experience");
        names.put(104003, "Hero's Wit");
        names.put(104011, "Enhancement Ore");
        names.put(104012, "Fine Enhancement Ore");
        names.put(104013, "Mystic Enhancement Ore");
        names.put(303, "Dendro Sigil");
        names.put(304, "Electro Sigil");
        names.put(305, "Anemo Sigil");
        names.put(307, "Geo Sigil");
        return new ItemIdRegistry(names);
    }

    private static YamlLootTables loot() throws Exception {
        return YamlLootTables.parse(resource("/rewards/loot.yaml"), items()::resolve);
    }

    @Test
    void twelveChestTiersContainNoFixedCurrencies() throws Exception {
        var loot = loot();
        String[] tiers = {
            "chest_common_light", "chest_common_low", "chest_common_medium",
            "chest_common_high", "chest_common_xhigh", "chest_common_max",
            "chest_exquisite_xhigh", "chest_exquisite_max",
            "chest_precious_xhigh", "chest_precious_max",
            "chest_luxurious_xhigh", "chest_luxurious_max"
        };
        assertEquals(99, loot.names().size()); // 12 tiers, 86 supporting pools, one point
        for (String tier : tiers) assertTrue(loot.contains("astaps.loot." + tier), tier);
        assertTrue(loot.contains("astaps.loot.three_realms_point"));
        String yaml = resource("/rewards/loot.yaml");
        for (String name : new String[]{
                "adventure_exp", "primogem", "mora", "anemo_sigil", "geo_sigil",
                "electro_sigil", "dendro_sigil"}) {
            assertFalse(yaml.contains("reward: genshin.item." + name), name);
        }
        assertFalse(yaml.contains("historical_"));
        assertFalse(java.util.regex.Pattern.compile("\\breward:\\s*[0-9]")
                .matcher(yaml).find());
        assertFalse(yaml.contains("count: \"1\""));
    }

    @Test
    void allTwentyFourGradeAndLevelCombinationsSelectTwelveTiers() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(resource("/rewards/rewards.css"), items(), loot);
        String[][] expected = {
            {"chest_common_light", "chest_common_low", "chest_common_medium",
             "chest_common_high", "chest_common_xhigh", "chest_common_max"},
            {"chest_common_max", "chest_common_max", "chest_common_max",
             "chest_common_max", "chest_exquisite_xhigh", "chest_exquisite_max"},
            {"chest_exquisite_max", "chest_exquisite_max", "chest_exquisite_max",
             "chest_exquisite_max", "chest_precious_xhigh", "chest_precious_max"},
            {"chest_precious_max", "chest_precious_max", "chest_precious_max",
             "chest_precious_max", "chest_luxurious_xhigh", "chest_luxurious_max"}
        };
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] levels = {1, 6, 11, 16, 21, 26};
        for (int kind = 0; kind < kinds.length; kind++) {
            for (int level = 0; level < levels.length; level++) {
                var selected = css.resolve("chest", Map.of(
                        "kind", kinds[kind], "level", Integer.toString(levels[level])));
                assertEquals(Map.of("astaps.loot." + expected[kind][level], 1),
                        selected.loot());
                assertEquals(30, selected.items().get(102).intValue());
                assertTrue(selected.rangedItems().containsKey(202));
            }
        }
    }

    @Test
    void regionalPrimogemsAndThirtyAdventureExpRemainCssOwned() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(resource("/rewards/rewards.css"), items(), loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] base = {2, 5, 10, 10}, low = {0, 2, 5, 10};
        for (int k = 0; k < kinds.length; k++) {
            assertEquals(base[k], css.resolve("chest", Map.of("kind", kinds[k]))
                    .items().get(201).intValue());
            assertEquals(low[k], css.resolve("chest",
                    Map.of("kind", kinds[k], "city", "1")).items().get(201).intValue());
            for (String area : new String[]{"10", "77"}) {
                assertEquals(base[k], css.resolve("chest", Map.of(
                        "kind", kinds[k], "city", "1", "area", area))
                        .items().get(201).intValue());
            }
            var selected = css.resolve("chest", Map.of(
                    "kind", kinds[k], "city", "2", "level", "26"));
            for (int seed = 0; seed < 32; seed++) {
                var rolled = RewardRoller.roll(selected, loot, new Random(seed),
                        new RewardContext(3, 0, "liyue"));
                assertEquals(30, rolled.get(102).intValue());
                assertEquals(low[k], rolled.getOrDefault(201, 0).intValue());
                assertTrue(rolled.get(202) >= 240 && rolled.get(202) <= 2160);
                assertTrue(rolled.get(307) >= 1 && rolled.get(307) <= 6);
            }
        }
    }

    @Test
    void allTiersHaveWeaponAndEnhancementOreCandidates() throws Exception {
        var loot = loot();
        for (String tier : new String[]{
                "chest_common_light", "chest_common_low", "chest_common_medium",
                "chest_common_high", "chest_common_xhigh", "chest_common_max",
                "chest_exquisite_xhigh", "chest_exquisite_max",
                "chest_precious_xhigh", "chest_precious_max",
                "chest_luxurious_xhigh", "chest_luxurious_max"}) {
            boolean hasWeapon = false, hasOre = false;
            for (int seed = 0; seed < 120; seed++) {
                var rewards = loot.roll("astaps.loot." + tier, new Random(seed));
                hasWeapon |= rewards.keySet().stream().anyMatch(
                        id -> id >= 11101 && id <= 15305);
                hasOre |= rewards.keySet().stream().anyMatch(
                        id -> id >= 104011 && id <= 104013);
            }
            assertTrue(hasWeapon, tier + " has no weapon drops");
            assertTrue(hasOre, tier + " has no enhancement ore drops");
        }
    }

    @Test
    void sourceArtifactIdentitiesRemainUnchanged() throws Exception {
        String source = resource("/rewards/loot.yaml");
        assertTrue(source.contains("genshin.item.artifact_adventurer_flower_one_star_zero_initial_substats"));
        assertTrue(source.contains("genshin.item.artifact_instructor_circlet_four_star_three_initial_substats"));
    }
}
