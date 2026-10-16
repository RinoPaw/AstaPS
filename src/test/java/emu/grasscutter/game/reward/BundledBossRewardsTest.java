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
        assertEquals(978, stylesheet.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertEquals(211, stylesheet.lines()
                .filter(line -> line.startsWith("boss[type=\"weekly\"][tag=\"")).count());

        // Equal-weight five-slot artifact pools are inlined into their callers.
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_sliver_first_electro_roll_2plus1pct98p92_artifacts_variant_7"));
        assertFalse(loot.contains("astaps.loot.world_lightning_prism_roll_1plus1pct78p03_artifacts_variant_6"));
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

        // Reusable pools retain their content-based identities.
        assertEquals(1409, loot.names().size());
        assertTrue(loot.contains("astaps.loot.midlander_weapon_billets"));
        assertTrue(loot.contains("astaps.loot.northlander_weapon_billets"));
        assertTrue(loot.contains("astaps.loot.weekly_northlander_extras"));
        assertTrue(loot.contains("astaps.loot.weekly_midlander_extras"));
        assertTrue(loot.contains("astaps.loot.weekly_high_artifacts"));
        assertTrue(loot.contains("astaps.loot.weekly_artifacts_sparse"));
        assertTrue(loot.contains("astaps.loot.weekly_artifacts_rare"));
        assertTrue(loot.contains("astaps.loot.world_five_star_artifacts_low"));
        assertTrue(loot.contains("astaps.loot.world_five_star_artifacts_guaranteed"));
        assertTrue(loot.contains("astaps.loot.gems_geo_hydro_basic"));
        assertTrue(loot.contains("astaps.loot.gems_cryo_hydro_basic"));
        assertTrue(loot.contains("astaps.loot.instructor_goblet_plume_low_substats"));
        assertTrue(loot.contains("astaps.loot.instructor_goblet_plume_mixed_substats"));
        assertTrue(loot.contains("astaps.loot.instructor_goblet_plume_extreme_substats"));
        assertTrue(loot.contains("astaps.loot.gems_cryo_electro_hydro_mixed"));
        assertTrue(loot.contains("astaps.loot.gems_anemo_geo_pyro_mixed"));
        assertFalse(yaml.contains("_first_electro"));
        assertFalse(yaml.contains("_first_cryo"));
        assertFalse(yaml.contains("_first_anemo"));
        assertFalse(yaml.contains("_first_pyro"));
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_sliver_first_electro_empty_reward_choices_roll_5plus1pct41p2_weapon_billets"));
        assertTrue(yaml.lines().noneMatch(line -> line.matches(
                "^(world|weekly)_.+_level_[0-9]+_[a-z0-9_]+:.*")));

        // Only CSS knows Boss identity and native level; Loot knows reward content and type.
        assertTrue(yaml.lines().noneMatch(line -> line.matches(
                "^(world|weekly)_[a-z0-9_]+_level_[0-9]+:.*")));
        assertTrue(loot.names().stream().anyMatch(name ->
                name.startsWith("astaps.loot.weekly_")));
        assertTrue(loot.names().stream().anyMatch(name ->
                name.startsWith("astaps.loot.world_")));

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
        assertEquals(1, normal.loot().get("astaps.loot.world_frostnights_glory_roll_1plus1pct33p86"));

        var legacy = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "36"));
        assertEquals(1, legacy.loot().get("astaps.loot.world_lightning_prism_roll_1plus1pct60p8"));

        // Native source text tags are translated only for CSS selection.
        // The original Chinese tag still belongs to the native fallback.
        var rewardConfig = new RewardConfiguration(loot, css);
        assertEquals(6000, rewardConfig.rollWorldBoss(
                "无相之雷", 105, new java.util.Random(71)).get(202));
        assertEquals(6000, rewardConfig.rollWorldBoss(
                "electro_hypostasis", 105, new java.util.Random(71)).get(202));

        // CSS references the canonical pool directly; no duplicate Boss-level aliases.
        assertTrue(loot.contains("astaps.loot.world_frostnights_glory_roll_1plus1pct33p86"));
        assertFalse(loot.contains("astaps.loot.world_frostnights_glory_roll_1plus1pct33p8605"));
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_sliver_first_electro_empty_reward_choices_roll_5plus1pct41p200"));
        assertTrue(yaml.lines().noneMatch(line -> line.matches(
                "^(world|weekly)_.+_level_[0-9]+: \\*[a-z0-9_]+$")));
        assertEquals(Map.of(
                "astaps.loot.world_frostnights_glory_entries_6", 1,
                "astaps.loot.gems_geo_hydro_basic", 1),
                css.resolve("boss", Map.of(
                        "type", "world", "tag", "frostnight_scion", "level", "105")).loot());
        var shouki = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "shouki_no_kami", "level", "120"));
        assertEquals(1, shouki.loot().get("astaps.loot.weekly_talent_materials_puppet_strings_mirror_of_mushin_dakas_bell_roll_2plus1pct40"));
        assertEquals(1, shouki.loot().get("astaps.loot.weekly_midlander_extras"));
        assertFalse(shouki.loot().containsKey("astaps.loot.weekly_northlander_extras"));
        assertEquals(1, shouki.loot().get("astaps.loot.weekly_high_artifacts"));
        assertFalse(shouki.loot().containsKey("astaps.loot.weekly_artifacts_rare"));
        assertEquals(Map.of("astaps.loot.world_gems_anemo_sliver_roll_1plus1pct47p246", 1),
                css.resolve("boss", Map.of(
                        "type", "world", "tag", "cryo_hypostasis", "level", "1")).loot());

        var highWorld = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "105"));
        assertEquals(200, highWorld.items().get(102));
        assertEquals(6000, highWorld.items().get(202));
        assertEquals(45, highWorld.items().get(105));
        assertEquals(200, highWorld.items().get(101));
        assertEquals(1, highWorld.loot().get("astaps.loot.world_lightning_prism_artifacts_gladiators_finale_wanderers_troupe_5star_3_4substats_roll_1"));
        assertEquals(1, highWorld.loot().get("astaps.loot.world_five_star_artifacts_guaranteed"));

        var fixedGems = css.resolve("boss", Map.of(
                "type", "world", "tag", "solitary_suanni", "level", "82"));
        assertEquals(1, fixedGems.loot().get("astaps.loot.gems_cryo_hydro_basic"));
        assertEquals(1, css.resolve("boss", Map.of(
                "type", "world", "tag", "chimera_sky_roamer", "level", "82"))
                .loot().get("astaps.loot.gems_cryo_hydro_basic"));
        assertFalse(css.resolve("boss", Map.of(
                "type", "world", "tag", "solitary_suanni", "level", "0"))
                .loot().containsKey("astaps.loot.gems_cryo_hydro_basic"));

        for (var caseData : new Object[][] {
                { "7", "instructor_goblet_plume_low_substats" },
                { "17", "instructor_goblet_plume_mixed_substats" },
                { "52", "instructor_goblet_plume_extreme_substats" }}) {
            String nativeLevel = (String) caseData[0];
            String expectedPool = (String) caseData[1];
            var selection = css.resolve("boss", Map.of(
                    "type", "world", "tag", "frostnight_scion", "level", nativeLevel));
            assertEquals(1, selection.loot().get("astaps.loot." + expectedPool));
            assertEquals(1, selection.loot().get("astaps.loot.gems_geo_hydro_basic"));
        }

        var mediumWorld = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "82"));
        assertEquals(1, mediumWorld.loot().get("astaps.loot.world_five_star_artifacts_medium"));
        assertFalse(mediumWorld.loot().containsKey("astaps.loot.world_five_star_artifacts_guaranteed"));
        assertTrue(css.resolve("boss", Map.of(
                "type", "world", "tag", "frostnight_scion", "level", "82"))
                .loot().keySet().stream().noneMatch(name -> name.contains("world_five_star_artifacts")));

        var weekly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "90"));
        assertEquals(300, weekly.items().get(102));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_talent_materials_dvalins_plume_dvalins_claw_dvalins_sigh_roll_2plus1pct10"));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_northlander_extras"));
        assertFalse(weekly.loot().containsKey("astaps.loot.weekly_midlander_extras"));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_high_artifacts"));

        var sparse = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "7"));
        assertEquals(1, sparse.loot().get("astaps.loot.weekly_artifacts_sparse"));
        assertEquals(1, sparse.loot().get("astaps.loot.weekly_northlander_extras"));
        assertFalse(sparse.loot().containsKey("astaps.loot.weekly_high_artifacts"));

        var rare = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "67"));
        assertEquals(1, rare.loot().get("astaps.loot.weekly_artifacts_rare"));
        assertFalse(rare.loot().containsKey("astaps.loot.weekly_high_artifacts"));
    }
}
