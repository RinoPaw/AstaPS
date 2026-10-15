package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NativeChestMigrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String resource(String name) throws Exception {
        try (InputStream stream = NativeChestMigrationTest.class.getResourceAsStream(name)) {
            assertNotNull(stream, "Missing migration resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ItemIdRegistry knownItems() {
        return new ItemIdRegistry(Map.ofEntries(
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
    }

    private static YamlLootTables loadLoot(ItemIdRegistry items) throws Exception {
        return YamlLootTables.parse(resource("/rewards/loot.yaml"), value ->
                value.startsWith("genshin.item.")
                        ? items.resolve(value)
                        : Integer.parseInt(value));
    }

    @Test
    void everyNativeChestVariantSelectsItsExactMigratedRoot() throws Exception {
        var items = knownItems();
        var loot = loadLoot(items);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), items, loot);
        var manifest = JSON.readTree(resource("/loot/native-chest-variants.json"));
        assertEquals(52, manifest.path("tags").asInt());
        assertEquals(192, manifest.path("rows").asInt());
        assertEquals(108, manifest.path("rootTables").asInt());
        assertEquals(679, manifest.path("reachableTables").asInt());

        var tags = new HashSet<String>();
        var roots = new HashSet<Integer>();
        int rows = 0;
        for (var variant : manifest.path("variants")) {
            String dropTag = variant.path("dropTag").asText();
            int minLevel = variant.path("minLevel").asInt();
            int rootId = variant.path("rootId").asInt();
            tags.add(dropTag);
            roots.add(rootId);
            var selected = css.resolve("chest", Map.of(
                    "kind", "common",
                    "scene", "3",
                    "sigil", "305",
                    "dropTag", dropTag,
                    "minLevel", Integer.toString(minLevel),
                    "level", Integer.toString(minLevel)));
            assertEquals(Map.of("astaps.loot.native_" + rootId, 1), selected.loot(),
                    "Wrong root for " + dropTag + " at " + minLevel);
            assertTrue(selected.items().isEmpty(),
                    "The fallback must not override native item quantities");
            rows++;
        }
        assertEquals(192, rows);
        assertEquals(52, tags.size());
        assertEquals(108, roots.size());
        for (int id : roots) {
            assertTrue(loot.contains("astaps.loot.native_" + id), "Missing native root " + id);
            assertDoesNotThrow(() -> loot.roll("astaps.loot.native_" + id, new Random(id)));
        }
    }

    @Test
    void theTwoKnownMondstadtNativeVariantsKeepTheirNativeGuarantees() throws Exception {
        var items = knownItems();
        var loot = loadLoot(items);
        for (int id : new int[]{52010000, 52010100}) {
            var random = new Random(id);
            for (int i = 0; i < 100; i++) {
                var result = loot.roll("astaps.loot.native_" + id, random);
                assertEquals(10, result.get(102).intValue());
                assertEquals(1, result.get(305).intValue());
                assertTrue(result.get(202) >= 240 && result.get(202) <= 360);
            }
        }
    }

    @Test
    void cssLeavesTheDefaultTierAvailableForObjectsWithoutNativeTags() throws Exception {
        var items = knownItems();
        var loot = loadLoot(items);
        var css = CssRewards.parse(resource("/rewards/rewards.css"), items, loot);
        var selection = css.resolve("chest", Map.of("kind", "common", "sigil", "305"));
        assertEquals(Map.of(
                "astaps.loot.fallback_common", 1,
                "astaps.loot.anemo_sigil", 3), selection.loot());
        var rolled = RewardRoller.roll(selection, loot, new Random(1));
        assertEquals(66, rolled.get(201).intValue());
        assertEquals(1000, rolled.get(202).intValue());
        assertEquals(20, rolled.get(102).intValue());
        assertEquals(3, rolled.get(305).intValue());
    }
}
