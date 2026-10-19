package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.game.drop.ArtifactResolver;
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
        names.put(101, "Character EXP");
        names.put(102, "Adventure EXP");
        names.put(105, "Companionship EXP");
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

    static ArtifactResolver artifacts() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        String[] sets = {"berserker", "instructor", "the_exile",
                "adventurer", "lucky_dog", "traveling_doctor"};
        int[] prefixes = {55, 57, 59, 60, 61, 63};
        for (int set = 0; set < sets.length; set++) {
            for (int slot = 1; slot <= 5; slot++) {
                int firstRank = set < 3 ? 3 : 1;
                int lastRank = set < 3 ? 4 : 3;
                for (int rank = firstRank; rank <= lastRank; rank++) {
                    int firstSubstat = rank <= 2 ? 0 : rank - 2;
                    int lastSubstat = rank == 1 ? 0 : firstSubstat + 1;
                    for (int substats = firstSubstat; substats <= lastSubstat; substats++) {
                        int nativeSlot = switch (slot) {
                            case 1 -> 4; // flower
                            case 2 -> 2; // plume
                            case 3 -> 5; // sands
                            case 4 -> 1; // goblet
                            case 5 -> 3; // circlet
                            default -> throw new IllegalArgumentException("Invalid slot");
                        };
                        variants.add(new ArtifactResolver.Variant(
                                prefixes[set] * 1000 + rank * 100 + nativeSlot * 10 + substats,
                                sets[set], slot, rank, substats));
                    }
                }
            }
        }
        return new ArtifactResolver(variants);
    }

    private static String chestYaml() throws Exception {
        return resource("/rewards/loot.yaml").split("# Historical Trounce Boss rewards", 2)[0];
    }

    private static String chestCss() throws Exception {
        return resource("/rewards/rewards.css").split(java.util.regex.Pattern.quote("/* Weekly Boss native"), 2)[0];
    }

    private static YamlLootTables loot() throws Exception {
        return YamlLootTables.parse(chestYaml(),
                items()::resolve, artifacts());
    }

    @Test
    void twelveChestTiersContainNoFixedCurrencies() throws Exception {
        String cssSource = chestCss();
        assertFalse(cssSource.contains("--loot:"));
        assertTrue(cssSource.contains("astaps.loot.xxxhigh: 1;"));
        var loot = loot();
        String[] tiers = {
            "minimal", "nano", "micro",
            "light", "low", "medium",
            "high", "xhigh",
            "xxhigh", "xxxhigh",
            "max", "ultra"
        };
        assertEquals(35, loot.names().size()); // 12 chest tiers, 23 shared pools
        for (String tier : tiers) assertTrue(loot.contains("astaps.loot." + tier), tier);
        assertFalse(loot.contains("astaps.loot.three_realms_point"));
        for (String pool : new String[]{
                "weapons_1_star", "weapons_2_star", "weapons_3_star",
                "weapons_1_to_2_low", "weapons_1_to_2_high",
                "weapons_2_to_3_low", "weapons_2_to_3_high"}) {
            assertTrue(loot.contains("astaps.loot." + pool));
        }

        for (String pool : new String[]{
                "artifacts_1_to_2_low", "artifacts_1_to_2_high",
                "artifacts_1_to_3", "artifacts_2_to_3",
                "artifacts_3_to_4_low", "artifacts_3_to_4_high"}) {
            assertTrue(loot.contains("astaps.loot." + pool));
        }
        assertTrue(loot.names().stream().noneMatch(name ->
                name.startsWith("astaps.loot.chest_")));
        String reorderedYaml = chestYaml();
        assertTrue(reorderedYaml.indexOf("artifacts_3_to_4_high:") <
                reorderedYaml.indexOf("minimal:"));
        String yaml = chestYaml();
        for (String name : new String[]{
                "adventure_exp", "primogem", "mora", "anemo_sigil", "geo_sigil",
                "electro_sigil", "dendro_sigil"}) {
            String prefix = "adventure_exp".equals(name) ? "genshin.experience.adventure"
                    : "genshin.currency." + name;
            assertFalse(yaml.contains("reward: " + prefix), name);
        }
        assertFalse(yaml.contains("historical_"));
        assertFalse(java.util.regex.Pattern.compile("\\breward:\\s*[0-9]")
                .matcher(yaml).find());
        assertFalse(yaml.contains("count: \"1\""));
        assertFalse(java.util.regex.Pattern.compile(
                "(?:count|weight):\\s*\\\"?\\d+\\.\\d+").matcher(yaml).find());
    }

    @Test
    void allTwentyFourGradeAndLevelCombinationsSelectTwelveTiers() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        String[][] expected = {
            {"minimal", "nano", "micro",
             "light", "low", "medium"},
            {"medium", "medium", "medium",
             "medium", "high", "xhigh"},
            {"xhigh", "xhigh", "xhigh",
             "xhigh", "xxhigh", "xxxhigh"},
            {"xxxhigh", "xxxhigh", "xxxhigh",
             "xxxhigh", "max", "ultra"}
        };
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] levels = {1, 6, 11, 16, 21, 26};
        for (int kind = 0; kind < kinds.length; kind++) {
            for (int level = 0; level < levels.length; level++) {
                var selected = css.resolve("chest", Map.of(
                        "type", "normal", "grade", kinds[kind],
                        "level", Integer.toString(levels[level])));
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
        var css = CssRewards.parse(chestCss(), items(), loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] base = {2, 5, 10, 10}, low = {0, 2, 5, 10};
        for (int k = 0; k < kinds.length; k++) {
            assertEquals(base[k], css.resolve("chest", Map.of("type", "normal", "grade", kinds[k]))
                    .items().get(201).intValue());
            assertEquals(low[k], css.resolve("chest",
                    Map.of("type", "normal", "grade", kinds[k], "city", "1")).items().get(201).intValue());
            for (String area : new String[]{"10", "77", "8", "19", "40", "41", "47", "300"}) {
                assertEquals(base[k], css.resolve("chest", Map.of(
                        "type", "normal", "grade", kinds[k], "city", "1", "area", area))
                        .items().get(201).intValue());
            }
            var selected = css.resolve("chest", Map.of(
                    "type", "normal", "grade", kinds[k], "city", "2", "level", "26"));
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
    void missingChestLevelCannotSilentlyFallBackToGradeRewards() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        for (String kind : new String[]{"common", "exquisite", "precious", "luxurious"}) {
            for (String level : new String[]{null, "0", "-1", "unknown"}) {
                var props = new java.util.HashMap<String, String>();
                props.put("type", "normal");
                props.put("grade", kind);
                if (level != null) props.put("level", level);
                var selection = css.resolve("chest", props);
                assertTrue(selection.loot().isEmpty(), kind + " level=" + level);
                assertFalse(selection.items().containsKey(202), "Mora must have no fallback");
                assertFalse(selection.rangedItems().containsKey(202), "Mora range must have no fallback");
                assertFalse(selection.items().containsKey(ItemIdRegistry.REGIONAL_SIGIL),
                        "Sigil must have no fallback");
                assertEquals(30, selection.items().get(102).intValue());
                assertThrows(IllegalStateException.class,
                        () -> RewardConfiguration.requireChestLoot("chest", selection, props));
            }
            var valid = Map.of("type", "normal", "grade", kind, "level", "26");
            assertDoesNotThrow(() -> RewardConfiguration.requireChestLoot(
                    "chest", css.resolve("chest", valid), valid));
        }
        var unrecognized = Map.of("type", "normal", "grade", "unknown", "level", "26");
        assertThrows(IllegalStateException.class, () ->
                RewardConfiguration.requireChestLoot(
                        "chest", css.resolve("chest", unrecognized), unrecognized));
    }

    @Test
    void specialChestTypesDoNotInheritNormalChestRewards() throws Exception {
        var css = CssRewards.parse(chestCss(), items(), loot());
        for (String type : new String[]{"mora", "remarkable"}) {
            var attrs = Map.of("type", type, "grade", "common", "level", "26");
            var selection = css.resolve("chest", attrs);
            assertTrue(selection.items().isEmpty(), type);
            assertTrue(selection.loot().isEmpty(), type);
            assertTrue(selection.rangedItems().isEmpty(), type);
            assertThrows(IllegalStateException.class, () ->
                    RewardConfiguration.requireChestLoot("chest", selection, attrs));
        }
        var missingType = Map.of("grade", "common", "level", "26");
        assertTrue(css.resolve("chest", missingType).loot().isEmpty());
    }

    @Test
    void allTiersHaveWeaponAndEnhancementOreCandidates() throws Exception {
        var loot = loot();
        for (String tier : new String[]{
                "minimal", "nano", "micro",
                "light", "low", "medium",
                "high", "xhigh",
                "xxhigh", "xxxhigh",
                "max", "ultra"}) {
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
    void mergedBattleAndPuzzlePoolsUseNativePuzzleOreDistributions() throws Exception {
        // Native Mondstadt puzzle roots 52010000 through 52011100,
        // and their +14 ore sub-tables. Battle EXP/book pools remain intact.
        var loot = loot();
        String[] tiers = {
                "minimal", "nano", "micro", "light", "low", "medium",
                "high", "xhigh", "xxhigh", "xxxhigh", "max", "ultra"
        };
        double[][] expected = {
                {2.1, 0.2, 0}, {2.6, 0.4, 0}, {3.1, 0.6, 0},
                {4.1, 0.6, 0}, {5.1, 0.6, 0}, {0, 3.1, 0},
                {0, 4.1, 0}, {0, 2.1, 1}, {0, 3.1, 1},
                {0, 0, 3}, {0, 0, 3.1}, {0, 0, 3.2}
        };
        int[] ids = {104011, 104012, 104013};
        var random = new Random(721);
        final int trials = 3_000;
        for (int i = 0; i < tiers.length; i++) {
            double[] sums = new double[ids.length];
            boolean hasExperience = false;
            for (int trial = 0; trial < trials; trial++) {
                Map<Integer, Integer> rolled = loot.roll("astaps.loot." + tiers[i], random);
                for (int k = 0; k < ids.length; k++) {
                    sums[k] += rolled.getOrDefault(ids[k], 0);
                }
                hasExperience |= rolled.containsKey(104001)
                        || rolled.containsKey(104002) || rolled.containsKey(104003);
                for (int recipe : new int[]{109202, 109207, 109406, 109407}) {
                    assertFalse(rolled.containsKey(recipe),
                            "Recipes belong to CSS, not YAML: " + tiers[i]);
                }
            }
            for (int k = 0; k < ids.length; k++) {
                assertEquals(expected[i][k], sums[k] / trials, 0.055,
                        tiers[i] + " ore " + ids[k]);
            }
            assertTrue(hasExperience, tiers[i] + " has no experience book");
        }
    }

    @Test
    void nativeRegionalRecipesAreDefinedByCssInsteadOfLoot() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(chestCss(), items(), loot);
        int[] recipes = {109202, 109207, 109406, 109407};
        int[][] cases = {
                // grade, level, city, northern chicken, pizza, mora meat, stir fried filet
                {0, 21, 1, 1, 0, 0, 0},
                {0, 21, 2, 0, 0, 1, 1},
                {1, 21, 1, 0, 1, 0, 0},
                {1, 21, 2, 0, 0, 0, 0},
                {0, 26, 1, 0, 0, 0, 0},
                {0, 21, 3, 0, 0, 0, 0},
                {1, 20, 1, 0, 0, 0, 0}
        };
        for (int[] test : cases) {
            String kind = test[0] == 0 ? "exquisite" : "precious";
            var selected = css.resolve("chest", Map.of(
                    "type", "normal", "grade", kind,
                    "level", Integer.toString(test[1]),
                    "city", Integer.toString(test[2])));
            assertEquals(test[3], selected.items().getOrDefault(recipes[0], 0).intValue());
            assertEquals(test[4], selected.items().getOrDefault(recipes[1], 0).intValue());
            assertEquals(test[5], selected.items().getOrDefault(recipes[2], 0).intValue());
            assertEquals(test[6], selected.items().getOrDefault(recipes[3], 0).intValue());
            assertEquals(1, selected.loot().values().stream()
                    .filter(v -> v > 0).count());
        }
        String yaml = chestYaml();
        for (String name : new String[]{"recipe_mora_meat",
                "recipe_stir_fried_filet", "recipe_mushroom_pizza",
                "recipe_northern_smoked_chicken"}) {
            assertFalse(yaml.contains("reward: genshin.material." + name));
        }
    }

    @Test
    void nativeArtifactVariantsRemainResolvableWithSetProperties() throws Exception {
        String source = chestYaml();
        assertTrue(source.contains("reward: \"genshin.artifact.<set1>\""));
        assertTrue(source.contains("chest_artifact_pair<set1, set2, rarity, substats>:"));
        assertEquals(20, java.util.regex.Pattern.compile("(?m)^chest_artifact_mix_[0-9]+:")
                .matcher(source).results().count());
        var chosen = artifacts().compile("genshin.artifact.berserker",
                Map.of("slot", "goblet", "rarity", 3, "substats", 1));
        assertEquals(55311, chosen.select(new Random(1)));
    }
}
