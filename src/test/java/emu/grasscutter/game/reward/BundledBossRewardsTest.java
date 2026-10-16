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
    void lootTablesNeverContainSingleEntryLists() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        var settings = org.snakeyaml.engine.v2.api.LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(100_000)
                .setCodePointLimit(3_000_000)
                .build();
        Object document = new org.snakeyaml.engine.v2.api.Load(settings).loadFromString(yaml);
        assertInstanceOf(Map.class, document);
        for (var entry : ((Map<?, ?>) document).entrySet()) {
            if (entry.getValue() instanceof java.util.List<?> list) {
                assertNotEquals(1, list.size(),
                        "Single-entry loot tables are forbidden: " + entry.getKey());
            }
        }
    }

    @Test
    void bundledGemAndTalentRarityDistributionsUseTwoDecimalPercentages() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        var matcher = Pattern.compile(
                "reward: genshin\\.(avatar_ascension|talent_up)\\.[a-z_]+,"
                        + " properties: \\{rarity: \"([^\"]+)\"\\}, weight: 1")
                .matcher(yaml);
        int gems = 0, talents = 0;
        while (matcher.find()) {
            if ("avatar_ascension".equals(matcher.group(1))) gems++;
            else talents++;
            var total = java.math.BigDecimal.ZERO;
            for (String part : matcher.group(2).split(", ")) {
                var pair = part.split(":");
                assertEquals(2, pair.length);
                assertTrue(pair[0].matches("[2-5]"));
                assertTrue(pair[1].matches("[0-9]+\\.[0-9]{2}"), part);
                total = total.add(new java.math.BigDecimal(pair[1]));
            }
            assertEquals(0, total.compareTo(new java.math.BigDecimal("100.00")));
        }
        assertEquals(291, gems);
        assertEquals(6, talents);
        assertFalse(yaml.contains("genshin.talent_up.freedom.teachings, weight:"));
        assertFalse(yaml.contains("genshin.avatar_ascension.vajrada_amethyst.sliver, weight:"));
    }

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
        // Use the same synthetic IDs for material entries now selected directly by CSS.
        var knownCategories = new HashMap<Integer, String>();
        Pattern.compile("genshin\\.(material|avatar_ascension|weapon_ascension|talent_up|weapon_crafting)\\.([a-z0-9_.]+)\\s*:")
                .matcher(stylesheet).results()
                .map(match -> Map.entry(match.group(1), match.group(2))).distinct()
                .forEach(entry -> {
                    String category = entry.getKey();
                    String name = entry.getValue();
                    String reference = "genshin." + category + "." + name;
                    int id = nativeIds.computeIfAbsent(reference,
                            ignored -> nextId.incrementAndGet());
                    known.put(id, name.replace('_', ' '));
                    knownCategories.put(id, switch (category) {
                        case "avatar_ascension", "weapon_crafting" -> "Character Development Items";
                        case "talent_up" -> "Talent Level-Up Materials";
                        default -> "Materials";
                    });
                });
        var css = CssRewards.parse(stylesheet, new ItemIdRegistry(known, knownCategories), loot);
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
        assertEquals(1176, loot.names().size());
        assertFalse(yaml.contains("empty_reward_choices"));
        assertTrue(loot.contains("astaps.loot.midlander_weapon_billets"));
        assertTrue(loot.contains("astaps.loot.northlander_weapon_billets"));
        assertTrue(loot.contains("astaps.loot.weekly_northlander_extras"));
        assertTrue(loot.contains("astaps.loot.weekly_midlander_extras"));
        assertTrue(loot.contains("astaps.loot.weekly_high_artifacts"));
        assertTrue(loot.contains("astaps.loot.artifacts_berserker_instructor_exile_3star"));
        assertTrue(loot.contains("astaps.loot.weekly_artifacts_rare"));
        assertTrue(loot.contains("astaps.loot.artifacts_gladiators_wanderers_5star"));
        assertFalse(loot.contains("astaps.loot.material_frostnights_glory"));
        assertTrue(loot.contains("astaps.loot.gems_geo_hydro_basic"));
        assertTrue(loot.contains("astaps.loot.gems_cryo_hydro_basic"));
        assertTrue(loot.contains("astaps.loot.instructor_goblet_plume_low_substats"));
        assertTrue(loot.contains("astaps.loot.instructor_goblet_plume_mixed_substats"));
        assertTrue(loot.contains("astaps.loot.instructor_goblet_plume_extreme_substats"));
        assertTrue(loot.contains("astaps.loot.gems_cryo_electro_hydro_mixed"));
        assertTrue(loot.contains("astaps.loot.gems_anemo_geo_pyro_mixed"));
        assertFalse(yaml.contains("gems_cryo_electro_hydro_sliver_uniform_first_electro"));
        assertFalse(yaml.contains("gems_cryo_electro_hydro_sliver_uniform_first_cryo"));
        assertFalse(yaml.contains("gems_anemo_geo_pyro_sliver_uniform_first_anemo"));
        assertFalse(yaml.contains("gems_anemo_geo_pyro_sliver_uniform_first_pyro"));
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_sliver_first_electro_empty_reward_choices_roll_5plus1pct41p2_weapon_billets"));
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_sliver_first_electro_roll_2plus1pct0p79"));
        assertTrue(loot.contains("astaps.loot.gems_cryo_electro_hydro_sliver"));
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
        var shoukiEarly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "shouki_no_kami", "level", "8"));
        assertTrue(shoukiEarly.rangedLoot().containsKey(
                "astaps.loot.gems_cryo_electro_hydro_sliver"));
        assertFalse(shoukiEarly.loot().keySet().stream().anyMatch(name ->
                name.contains("first_electro_roll_")));
        assertTrue(stylesheet.contains(
                "astaps.loot.gems_cryo_electro_hydro_sliver: '2+1:14.32';"));
        var raidenEarly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "raiden_shogun", "level", "8"));
        assertTrue(raidenEarly.rangedItems().size() > 0);
        assertTrue(stylesheet.contains(
                "genshin.avatar_ascension.vajrada_amethyst.sliver: '2+1:14.32';"));
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
        assertEquals(1, highWorld.loot().get("astaps.loot.artifacts_gladiators_wanderers_5star"));

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
        assertTrue(mediumWorld.rangedLoot().containsKey("astaps.loot.artifacts_gladiators_wanderers_5star"));
        assertFalse(mediumWorld.loot().containsKey("astaps.loot.artifacts_gladiators_wanderers_5star"));
        assertTrue(css.resolve("boss", Map.of(
                "type", "world", "tag", "frostnight_scion", "level", "82"))
                .rangedLoot().containsKey("astaps.loot.artifacts_gladiators_wanderers_5star"));

        var weekly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "90"));
        assertEquals(300, weekly.items().get(102));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_talent_materials_dvalins_plume_dvalins_claw_dvalins_sigh_roll_2plus1pct10"));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_northlander_extras"));
        assertFalse(weekly.loot().containsKey("astaps.loot.weekly_midlander_extras"));
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_high_artifacts"));

        var sparse = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "7"));
        assertTrue(sparse.rangedLoot().containsKey("astaps.loot.artifacts_berserker_instructor_exile_3star"));
        assertEquals(1, sparse.loot().get("astaps.loot.weekly_northlander_extras"));
        assertFalse(sparse.loot().containsKey("astaps.loot.weekly_high_artifacts"));

        var rare = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "67"));
        assertEquals(1, rare.loot().get("astaps.loot.weekly_artifacts_rare"));
        assertFalse(rare.loot().containsKey("astaps.loot.weekly_high_artifacts"));
    }
}
