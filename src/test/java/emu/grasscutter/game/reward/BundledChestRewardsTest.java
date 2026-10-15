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
            assertNotNull(stream, "Missing chest rewards: " + path);
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

    private static YamlLootTables loadLoot(ItemIdRegistry registry) throws Exception {
        return YamlLootTables.parse(resource("/rewards/loot.yaml"), registry::resolve);
    }

    @Test
    void bundledChestsUseExactlyFourNamedTables() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] primogems = {2, 5, 10, 10};
        int[] sigils = {1, 2, 3, 4};
        int[] minMora = {257, 756, 1433, 2000};
        int[] maxMora = {996, 1367, 1433, 3000};
        assertEquals(708, loot.names().size()); // 29 active presets and 679 reference nodes.
        assertTrue(loot.contains("astaps.loot.three_realms_point"));

        for (int tier = 0; tier < kinds.length; tier++) {
            var selection = css.resolve("chest", Map.of("kind", kinds[tier]));
            assertEquals(Map.of("astaps.loot." + kinds[tier] + "_chest", 1),
                    selection.loot());
            assertEquals(Map.of(201, primogems[tier], 102, 30,
                    ItemIdRegistry.REGIONAL_SIGIL, sigils[tier]), selection.items());
            for (int sample = 0; sample < 20; sample++) {
                var rolled = RewardRoller.roll(
                        selection, loot, new Random(tier * 100L + sample),
                        new RewardContext(3, 0, "mondstadt"));
                assertEquals(primogems[tier], rolled.get(201).intValue());
                assertEquals(30, rolled.get(102).intValue());
                assertTrue(rolled.get(202) >= minMora[tier]
                        && rolled.get(202) <= maxMora[tier]);
                assertEquals(sigils[tier], rolled.get(305).intValue());
                assertTrue(rolled.keySet().stream().noneMatch(
                        id -> id >= 301 && id <= 308 && id != 305));
            }
        }
    }

    @Test
    void sixNamedLevelTiersUseTheCorrespondingTableForEveryChestKind() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        String[] names = {"light", "low", "medium", "high", "xhigh", "max"};
        int[] levels = {1, 6, 11, 16, 21, 26, 99};
        for (String kind : kinds) {
            for (int i = 0; i < levels.length; i++) {
                String tier = names[Math.min(i, names.length - 1)];
                var selection = css.resolve("chest", Map.of(
                        "kind", kind, "level", Integer.toString(levels[i])));
                String table = "astaps.loot." + kind + "_chest_" + tier;
                assertEquals(Map.of(table, 1), selection.loot());
                assertTrue(loot.contains(table));
                var actual = loot.roll(table, new Random(300));
                var base = loot.roll("astaps.loot." + kind + "_chest", new Random(300));
                assertEquals(base, actual, "Unverified tiers must not fabricate new drops");
            }
            assertEquals(Map.of("astaps.loot." + kind + "_chest", 1),
                    css.resolve("chest", Map.of("kind", kind)).loot());
        }
    }

    @Test
    void cityAndAreaChoosePrimogemsIndependentlyOfLevel() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] normal = {2, 5, 10, 10};
        int[] low = {0, 2, 5, 10};
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i];
            assertEquals(normal[i], css.resolve("chest", Map.of("kind", kind))
                    .items().get(201).intValue());
            for (String city : new String[]{"1", "2"}) {
                for (String level : new String[]{"1", "6", "16", "26"}) {
                    var selected = css.resolve("chest", Map.of(
                            "kind", kind, "city", city, "level", level));
                    assertEquals(low[i], selected.items().get(201).intValue());
                }
            }
            for (String city : new String[]{"3", "4", "5", "6", "7", "8"}) {
                assertEquals(normal[i], css.resolve("chest",
                        Map.of("kind", kind, "city", city)).items().get(201).intValue());
            }
            for (String area : new String[]{"10", "77"}) {
                assertEquals(normal[i], css.resolve("chest",
                        Map.of("kind", kind, "city", "1", "area", area))
                        .items().get(201).intValue());
                assertEquals(normal[i], css.resolve("chest",
                        Map.of("kind", kind, "area", area))
                        .items().get(201).intValue());
            }
            for (String area : new String[]{"8", "19", "40", "41", "47", "300"}) {
                assertEquals(normal[i], css.resolve("chest",
                        Map.of("kind", kind, "city", "2", "area", area))
                        .items().get(201).intValue());
                assertEquals(normal[i], css.resolve("chest",
                        Map.of("kind", kind, "area", area))
                        .items().get(201).intValue());
            }
        }
        var lowCommon = css.resolve("chest", Map.of(
                "kind", "common", "city", "2", "area", "5", "level", "26"));
        assertEquals(0, lowCommon.items().get(201).intValue());
        assertEquals(Map.of("astaps.loot.common_chest_max", 1), lowCommon.loot());
    }

    @Test
    void everyWorldChestTierAndRegionGrantsThirtyAdventureExp() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        for (String kind : new String[]{"common", "exquisite", "precious", "luxurious"}) {
            for (String level : new String[]{"1", "5", "6", "11", "16", "21", "26", "99"}) {
                for (String city : new String[]{"1", "2", "3"}) {
                    var selected = css.resolve("chest", Map.of(
                            "kind", kind, "level", level, "city", city, "area", "1"));
                    assertEquals(30, selected.items().get(102).intValue(),
                            kind + " level=" + level + " city=" + city);
                }
            }
            assertEquals(30, css.resolve("chest", Map.of("kind", kind))
                    .items().get(102).intValue());
        }
    }

    @Test
    void samePresetResolvesSigilForOtherSourceRegions() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        var selection = css.resolve("chest", Map.of("kind", "exquisite"));
        var rolled = RewardRoller.roll(selection, loot, new Random(5),
                new RewardContext(3, 0, "liyue"));
        assertEquals(2, rolled.get(307).intValue());
        assertFalse(rolled.containsKey(305));
        var unresolved = RewardRoller.roll(selection, loot, new Random(5));
        assertEquals(5, unresolved.get(201).intValue());
        assertEquals(30, unresolved.get(102).intValue());
        assertFalse(unresolved.containsKey(305));
        assertFalse(unresolved.containsKey(307));
    }

    @Test
    void regionMetadataDoesNotAffectAnOrdinaryChestPreset() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        var basic = css.resolve("chest", Map.of("kind", "common"));
        assertEquals(basic, css.resolve("chest", Map.of(
                "kind", "common", "scene", "3", "level", "26",
                "group", "910700123", "config", "10")));
        assertTrue(css.resolve("chest", Map.of()).loot().isEmpty());
    }

    @Test
    void bundledLootUsesQualifiedItemNamesAndCompactProbabilities() throws Exception {
        String yaml = resource("/rewards/loot.yaml");
        assertFalse(java.util.regex.Pattern.compile("\\breward:\\s*[0-9]")
                .matcher(yaml).find(), "Item rewards must use qualified names");
        assertFalse(yaml.contains("count: \"1\""), "The default count is implicit");
        var matcher = java.util.regex.Pattern.compile("\\bweight:\\s*([0-9]+(?:\\.[0-9]+)?)")
                .matcher(yaml);
        int weights = 0;
        while (matcher.find()) {
            assertTrue(Double.parseDouble(matcher.group(1)) <= 100,
                    "Unexpected large relative weight: " + matcher.group(1));
            weights++;
        }
        assertTrue(weights > 200);
    }

    @Test
    void historicalCombatAndPuzzleTablesRemainIndependentOfCss() throws Exception {
        var registry = items();
        var loot = loadLoot(registry);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);

        for (String table : new String[]{
                "historical_mondstadt_combat_common",
                "historical_mondstadt_puzzle_common",
                "historical_mondstadt_combat_exquisite_max",
                "historical_mondstadt_puzzle_exquisite_max",
                "historical_golden_apple_archipelago_combat_common",
                "historical_golden_apple_archipelago_puzzle_common",
                "historical_dragonspine_combat_common",
                "historical_dragonspine_puzzle_common",
                "historical_dragonspine_crimson_agate_common"}) {
            assertTrue(loot.contains("astaps.loot." + table), table);
        }
        assertEquals(679, loot.names().stream()
                .filter(name -> name.startsWith("astaps.loot.historical_")).count());

        for (int seed = 0; seed < 64; seed++) {
            var combat = loot.roll("astaps.loot.historical_mondstadt_combat_common", new Random(seed));
            var puzzle = loot.roll("astaps.loot.historical_mondstadt_puzzle_common", new Random(seed));
            assertEquals(10, combat.get(102).intValue());
            assertEquals(10, puzzle.get(102).intValue());
            assertEquals(1, combat.get(305).intValue());
            assertEquals(1, puzzle.get(305).intValue());
            assertTrue(combat.get(202) >= 240 && combat.get(202) <= 360);
            assertTrue(puzzle.get(202) >= 240 && puzzle.get(202) <= 360);
            assertTrue(combat.get(104001) >= 1 && combat.get(104001) <= 2);
            assertFalse(combat.containsKey(104011));
            assertTrue(puzzle.get(104011) >= 2 && puzzle.get(104011) <= 3);
            assertFalse(puzzle.containsKey(104001));
        }

        assertEquals(Map.of("astaps.loot.common_chest", 1),
                css.resolve("chest", Map.of("kind", "common")).loot());
    }

    @Test
    void shippedTemplatesHaveNoHistoricalMappingNamesOrChineseTags() throws Exception {
        for (String text : new String[]{
                resource("/rewards/loot.yaml"), resource("/rewards/rewards.css")}) {
            assertFalse(text.contains("native_"));
            assertFalse(text.contains("fallback_"));
            assertFalse(text.contains("dropTag"));
            assertFalse(text.contains("[sigil="));
            assertFalse(text.contains("drop_tag"));
            assertFalse(text.codePoints().anyMatch(
                    cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN));
        }
    }
}
