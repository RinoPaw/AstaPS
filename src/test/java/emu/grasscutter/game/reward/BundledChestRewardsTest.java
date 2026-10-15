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
        return new ItemIdRegistry(Map.ofEntries(
                Map.entry(102, "Adventure EXP"),
                Map.entry(201, "Primogem"),
                Map.entry(202, "Mora"),
                Map.entry(104001, "Wanderer's Advice"),
                Map.entry(104002, "Adventurer's Experience"),
                Map.entry(104003, "Hero's Wit"),
                Map.entry(104011, "Enhancement Ore"),
                Map.entry(104012, "Fine Enhancement Ore")));
    }

    @Test
    void bundledChestsUseExactlyFourNamedTables() throws Exception {
        var registry = items();
        var loot = YamlLootTables.parse(resource("/rewards/loot.yaml"), registry::resolve);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] primogems = {2, 5, 10, 10};
        int[] sigils = {1, 2, 3, 4};
        int[] minMora = {257, 756, 1433, 2000};
        int[] maxMora = {996, 1367, 1433, 3000};
        assertEquals(5, loot.names().size()); // Four chests and one scene-point item pool.
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
    void samePresetResolvesSigilForOtherSourceRegions() throws Exception {
        var registry = items();
        var loot = YamlLootTables.parse(resource("/rewards/loot.yaml"), registry::resolve);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        var selection = css.resolve("chest", Map.of("kind", "exquisite"));
        var rolled = RewardRoller.roll(selection, loot, new Random(5),
                new RewardContext(3, 0, "liyue"));
        assertEquals(2, rolled.get(307).intValue());
        assertFalse(rolled.containsKey(305));
        assertThrows(IllegalArgumentException.class,
                () -> RewardRoller.roll(selection, loot, new Random(5)));
    }

    @Test
    void regionMetadataDoesNotAffectAnOrdinaryChestPreset() throws Exception {
        var registry = items();
        var loot = YamlLootTables.parse(resource("/rewards/loot.yaml"), registry::resolve);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), registry, loot);
        var basic = css.resolve("chest", Map.of("kind", "common"));
        assertEquals(basic, css.resolve("chest", Map.of(
                "kind", "common", "scene", "3", "level", "26",
                "group", "910700123", "config", "10")));
        assertTrue(css.resolve("chest", Map.of()).loot().isEmpty());
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
