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
        try (var in = BundledChestRewardsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Missing bundled reward template: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void bundledLootAndCssPreserveAllFourWorldChestRewardTiers() throws Exception {
        var items = new ItemIdRegistry(Map.ofEntries(
                Map.entry(102, "Adventure EXP"),
                Map.entry(201, "Primogem"),
                Map.entry(202, "Mora"),
                Map.entry(301, "Pyro Sigil"),
                Map.entry(302, "Hydro Sigil"),
                Map.entry(303, "Dendro Sigil"),
                Map.entry(304, "Electro Sigil"),
                Map.entry(305, "Anemo Sigil"),
                Map.entry(306, "Cryo Sigil"),
                Map.entry(307, "Geo Sigil"),
                Map.entry(308, "Lunar Sigil"),
                Map.entry(104001, "Wanderer's Advice"),
                Map.entry(104002, "Adventurer's Experience"),
                Map.entry(104003, "Hero's Wit"),
                Map.entry(104011, "Enhancement Ore"),
                Map.entry(104012, "Fine Enhancement Ore"),
                Map.entry(104013, "Mystic Enhancement Ore")));
        var loot = YamlLootTables.parse(resource("/rewards/loot.yaml"), items::resolve);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), items, loot);

        String[] tiers = {"common", "exquisite", "precious", "luxurious"};
        int[] primogems = {66, 166, 366, 566};
        int[] mora = {1000, 1500, 2000, 2500};
        int[] adventureExp = {20, 25, 30, 30};
        int[] sigils = {3, 5, 8, 10};

        for (int tier = 0; tier < tiers.length; tier++) {
            for (int sigil = 301; sigil <= 308; sigil++) {
                var selected = css.resolve("chest", Map.of(
                        "kind", tiers[tier], "scene", "3", "sigil", Integer.toString(sigil)));
                assertEquals(1, selected.loot().size());
                var rolled = RewardRoller.roll(selected, loot, new Random(tier * 100L + sigil));
                assertEquals(primogems[tier], rolled.get(201));
                assertEquals(mora[tier], rolled.get(202));
                assertEquals(adventureExp[tier], rolled.get(102));
                assertEquals(sigils[tier], rolled.get(sigil));
                assertEquals(5 + (tier == 0 ? -1 : tier == 3 ? 0 : 0),
                        rolled.size(), "Check all material entries exist");
            }
        }
        assertTrue(css.resolve("monster", Map.of("rank", "elite")).loot().isEmpty());
    }

    @Test
    void defaultRewardMaterialsMatchOriginalTierQuantities() throws Exception {
        var items = new ItemIdRegistry(Map.ofEntries(
                Map.entry(102, "Adventure EXP"), Map.entry(201, "Primogem"),
                Map.entry(202, "Mora"), Map.entry(305, "Anemo Sigil"),
                Map.entry(104001, "Wanderer's Advice"),
                Map.entry(104002, "Adventurer's Experience"),
                Map.entry(104003, "Hero's Wit"),
                Map.entry(104011, "Enhancement Ore"),
                Map.entry(104012, "Fine Enhancement Ore"),
                Map.entry(104013, "Mystic Enhancement Ore")));
        var loot = YamlLootTables.parse(resource("/rewards/loot.yaml"), items::resolve);
        assertEquals(Map.of(104011, 2, 104001, 2, 104002, 1),
                loot.roll("astaps.loot.starter_materials", new Random(1)));
        assertEquals(Map.of(104012, 2, 104001, 3, 104002, 2, 104003, 1),
                loot.roll("astaps.loot.intermediate_materials", new Random(1)));
        assertEquals(Map.of(104012, 3, 104013, 1, 104001, 3, 104002, 3, 104003, 2),
                loot.roll("astaps.loot.advanced_materials", new Random(1)));
        assertEquals(Map.of(104012, 4, 104013, 1, 104001, 2, 104002, 4, 104003, 3),
                loot.roll("astaps.loot.rich_materials", new Random(1)));
    }
}
