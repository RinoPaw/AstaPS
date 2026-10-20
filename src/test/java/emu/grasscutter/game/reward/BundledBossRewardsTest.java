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
    void worldBossTemplatesCoverAllMappedIdentitiesWithoutInventingWeeklyDrops() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String css = RewardConfiguration.readBundledCss();
        assertTrue(yaml.contains("world_gem_two<COUNT, ELEMENTS...>:"));
        assertTrue(yaml.contains("type: genshin.avatar_ascension"));
        assertTrue(yaml.contains("world_gem_four<COUNT, ELEMENTS...>:"));
        assertTrue(yaml.contains("world_gem_one<ELEMENTS...>:"));
        assertTrue(css.contains("astaps.loot.world_gem_one<<ELEMENT>[0], <ELEMENT>[1]>:"));
        for (int rarity = 1; rarity <= 4; rarity++) {
            String template = "world_artifact_" + rarity + "star<SETS...>:";
            assertTrue(yaml.contains(template), template);
            assertFalse(yaml.contains("world_artifact_" + rarity + "star_1<"));
            assertFalse(css.contains("world_artifact_" + rarity + "star_1<"));
        }
        assertTrue(yaml.contains("world_artifact_5star:"));
        assertTrue(yaml.contains("world_artifact_5star_plume_goblet<PLUME_SUBSTATS, GOBLET_SUBSTATS>:"));
        assertTrue(yaml.contains("world_artifact_5star_goblet<SUBSTATS>:"));
        assertTrue(css.contains("astaps.loot.world_"));
        assertFalse(css.contains("boss[type=\"weekly\"]"));
        String identities = css.substring(css.indexOf("/* 身份绑定"),
                css.indexOf("/* 通用等级奖励"));
        assertEquals(48, identities.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertTrue(css.contains("astaps.loot.world_artifact_5star:"));
        assertFalse(css.contains("world_artifact_5star<"));
        assertTrue(css.contains("world_artifact_5star_plume_goblet<0, 0>"));
        assertTrue(css.contains("world_artifact_5star_goblet<1>"));
        assertTrue(css.contains("world_artifact_5star_goblet<0>"));
        assertFalse(css.contains("world_artifact_5star_2<"));
        assertFalse(css.contains("world_artifact_5star_special<"));
        assertFalse(css.contains("world_artifact_5star_piece<"));
        assertFalse(css.contains("world_artifact_5star_plume_goblet<instructor,"));
    }

    @Test
    void bundledWorldAndWeeklyRulesLoadAndNativeFallbackIsPreserved() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String stylesheet = RewardConfiguration.readBundledCss();
        var ids = new HashMap<String, Integer>();
        var nextId = new AtomicInteger(3000000);
        var artifacts = new ArrayList<ArtifactResolver.Variant>();
        var nextArtifactId = new AtomicInteger(4000000);
        // Collect native sets from the artifact template arguments and the
        // fixed-set YAML entries. Prayers sets contain circlets only.
        var sets = new java.util.LinkedHashSet<String>();
        Pattern.compile("world_artifact_[1-4]star<([^>]+)>")
                .matcher(stylesheet).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1).split(",")))
                .map(String::trim)
                .forEach(sets::add);
        Pattern.compile("(?m)^\\s*set:\\s*(.+)$")
                .matcher(yaml).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1)
                        .replace(Character.toString(34), "").split(",")))
                .map(String::trim)
                .map(value -> value.split(":", 2)[0])
                .filter(name -> name.matches("[a-z0-9_]+"))
                .forEach(sets::add);
        sets.forEach(name -> {
            for (int rarity = 1; rarity <= 5; rarity++) {
                for (int slot = 1; slot <= 5; slot++) {
                    if (name.startsWith("prayers_") && slot != 5) continue;
                    for (int substats = 0; substats <= 4; substats++) {
                        artifacts.add(new ArtifactResolver.Variant(
                                nextArtifactId.incrementAndGet(),
                                name, slot, rarity, substats));
                    }
                }
            }
        });
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
                .map(match -> Map.entry(match.group(1), match.group(2)))
                .distinct()
                .forEach(entry -> {
                    String category = entry.getKey();
                    String name = entry.getValue();
                    String canonical = ResourceAliases.GENSHIN.resolve(
                            "genshin." + category + "." + name).toString();
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    String displayName = category.equals("avatar_ascension")
                            ? canonical.substring("genshin.avatar_ascension.".length())
                                    .replace('.', ' ').replace('_', ' ')
                            : name.replace('_', ' ');
                    known.put(id, displayName);
                    categories.put(id, switch (category) {
                        case "avatar_ascension", "weapon_crafting" -> "Character Development Items";
                        case "talent_up" -> "Talent Level-Up Materials";
                        default -> "Materials";
                    });
                });
        Pattern.compile("BOSS_MATERIAL: ([a-z0-9_]+);")
                .matcher(stylesheet).results().map(match -> match.group(1)).distinct()
                .forEach(name -> {
                    String canonical = "genshin.avatar_ascension." + name;
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    known.put(id, name.replace('_', ' '));
                    categories.put(id, "Character Development Items");
                });
        // Some Bosses expose several material variants in an array binding.
        Pattern.compile("BOSS_MATERIAL:\\s*\\[([^]]+)\\]")
                .matcher(stylesheet).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1).split(",")))
                .map(String::trim)
                .forEach(name -> {
                    String canonical = "genshin.avatar_ascension." + name;
                    int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                    known.put(id, name.replace('_', ' '));
                    categories.put(id, "Character Development Items");
                });
        // CSS uses elemental gem aliases; register all seven canonical gem families.
        for (String family : new String[]{"vayuda_turquoise", "prithiva_topaz",
                "vajrada_amethyst", "nagadus_emerald", "varunada_lazurite",
                "agnidus_agate", "shivada_jade"}) {
            for (String grade : new String[]{"sliver", "fragment", "chunk", "gemstone"}) {
                String canonical = "genshin.avatar_ascension." + family + "." + grade;
                int id = ids.computeIfAbsent(canonical, ignored -> nextId.incrementAndGet());
                known.put(id, family.replace('_', ' ') + " " + grade);
                categories.put(id, "Character Development Items");
            }
        }
        var css = CssRewards.parse(stylesheet, new ItemIdRegistry(known, categories),
                loot);
        var config = new RewardConfiguration(loot, css);
        String identityCss = stylesheet.substring(
                stylesheet.indexOf("/* 身份绑定"),
                stylesheet.indexOf("/* 通用等级奖励"));
        var bosses = identityCss.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\""))
                .map(line -> line.substring(
                        line.indexOf("[tag=\"") + 6, line.indexOf("\"] {")))
                .distinct().toList();
        assertEquals(48, bosses.size());
        for (var boss : bosses) {
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
            for (int level : new int[] {30, 35, 40, 45, 50, 55, 60, 65, 70, 75,
                    80, 85, 90, 93, 99, 100, 104}) {
                assertNotNull(config.rollWorldBoss(boss, level, new java.util.Random(71)),
                        "Unconfigured middle world Boss: " + boss + "@" + level);
            }
            // The last CSS level interval is open-ended (93~).
            assertNotNull(config.rollWorldBoss(boss, 105, new java.util.Random(71)),
                    "Lv.105+ retains the level-93-plus configured range: " + boss);
        }
        var pureMaterial = css.resolve("boss", Map.of(
                "type", "world", "tag", "precision_clockwork", "level", "1"));
        assertEquals(1, pureMaterial.rangedItems().size());
        assertTrue(pureMaterial.loot().isEmpty());
        assertTrue(pureMaterial.rangedLoot().keySet().stream()
                .allMatch(name -> name.startsWith("astaps.loot.world_")));
        assertNotNull(config.rollWorldBoss("精密发条机关", 1, new java.util.Random(71)));

        var dual = css.resolve("boss", Map.of(
                "type", "world", "tag", "maguu_kenki", "level", "45"));
        assertTrue(dual.rangedLoot().keySet().stream()
                .anyMatch(key -> key.startsWith("astaps.loot.world_")));
        var weekly = css.resolve("boss", Map.of(
                "type", "weekly", "tag", "stormterror", "level", "90"));
        assertTrue(weekly.loot().isEmpty());
        assertTrue(weekly.rangedLoot().isEmpty());
    }
}
