package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceAliases;
import emu.grasscutter.game.drop.ArtifactResolver;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Regression checks for the staged native world Boss migration. */
final class BundledBossRewardsTest {
    @Test
    void originalWorldTablesAreRemovedButSharedAndWeeklyLootRemain() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String css = RewardConfiguration.readBundled("rewards.css");
        assertFalse(Pattern.compile("(?m)^world_[a-z0-9_]+:").matcher(yaml).find());
        assertFalse(css.contains("astaps.loot.world_"));
        assertTrue(yaml.contains("weekly_northlander_extras:"));
        assertTrue(yaml.contains("gems_anemo_cryo_sliver:"));
        assertTrue(yaml.contains("gems_world_early_anemo_cryo_sliver:"));
        assertEquals(48, css.lines().filter(line ->
                line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertEquals(211, css.lines().filter(line ->
                line.startsWith("boss[type=\"weekly\"][tag=\"")).count());
        int start = css.indexOf("/* World Boss drops (ChestDrop sourceType 34)");
        assertTrue(start >= 0);
        String world = css.substring(start);
        assertFalse(world.contains("astaps.loot.artifacts_"));
        assertFalse(world.contains("genshin.artifact."));
        assertTrue(world.lines().filter(line -> line.startsWith("boss[type=\"world\"]"))
                .allMatch(line -> line.contains("[level=\"1~29\"]")));
    }

    @Test
    void allEarlyCurvesCoverSourceBracketsWithoutInterpolation() throws Exception {
        var curves = RewardCurves.parse(RewardConfiguration.readBundled("curves.yaml"));
        String[] levels = {"1", "5", "10", "15", "20", "25"};
        String[] gemCounts = {
                "3+3:47.246", "3+3:57.179", "3+3:69.587",
                "3+3:81.995", "3+3:94.403", "6+3:6.811"
        };
        String[] materialCounts = {
                "1+1:33.86", "1+1:42.89", "1+1:54.17",
                "1+1:65.45", "1+1:76.73", "1+1:88.01"
        };
        for (int i = 0; i < levels.length; i++) {
            assertEquals(gemCounts[i],
                    curves.require("world_gems_early_standard_item_count").lookup(levels[i]));
            assertEquals(materialCounts[i],
                    curves.require("world_ascension_material_early_count").lookup(levels[i]));
        }
        assertEquals("9", curves.require("world_gems_early_fixed_item_count").lookup("29"));
        assertThrows(IllegalArgumentException.class, () ->
                curves.require("world_ascension_material_early_count").lookup("30"));
    }

    @Test
    void bundledWorldAndWeeklyRulesLoadAndNativeFallbackIsPreserved() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String stylesheet = RewardConfiguration.readBundled("rewards.css");
        var ids = new HashMap<String, Integer>();
        var nextId = new AtomicInteger(3000000);
        var artifacts = new ArrayList<ArtifactResolver.Variant>();
        var nextArtifactId = new AtomicInteger(4000000);
        Pattern.compile("reward: genshin\\.artifact\\.([a-z0-9_]+)")
                .matcher(yaml).results().map(match -> match.group(1)).distinct()
                .forEach(name -> artifacts.add(new ArtifactResolver.Variant(
                        nextArtifactId.incrementAndGet(), name, 1, 5, 3)));
        var loot = YamlLootTables.parse(yaml,
                id -> ids.computeIfAbsent(ResourceAliases.GENSHIN.resolve(id).toString(),
                        ignored -> nextId.incrementAndGet()),
                new ArtifactResolver(artifacts));
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
        var categories = new HashMap<Integer, String>();
        Pattern.compile("genshin\\.(material|avatar_ascension|weapon_ascension|talent_up|weapon_crafting)\\.([a-z0-9_.]+)\\s*:")
                .matcher(stylesheet).results()
                .map(match -> "genshin." + match.group(1) + "." + match.group(2))
                .distinct()
                .forEach(reference -> {
                    String canonical = ResourceAliases.GENSHIN.resolve(reference).toString();
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    known.put(id, canonical.replace('.', ' ').replace('_', ' '));
                    categories.put(id, "Character Development Items");
                });
        var curves = RewardCurves.parse(RewardConfiguration.readBundled("curves.yaml"));
        var css = CssRewards.parse(stylesheet, new ItemIdRegistry(known, categories),
                loot, curves);
        var config = new RewardConfiguration(loot, css);
        for (var boss : new String[] {
                "electro_hypostasis", "primo_geovishap_cryo", "maguu_kenki",
                "setekh_wenut", "frostnight_scion", "icewind_suite",
                "knuckle_duck", "precision_clockwork", "tenebrous_papilla"
        }) {
            for (int level : new int[] {1, 5, 10, 15, 20, 25, 29}) {
                var attributes = Map.of(
                        "type", "world", "tag", boss, "level", Integer.toString(level));
                var selection = css.resolve("boss", attributes);
                assertTrue(!selection.rangedItems().isEmpty()
                        || !selection.rangedLoot().isEmpty(), boss + "@" + level);
                assertTrue(selection.loot().isEmpty());
                assertNotNull(config.rollWorldBoss(boss, level, new java.util.Random(71)),
                        boss + "@" + level);
            }
            assertNull(config.rollWorldBoss(boss, 36, new java.util.Random(71)),
                    "Lv.30+ must use native fallback: " + boss);
        }
        var pureMaterial = css.resolve("boss", Map.of(
                "type", "world", "tag", "precision_clockwork", "level", "1"));
        assertEquals(1, pureMaterial.rangedItems().size());
        assertTrue(pureMaterial.loot().isEmpty());
        assertTrue(pureMaterial.rangedLoot().isEmpty());
        assertNotNull(config.rollWorldBoss("精密发条机关", 1, new java.util.Random(71)));

        var dual = css.resolve("boss", Map.of(
                "type", "world", "tag", "maguu_kenki", "level", "5"));
        assertTrue(dual.rangedLoot().containsKey(
                "astaps.loot.gems_world_early_anemo_cryo_sliver"));
        var weekly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "90"));
        assertTrue(weekly.loot().containsKey("astaps.loot.weekly_northlander_extras"));
        assertTrue(loot.contains("astaps.loot.weekly_midlander_extras"));
    }
}
