package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.game.entity.gadget.chest.WorldChestLootHelper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BundledChestRewardsTest {
    private static String resource(String path) throws Exception {
        try (var stream = BundledChestRewardsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing bundled reward template: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlLootTables bundledLoot() throws Exception {
        // Native root contents use original numeric item IDs. Their lookup is validated
        // against actual game resources at server startup, not by this isolated fixture.
        var ids = new ItemIdRegistry(Map.ofEntries(
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
        return YamlLootTables.parse(resource("/rewards/loot.yaml"), reference ->
                reference.startsWith("genshin.item.")
                        ? ids.resolve(reference)
                        : Integer.parseInt(reference));
    }

    private static CssRewards bundledCss(YamlLootTables loot) throws Exception {
        return CssRewards.parse(resource("/rewards/rewards.css"),
                new ItemIdRegistry(Map.of(102, "Adventure EXP", 201, "Primogem")), loot);
    }

    @Test
    void all192SourceLevelSelectionsResolveToTheirPinnedNativeRoots() throws Exception {
        var loot = bundledLoot();
        var css = bundledCss(loot);
        var records = JsonParser.parseString(resource("/loot/chest-native-selectors.json"))
                .getAsJsonArray();
        assertEquals(192, records.size());
        var seen = new java.util.HashSet<String>();
        var roots = new java.util.HashSet<String>();

        for (var element : records) {
            var entry = element.getAsJsonObject();
            String tag = entry.get("tag").getAsString();
            int level = entry.get("minLevel").getAsInt();
            int root = entry.get("root").getAsInt();
            String target = "astaps.loot.native_" + root;
            seen.add(tag);
            roots.add(target);
            assertTrue(loot.contains(target), "Missing native root " + target);
            var selected = css.resolve("chest", Map.of(
                    "dropTag", tag,
                    "minLevel", Integer.toString(level),
                    "kind", "common",
                    "sigil", "305"));
            assertEquals(Map.of(target, 1), selected.loot(),
                    "Incorrect CSS mapping: " + tag + " level " + level);
            assertEquals(30, selected.items().get(102).intValue());
            assertTrue(selected.items().containsKey(201),
                    "Native tables still require a fixed Primogem decision");
        }
        assertEquals(52, seen.size());
        assertEquals(108, roots.size());
    }

    @Test
    void defaultFourTiersAndEightSigilsRemainFallbackRewards() throws Exception {
        var loot = bundledLoot();
        var css = bundledCss(loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] primogems = {2, 5, 10, 10};
        int[] minMora = {257, 756, 1433, 2000};
        int[] maxMora = {996, 1367, 1433, 3000};
        int[] sigils = {1, 2, 3, 4};
        for (int tier = 0; tier < kinds.length; tier++) {
            for (int sigil = 301; sigil <= 308; sigil++) {
                var selected = css.resolve("chest", Map.of(
                        "kind", kinds[tier],
                        "scene", "3",
                        "dropTag", "unrecognized source",
                        "level", "26",
                        "sigil", Integer.toString(sigil)));
                assertEquals(2, selected.loot().size());
                var rolled = RewardRoller.roll(selected, loot, new Random(tier * 100L + sigil));
                assertEquals(primogems[tier], rolled.get(201).intValue());
                assertTrue(rolled.get(202) >= minMora[tier] && rolled.get(202) <= maxMora[tier]);
                assertEquals(30, rolled.get(102).intValue());
                assertEquals(sigils[tier], rolled.get(sigil).intValue());
                assertTrue(rolled.size() >= 5);
            }
        }
    }

    @Test
    void nativeChestRootIsRolledInsteadOfHardcodedTierPayout() throws Exception {
        var loot = bundledLoot();
        var css = bundledCss(loot);
        var selected = css.resolve("chest", Map.of(
                "dropTag", "解谜低级蒙德",
                "minLevel", "1",
                "kind", "common",
                "sigil", "305"));
        assertEquals(Map.of("astaps.loot.native_52010000", 1), selected.loot());
        var reward = RewardRoller.roll(selected, loot, new Random(1));
        assertEquals(30, reward.get(102).intValue());
        assertEquals(1, reward.get(305).intValue());
        assertTrue(reward.get(202) >= 240 && reward.get(202) <= 360);
        assertFalse(reward.containsKey(201), "Old Mondstadt common chests give zero Primogems");
    }

    @Test
    void nativeChestPrimogemsFollowRegionAndTierInsteadOfLegacyCheatAmounts() throws Exception {
        var loot = bundledLoot();
        var css = bundledCss(loot);
        var cases = new Object[][]{
                {"战斗低级蒙德", "common", 0},
                {"解谜中级璃月", "exquisite", 2},
                {"战斗高级蒙德", "precious", 5},
                {"雪山战斗低级蒙德", "common", 2},
                {"战斗低级须弥", "common", 2},
                {"解谜中级稻妻", "exquisite", 5}
        };
        for (var sample : cases) {
            String tag = (String) sample[0];
            String tier = (String) sample[1];
            int gems = (Integer) sample[2];
            var selected = css.resolve("chest", Map.of(
                    "kind", tier, "dropTag", tag, "minLevel", "1", "sigil", "305"));
            assertEquals(30, selected.items().get(102).intValue(), tag);
            assertEquals(gems, selected.items().get(201).intValue(), tag);
            var rolled = RewardRoller.roll(selected, loot, new Random(1));
            assertEquals(30, rolled.get(102).intValue(), tag);
            assertEquals(gems, rolled.getOrDefault(201, 0).intValue(), tag);
        }
    }

    @Test
    void everyNativeTableIsCompiledAndReferencedRootsExist() throws Exception {
        var loot = bundledLoot();
        assertEquals(679, loot.names().stream().filter(name -> name.startsWith("astaps.loot.native_")).count());
        assertTrue(loot.contains("astaps.loot.native_55011100"));
        assertFalse(loot.contains("astaps.loot.native_0"));
    }

    @Test
    void sceneGadgetLevelMapsToNativeChestMinLevel() {
        assertEquals(1, WorldChestLootHelper.minLevelFromSourceLevel(1));
        assertEquals(1, WorldChestLootHelper.minLevelFromSourceLevel(5));
        assertEquals(6, WorldChestLootHelper.minLevelFromSourceLevel(6));
        assertEquals(11, WorldChestLootHelper.minLevelFromSourceLevel(15));
        assertEquals(16, WorldChestLootHelper.minLevelFromSourceLevel(20));
        assertEquals(21, WorldChestLootHelper.minLevelFromSourceLevel(25));
        assertEquals(26, WorldChestLootHelper.minLevelFromSourceLevel(60));
    }
}
