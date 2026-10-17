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

/** Ensure the entire bundled CSS/YAML, including Boss tables, can load together. */
final class BundledBossRewardsTest {
    @Test
    void bundledLootTablesNeverWrapASingleReward() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        var settings = org.snakeyaml.engine.v2.api.LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(100_000)
                .setCodePointLimit(3_000_000)
                .build();
        Object document = new org.snakeyaml.engine.v2.api.Load(settings).loadFromString(yaml);
        assertInstanceOf(Map.class, document);
        for (var entry : ((Map<?, ?>) document).entrySet()) {
            if (entry.getKey() instanceof String name
                    && !name.matches("[a-z][a-z0-9_]*<[a-z][a-z0-9_]*>")
                    && entry.getValue() instanceof java.util.List<?> list) {
                assertNotEquals(1, list.size(),
                        "Redundant single-entry loot table: " + entry.getKey());
            }
        }
        // Former one-item aliases are inlined as nested mappings.
        // The inner count is the number of copies per independent roll.
        assertFalse(yaml.contains("gems_electro_chunk_fragment_sliver_odds_4945_1319_330_count_3:"));
        assertTrue(yaml.contains(
                "- reward: {reward: genshin.avatar_ascension.electro, properties: {rarity: \"2:75, 3:20, 4:5\"}, count: 3}\n    count: \"2+1:17.602\""));
        assertTrue(yaml.contains(
                "- reward: {reward: genshin.avatar_ascension.hydro, properties: {rarity: \"2:74.5, 3:20, 4:5, 5:0.5\"}, count: 9}\n    count: \"2+1:24.257\""));
        var nestedGems = Pattern.compile(
                "(?m)^  - reward: \\{reward: genshin\\.avatar_ascension\\.[a-z_]+,"
                        + " properties: \\{rarity: \"[^\"]+\"\\}(?:, count: (?:3|9))?\\}$"
                        + "\\n    count: \"[1-5]\\+1:[0-9.]+\"")
                .matcher(yaml);
        assertEquals(150, nestedGems.results().count(),
                "Nested gem groups must keep their independent rarity rolls");
        assertFalse(Pattern.compile(
                "reward: genshin\\.avatar_ascension\\.[a-z_]+,"
                        + " properties: \\{rarity: \"[^\"]+\"\\}, count: \"[1-9][0-9]*\\+[1-9][0-9]*:")
                .matcher(yaml).find(), "Do not flatten independent rarity rolls");
    }

    @Test
    void bundledGemAndTalentRarityDistributionsUseMinimalPrecision() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        var matcher = Pattern.compile(
                "reward: genshin\\.(avatar_ascension|talent_up)\\.[a-z_]+,"
                        + " properties: \\{rarity: \"([^\"]+)\"\\}")
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
                assertTrue(pair[1].matches("[0-9]+(?:\\.[0-9]?[1-9])?"), part);
                total = total.add(new java.math.BigDecimal(pair[1]));
            }
            assertEquals(0, total.compareTo(new java.math.BigDecimal("100.00")));
        }
        assertEquals(621, gems, "All non-duplicated gem-family rewards must be validated");
        assertEquals(0, talents, "Guaranteed rarity selections use integer grades");
        assertEquals(6, Pattern.compile(
                "reward: genshin\\.talent_up\\.[a-z_]+, properties: \\{rarity: [234]\\}")
                .matcher(yaml).results().count());
        assertFalse(yaml.contains("rarity: \"2:100\""));
        assertFalse(yaml.contains("rarity: \"3:100\""));
        // The old per-grade weighted rows with count 3 or 9 must be gone
        // whenever multiple rarities could be combined.
        assertFalse(Pattern.compile(
                "reward: genshin\\.avatar_ascension\\.[a-z_]+\\.[2-5], count: \"[39]\", weight: [1-9][0-9]+")
                .matcher(yaml).find());
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
                id -> nativeIds.computeIfAbsent(
                        ResourceAliases.GENSHIN.resolve(id).toString(),
                        ignored -> nextId.incrementAndGet()),
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
                    var canonical = ResourceAliases.GENSHIN.resolve(reference).toString();
                    int id = nativeIds.computeIfAbsent(canonical,
                            ignored -> nextId.incrementAndGet());
                    var displayName = category.equals("avatar_ascension")
                            ? canonical.substring("genshin.avatar_ascension.".length())
                                    .replace('.', ' ').replace('_', ' ')
                            : name.replace('_', ' ');
                    known.put(id, displayName);
                    knownCategories.put(id, switch (category) {
                        case "avatar_ascension", "weapon_crafting" -> "Character Development Items";
                        case "talent_up" -> "Talent Level-Up Materials";
                        default -> "Materials";
                    });
                });
        var curves = RewardCurves.parse(RewardConfiguration.readBundled("curves.yaml"));
        var css = CssRewards.parse(stylesheet, new ItemIdRegistry(known, knownCategories),
                loot, curves);
        assertEquals("1+1:86.64", curves.require("marionette_core").lookup("52"));
        assertEquals("2+1:3.87", curves.require("marionette_core").lookup("63"));
        assertEquals("2+1:55.56", curves.require("marionette_core").lookup("130"));
        assertThrows(IllegalArgumentException.class,
                () -> curves.require("marionette_core").lookup("29"));
        assertEquals("4+1:21", curves.require("weekly_gems_4plus1").lookup("70"));
        assertEquals("4+1:45.71", curves.require("weekly_gems_4plus1").lookup("77"));
        assertEquals("4+1:68.39", curves.require("weekly_gems_4plus1").lookup("82"));
        assertEquals("4+1:91.01", curves.require("weekly_gems_4plus1").lookup("89"));
        assertThrows(IllegalArgumentException.class,
                () -> curves.require("weekly_gems_4plus1").lookup("90"));
        assertEquals(27, stylesheet.lines().filter(line ->
                line.contains("weekly_gems_4plus1(level)")).count());
        assertEquals(14, yaml.lines().filter(line ->
                line.matches("^weekly_gems_[a-z0-9_]+_rarity_2to[45]_first_[a-z]+:$")).count());
        assertFalse(loot.contains(
                "astaps.loot.weekly_gems_anemo_geo_pyro_fragment_4plus1pct45p71_first_anemo"));
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
        assertEquals(612, loot.names().size()); // Weekly 4+1 groups also share weighted rarity pools.
        assertEquals(374, yaml.lines().filter(line -> line.matches(
                "^world_[a-z0-9_]+_(material|gem|artifact):$")).count(),
                "Shared world pools must contain only one reward category");
        assertTrue(yaml.lines().noneMatch(line ->
                line.matches("^world_marionette_core_shivada_jade_sliver_weight_4193:$")));
        assertTrue(stylesheet.contains(
                "genshin.avatar_ascension.marionette_core: \"1+1:86.64\";"));
        assertEquals(21, yaml.lines().filter(line -> line.contains(
                "reward: genshin.avatar_ascension, properties: {element: \"electro:33.33, cryo:33.33, hydro:33.34\", rarity: 3}")).count());
        assertFalse(yaml.contains("gems_cryo_electro_hydro_fragment: &gems_cryo_electro_hydro_fragment"));
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
        assertTrue(normal.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.frostnights_glory")));
        assertEquals(1, normal.loot().get(
                "astaps.loot.world_frostnights_glory_roll_1plus1pct33p86_artifact"));

        var legacy = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "36"));
        assertTrue(legacy.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.lightning_prism")));
        assertTrue(legacy.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.vajrada_amethyst.sliver")));
        assertEquals(1, legacy.loot().get(
                "astaps.loot.world_lightning_prism_roll_1plus1pct60p8_artifact"));

        // Native source text tags are translated only for CSS selection.
        // The original Chinese tag still belongs to the native fallback.
        var rewardConfig = new RewardConfiguration(loot, css);
        assertEquals(6000, rewardConfig.rollWorldBoss(
                "无相之雷", 105, new java.util.Random(71)).get(202));
        assertEquals(6000, rewardConfig.rollWorldBoss(
                "electro_hypostasis", 105, new java.util.Random(71)).get(202));

        // CSS owns the boss material and selects reusable, category-only pools.
        assertTrue(loot.contains("astaps.loot.world_frostnights_glory_roll_1plus1pct33p86_artifact"));
        assertFalse(loot.contains("astaps.loot.world_frostnights_glory_roll_1plus1pct33p86"));
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_sliver_first_electro_empty_reward_choices_roll_5plus1pct41p200"));
        assertTrue(yaml.lines().noneMatch(line -> line.matches(
                "^(world|weekly)_.+_level_[0-9]+: \\*[a-z0-9_]+$")));
        var frostnight105 = css.resolve("boss", Map.of(
                "type", "world", "tag", "frostnight_scion", "level", "105"));
        assertEquals(Map.of("astaps.loot.gems_geo_hydro_basic", 1), frostnight105.loot());
        assertTrue(frostnight105.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.frostnights_glory")));
        assertTrue(frostnight105.rangedLoot().containsKey(
                "astaps.loot.artifacts_instructor_5star_3_4substats_goblet_plume"));
        // Maguu Kenki 50–54 has three separately selected reward categories.
        var maguu = css.resolve("boss", Map.of(
                "type", "world", "tag", "maguu_kenki", "level", "52"));
        assertTrue(maguu.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.marionette_core")));
        assertEquals(1, maguu.loot().get(
                "astaps.loot.world_marionette_core_shivada_jade_sliver_weight_4193_gem"));
        assertEquals(1, maguu.loot().get(
                "astaps.loot.world_hurricane_seed_shivada_jade_sliver_weight_3817_artifact"));
        assertFalse(loot.contains(
                "astaps.loot.world_marionette_core_shivada_jade_sliver_weight_4193"));
        var maguuMaterials = RewardRoller.roll(maguu, loot, new java.util.Random(4193));
        assertTrue(maguuMaterials.getOrDefault(
                nativeIds.get("genshin.avatar_ascension.marionette_core"), 0) >= 1);

        // Andrius 75–84 uses the same weighted rarity pool with an exact level curve.
        var andrius75 = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "andrius", "level", "77"));
        var andrius80 = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "andrius", "level", "82"));
        String anemoGeoPyro = "astaps.loot.weekly_gems_anemo_geo_pyro_fragment_rarity_2to5_first_anemo";
        String fragmentPool = "astaps.loot.gems_anemo_geo_pyro_fragment";
        assertTrue(andrius75.rangedLoot().containsKey(anemoGeoPyro));
        assertTrue(andrius80.rangedLoot().containsKey(anemoGeoPyro));
        assertTrue(andrius75.rangedLoot().containsKey(fragmentPool));
        assertEquals(4, andrius75.rangedLoot().get(anemoGeoPyro).sample(
                new java.util.Random() { @Override public double nextDouble() { return 0.99; } }));
        assertEquals(5, andrius75.rangedLoot().get(anemoGeoPyro).sample(
                new java.util.Random() { @Override public double nextDouble() { return 0.0; } }));
        assertEquals(5, andrius80.rangedLoot().get(anemoGeoPyro).sample(
                new java.util.Random() { @Override public double nextDouble() { return 0.5; } }));
        assertEquals(4, andrius75.rangedLoot().get(anemoGeoPyro).sample(
                new java.util.Random() { @Override public double nextDouble() { return 0.7; } }));
        assertFalse(andrius75.loot().containsKey(anemoGeoPyro));
        assertFalse(andrius80.loot().containsKey(anemoGeoPyro));

        // Tartaglia 75–79 selects talent materials independently of gem rolls.
        var tartaglia75 = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "tartaglia", "level", "76"));
        assertEquals(1, tartaglia75.loot().get("astaps.loot.weekly_gems_anemo_electro_hydro_vajrada_amethyst_chunk_weight_738"));
        assertTrue(tartaglia75.rangedLoot().containsKey("astaps.loot.talent_materials_tusk_of_monoceros_caeli_shard_of_a_foul_legacy_shadow_of_the_warrior"));
        assertFalse(loot.contains("astaps.loot.weekly_talent_materials_tusk_of_vajrada_amethyst_chunk_weight_738"));
        int tartagliaPoolStart = yaml.indexOf("weekly_gems_anemo_electro_hydro_vajrada_amethyst_chunk_weight_738:");
        int tartagliaPoolEnd = yaml.indexOf("\nweekly_", tartagliaPoolStart + 1);
        assertTrue(tartagliaPoolStart >= 0 && tartagliaPoolEnd > tartagliaPoolStart);
        assertFalse(yaml.substring(tartagliaPoolStart, tartagliaPoolEnd)
                .contains("talent_materials_tusk_of_monoceros_caeli"));
        int tuskId = nativeIds.get("genshin.talent_up.tusk_of_monoceros_caeli");
        int shardId = nativeIds.get("genshin.talent_up.shard_of_a_foul_legacy");
        int shadowId = nativeIds.get("genshin.talent_up.shadow_of_the_warrior");
        var tartagliaRandom = new java.util.Random(76);
        int bonusTalents = 0;
        for (int i = 0; i < 1_000; i++) {
            var rolled = RewardRoller.roll(tartaglia75, loot, tartagliaRandom);
            int talentCount = rolled.getOrDefault(tuskId, 0)
                    + rolled.getOrDefault(shardId, 0)
                    + rolled.getOrDefault(shadowId, 0);
            assertTrue(talentCount == 1 || talentCount == 2);
            if (talentCount == 2) bonusTalents++;
        }
        assertEquals(275, bonusTalents, 55);

        // Weekly talent rewards are independently rolled from CSS; gems remain in YAML.
        var shoukiLate = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "shouki_no_kami", "level", "87"));
        assertEquals(1, shoukiLate.loot().get("astaps.loot.weekly_gems_cryo_electro_hydro_fragment_4plus1pct91p01_first_electro"));
        var lateTalent = shoukiLate.rangedLoot().get("astaps.loot.talent_materials_puppet_strings_mirror_of_mushin_dakas_bell");
        assertNotNull(lateTalent);
        var lateRandom = new java.util.Random(814);
        int extraTalents = 0;
        for (int i = 0; i < 1000; i++) {
            int count = lateTalent.sample(lateRandom);
            assertTrue(count == 1 || count == 2);
            extraTalents += count - 1;
        }
        assertEquals(825, extraTalents, 45);
        assertEquals(0, yaml.lines().filter(line -> line.startsWith("weekly_talent_materials_")).count());
        assertEquals(108, yaml.lines().filter(line -> line.startsWith("weekly_gems_")
                && line.endsWith(":")).count());
        assertEquals(61, stylesheet.lines().filter(line ->
                line.trim().startsWith("astaps.loot.talent_materials_")).count());

        // The integer template parameter controls the independent fifth gem roll.
        assertTrue(yaml.contains("weekly_gems_cryo_electro_hydro_rarity_roll<chance>:"));
        assertTrue(yaml.contains("reward: astaps.loot.weekly_gems_cryo_electro_hydro_rarity_roll<21>"));
        assertFalse(yaml.contains("20.93"));
        assertFalse(stylesheet.contains("20p93"));
        assertFalse(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_rarity_roll<chance>"));
        assertTrue(loot.contains("astaps.loot.weekly_gems_cryo_electro_hydro_fragment_4plus1pct21_first_electro"));
        var gemRandom = new java.util.Random(21);
        int totalGems = 0;
        for (int i = 0; i < 10_000; i++) {
            var bundle = loot.roll("astaps.loot.weekly_gems_cryo_electro_hydro_fragment_4plus1pct21_first_electro", gemRandom);
            int count = bundle.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(count >= 5 && count <= 7);
            totalGems += count;
        }
        assertEquals(5.23, totalGems / 10_000.0, 0.035);

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
        assertEquals(1, shouki.loot().get("astaps.loot.weekly_gems_cryo_electro_hydro_fragment_5plus1pct13p72"));
        assertEquals(1, shouki.loot().get("astaps.loot.weekly_midlander_extras"));
        assertFalse(shouki.loot().containsKey("astaps.loot.weekly_northlander_extras"));
        assertEquals(1, shouki.loot().get("astaps.loot.weekly_high_artifacts"));
        assertFalse(shouki.loot().containsKey("astaps.loot.weekly_artifacts_rare"));
        var cryoHypostasis = css.resolve("boss", Map.of(
                "type", "world", "tag", "cryo_hypostasis", "level", "1"));
        assertTrue(cryoHypostasis.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.vayuda_turquoise.sliver")));
        assertEquals(1, cryoHypostasis.loot().get(
                "astaps.loot.world_gems_cryo_sliver_artifacts_lucky_dog_2star_0_1substats_roll_1pct42_artifact"));

        var highWorld = css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis", "level", "105"));
        assertEquals(200, highWorld.items().get(102));
        assertEquals(6000, highWorld.items().get(202));
        assertEquals(45, highWorld.items().get(105));
        assertEquals(200, highWorld.items().get(101));
        assertTrue(highWorld.rangedItems().containsKey(
                nativeIds.get("genshin.avatar_ascension.lightning_prism")));
        assertEquals(1, highWorld.loot().get(
                "astaps.loot.world_lightning_prism_artifacts_gladiators_finale_wanderers_troupe_5star_3_4substats_roll_1pct63_gem"));
        assertEquals(1, highWorld.loot().get(
                "astaps.loot.world_lightning_prism_artifacts_gladiators_finale_wanderers_troupe_5star_3_4substats_roll_1pct63_artifact"));
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
        assertEquals(1, weekly.loot().get("astaps.loot.weekly_gems_cryo_electro_hydro_fragment_5plus1pct13p72"));
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
