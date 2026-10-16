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
        assertEquals(965, stylesheet.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertEquals(200, stylesheet.lines()
                .filter(line -> line.startsWith("boss[type=\"weekly\"][tag=\"")).count());

        // Equal-weight five-slot artifact pools are inlined into their callers.
        assertFalse(loot.contains("astaps.loot.weekly_shouki_no_kami_level_30_artifacts_variant_7"));
        assertFalse(loot.contains("astaps.loot.world_electro_hypostasis_level_45_artifacts_variant_6"));
        assertTrue(yaml.contains("reward: genshin.artifact.the_exile, properties: {slot: \"1~5\", rarity: 4, substats: 2}, weight: 1"));

        // Shared Boss artifact pools encode equivalent 33:17 substat odds directly.
        assertTrue(yaml.contains("substats: \"1:66, 2:34\""));
        assertTrue(yaml.contains("substats: \"2:66, 3:34\""));
        assertTrue(yaml.contains("substats: \"3:66, 4:34\""));

        // Native null/item odds are represented by a single optional drop.
        assertTrue(yaml.contains("reward: genshin.material.dream_solvent, count: \"1:33\""));
        assertTrue(yaml.contains("reward: *midlander_weapon_billets, count: \"1:12\""));
        assertFalse(Pattern.compile("(?m)^  - reward:\\n      - \\{reward: null, weight:")
                .matcher(yaml).find());

        // Reusable pools have content-based names; Boss-level entry names are stable.
        assertEquals(1393, loot.names().size());
        assertTrue(loot.contains("astaps.loot.midlander_weapon_billets"));
        assertTrue(loot.contains("astaps.loot.northlander_weapon_billets"));
        assertFalse(loot.contains("astaps.loot.weekly_shouki_no_kami_level_1_weapon_billets"));
        assertTrue(yaml.lines().noneMatch(line -> line.matches(
                "^(world|weekly)_.+_level_[0-9]+_[a-z0-9_]+:.*")));

        // Shared Boss rewards depend on type/level. Specific loot still needs a tag.
        var unidentified = css.resolve("boss", Map.of("type", "world", "level", "105"));
        assertEquals(200, unidentified.items().get(102));
        assertEquals(6000, unidentified.items().get(202));
        assertEquals(6000, css.resolve("boss", Map.of(
                "type", "world", "level", "140")).items().get(202));
        assertEquals(9000, css.resolve("boss", Map.of(
                "type", "weekly", "level", "140")).items().get(202));
        assertEquals(45, unidentified.items().get(105));
        assertTrue(unidentified.loot().isEmpty());

        var normal = css.resolve("boss", Map.of(
                "type", "world", "tag", "frostnight_scion", "level", "1"));
        assertEquals(1825, normal.items().get(202));
        assertEquals(200, normal.items().get(102));
        assertEquals(1, normal.loot().get("astaps.loot.world_frostnight_scion_level_1"));

        var legacy = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "36"));
        assertEquals(1, legacy.loot().get("astaps.loot.world_electro_hypostasis_level_35"));

        // Native source text tags are translated only for CSS selection.
        // The original Chinese tag still belongs to the native fallback.
        var rewardConfig = new RewardConfiguration(loot, css);
        assertEquals(6000, rewardConfig.rollWorldBoss(
                "无相之雷", 105, new java.util.Random(71)).get(202));
        assertEquals(6000, rewardConfig.rollWorldBoss(
                "electro_hypostasis", 105, new java.util.Random(71)).get(202));

        // CSS references the canonical pool directly; no duplicate Boss-level aliases.
        assertTrue(loot.contains("astaps.loot.world_frostnight_scion_level_100"));
        assertFalse(loot.contains("astaps.loot.world_frostnight_scion_level_105"));
        assertFalse(loot.contains("astaps.loot.weekly_shouki_no_kami_level_100"));
        assertTrue(yaml.lines().noneMatch(line -> line.matches(
                "^(world|weekly)_.+_level_[0-9]+: \\*[a-z0-9_]+$")));
        assertEquals(Map.of("astaps.loot.world_frostnight_scion_level_100", 1),
                css.resolve("boss", Map.of(
                        "type", "world", "tag", "frostnight_scion", "level", "105")).loot());
        assertEquals(Map.of("astaps.loot.weekly_shouki_no_kami_level_93", 1),
                css.resolve("boss", Map.of(
                        "type", "weekly", "tag", "shouki_no_kami", "level", "120")).loot());
        assertEquals(Map.of("astaps.loot.world_cryo_regisvine_level_1", 1),
                css.resolve("boss", Map.of(
                        "type", "world", "tag", "cryo_hypostasis", "level", "1")).loot());

        var highWorld = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "105"));
        assertEquals(200, highWorld.items().get(102));
        assertEquals(6000, highWorld.items().get(202));
        assertEquals(45, highWorld.items().get(105));
        assertEquals(200, highWorld.items().get(101));
        assertEquals(1, highWorld.loot().get("astaps.loot.world_electro_hypostasis_level_93"));

        var weekly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "90"));
        assertEquals(300, weekly.items().get(102));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_stormterror_level_90"));
    }
}
