package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.ArtifactResolver;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Ensure the entire bundled CSS/YAML, including Boss tables, can load together. */
final class BundledBossRewardsTest {
    @Test
    void nativeBossSelectorsResolveToBundledSemanticLoot() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String stylesheet = RewardConfiguration.readBundled("rewards.css");

        // Parse all tables, including Boss tables, with unique synthetic inventory IDs.
        // The server resource loader performs the separate real-item-identity validation.
        var nativeIds = new HashMap<String, Integer>();
        var nextId = new AtomicInteger(3000000);
        var variants = new ArrayList<ArtifactResolver.Variant>();
        var nextArtifactId = new AtomicInteger(4000000);
        Pattern.compile("reward: genshin\\.artifact\\.([a-z0-9_]+)")
                .matcher(yaml).results()
                .map(match -> match.group(1)).distinct()
                .forEach(name -> variants.add(new ArtifactResolver.Variant(
                        nextArtifactId.incrementAndGet(), name, 1, 5, 3)));
        var loot = YamlLootTables.parse(yaml,
                id -> nativeIds.computeIfAbsent(id, ignored -> nextId.incrementAndGet()),
                new ArtifactResolver(variants));

        var known = new HashMap<Integer, String>(ItemIdRegistry.chestDropNames());
        known.putAll(Map.ofEntries(
                Map.entry(101, "Character EXP"),
                Map.entry(102, "Adventure EXP"),
                Map.entry(105, "Companionship EXP"),
                Map.entry(201, "Primogem"),
                Map.entry(202, "Mora"),
                Map.entry(104001, "Wanderer's Advice"),
                Map.entry(104002, "Adventurer's Experience"),
                Map.entry(104003, "Hero's Wit"),
                Map.entry(104011, "Enhancement Ore"),
                Map.entry(104012, "Fine Enhancement Ore"),
                Map.entry(104013, "Mystic Enhancement Ore"),
                Map.entry(303, "Dendro Sigil"),
                Map.entry(304, "Electro Sigil"),
                Map.entry(305, "Anemo Sigil"),
                Map.entry(307, "Geo Sigil")));
        var css = CssRewards.parse(stylesheet, new ItemIdRegistry(known), loot);
        assertEquals(1175, stylesheet.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertEquals(250, stylesheet.lines()
                .filter(line -> line.startsWith("boss[type=\"weekly\"][tag=\"")).count());

        var normal = css.resolve("boss", Map.of(
                "type", "world", "tag", "霜夜灵嗣", "level", "1"));
        assertEquals(1825, normal.items().get(202));
        assertEquals(200, normal.items().get(102));
        assertEquals(1, normal.loot().get("astaps.loot.world_frostnight_scion_level_1"));

        var legacy = css.resolve("boss", Map.of(
                "type", "world", "tag", "无相之雷", "level", "36"));
        assertEquals(1, legacy.loot().get("astaps.loot.world_electro_hypostasis_level_35"));

        // Reused YAML pools keep each externally addressable table name.
        assertTrue(loot.contains("astaps.loot.world_frostnight_scion_level_105"));
        assertEquals(
                loot.roll("astaps.loot.world_frostnight_scion_level_100", new java.util.Random(71)),
                loot.roll("astaps.loot.world_frostnight_scion_level_105", new java.util.Random(71)));

        var highWorld = css.resolve("boss", Map.of(
                "type", "world", "tag", "无相之雷", "level", "105"));
        assertEquals(200, highWorld.items().get(102));
        assertEquals(6000, highWorld.items().get(202));
        assertEquals(45, highWorld.items().get(105));
        assertEquals(200, highWorld.items().get(101));
        assertEquals(1, highWorld.loot().get("astaps.loot.world_electro_hypostasis_level_105"));

        var weekly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "风魔龙", "level", "90"));
        assertEquals(300, weekly.items().get(102));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_stormterror_level_90"));
    }
}
