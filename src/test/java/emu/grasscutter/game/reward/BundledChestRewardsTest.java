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
        return YamlLootTables.parse(resource("/rewards/loot.yaml"),
                reference -> Integer.parseInt(reference));
    }

    private static CssRewards bundledCss(YamlLootTables loot) throws Exception {
        return CssRewards.parse(resource("/rewards/rewards.css"),
                new ItemIdRegistry(Map.of()), loot);
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
                    "level", Integer.toString(level),
                    "kind", "common",
                    "sigil", "305"));
            assertEquals(Map.of(target, 1), selected.loot(),
                    "Incorrect CSS mapping: " + tag + " level " + level);
            assertTrue(selected.items().isEmpty(), "Fixed CSS values override native loot");
        }
        assertEquals(52, seen.size());
        assertEquals(108, roots.size());
    }

    @Test
    void defaultFourTiersAndEightSigilsRemainFallbackRewards() throws Exception {
        var loot = bundledLoot();
        var css = bundledCss(loot);
        String[] kinds = {"common", "exquisite", "precious", "luxurious"};
        int[] primogems = {66, 166, 366, 566};
        int[] mora = {1000, 1500, 2000, 2500};
        int[] adventureExp = {20, 25, 30, 30};
        int[] sigils = {3, 5, 8, 10};
        int[] itemTypes = {7, 8, 9, 9};
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
                assertEquals(mora[tier], rolled.get(202).intValue());
                assertEquals(adventureExp[tier], rolled.get(102).intValue());
                assertEquals(sigils[tier], rolled.get(sigil).intValue());
                assertEquals(itemTypes[tier], rolled.size());
            }
        }
    }

    @Test
    void nativeChestRootIsRolledInsteadOfHardcodedTierPayout() throws Exception {
        var loot = bundledLoot();
        var css = bundledCss(loot);
        var selected = css.resolve("chest", Map.of(
                "dropTag", "解谜低级蒙德",
                "level", "1",
                "kind", "common",
                "sigil", "305"));
        assertEquals(Map.of("astaps.loot.native_52010000", 1), selected.loot());
        var reward = RewardRoller.roll(selected, loot, new Random(1));
        assertEquals(10, reward.get(102).intValue());
        assertEquals(1, reward.get(305).intValue());
        assertTrue(reward.get(202) >= 240 && reward.get(202) <= 360);
        assertFalse(reward.containsKey(201));
    }

    @Test
    void everyNativeTableIsCompiledAndReferencedRootsExist() throws Exception {
        var loot = bundledLoot();
        assertEquals(679, loot.names().stream().filter(name -> name.startsWith("astaps.loot.native_")).count());
        assertTrue(loot.contains("astaps.loot.native_55011100"));
        assertFalse(loot.contains("astaps.loot.native_0"));
    }

    @Test
    void adventureRankMapsToNativeChestMinLevel() {
        assertEquals(1, WorldChestLootHelper.chestLevelBand(1));
        assertEquals(1, WorldChestLootHelper.chestLevelBand(5));
        assertEquals(6, WorldChestLootHelper.chestLevelBand(6));
        assertEquals(11, WorldChestLootHelper.chestLevelBand(15));
        assertEquals(16, WorldChestLootHelper.chestLevelBand(20));
        assertEquals(21, WorldChestLootHelper.chestLevelBand(25));
        assertEquals(26, WorldChestLootHelper.chestLevelBand(60));
    }
}
