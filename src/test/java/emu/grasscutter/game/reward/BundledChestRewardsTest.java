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
                        variants.add(new ArtifactResolver.Variant(
                                prefixes[set] * 1000 + rank * 100 + slot * 10 + substats,
                                sets[set], slot, rank, substats));
                    }
                }
            }
        }
        return new ArtifactResolver(variants);
    }

    private static YamlLootTables loot() throws Exception {
        return YamlLootTables.parse(resource("/rewards/loot.yaml"),
                items()::resolve, artifacts());
    }

    @Test
    void twelveChestTiersContainNoFixedCurrencies() throws Exception {
        var loot = loot();
        String[] tiers = {
            "minimal", "nano", "micro",
            "light", "low", "medium",
            "high", "xhigh",
            "xxhigh", "xxxhigh",
            "max", "ultra"
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
        assertFalse(java.util.regex.Pattern.compile(
                "(?:count|weight):\\s*\\\"?\\d+\\.\\d+").matcher(yaml).find());
    }

    @Test
    void allTwentyFourGradeAndLevelCombinationsSelectTwelveTiers() throws Exception {
        var loot = loot();
        var css = CssRewards.parse(resource("/rewards/rewards.css"), items(), loot);
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
    void nativeArtifactVariantsRemainResolvableWithSetProperties() throws Exception {
        String source = resource("/rewards/loot.yaml");
        assertTrue(source.contains("reward: genshin.artifact.adventurer"));
        assertTrue(source.contains("properties: {slot: \"1~5\", rarity: 1}"));
        assertEquals(18, java.util.regex.Pattern.compile(
                "reward: genshin\\.artifact\\.[a-z0-9_]+")
                .matcher(source).results().count());
        var chosen = artifacts().compile("genshin.artifact.berserker",
                Map.of("slot", "goblet", "rarity", 3, "substats", 1));
        assertEquals(55311, chosen.select(new Random(1)));
    }
}
