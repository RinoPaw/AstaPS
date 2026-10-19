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
        String css = RewardConfiguration.readBundled("rewards.css");
        assertTrue(yaml.contains("world_40-44_gem_1<element>:"));
        assertTrue(yaml.contains("world_40-44_gem_2<element1, element2>:"));
        assertTrue(yaml.contains("world_artifact_5star_special<set1, plume_substats, goblet_substats>:"));
        assertTrue(css.contains("astaps.loot.world_"));
        assertFalse(css.contains("boss[type=\"weekly\"]"));
        String identities = css.substring(css.indexOf("/* Boss identities:"),
                css.indexOf("/* These 25 Boss identities"));
        assertEquals(48, identities.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\"")).count());
        assertTrue(css.contains("world_artifact_5star_1<"));
    }

    @Test
    void bundledWorldAndWeeklyRulesLoadAndNativeFallbackIsPreserved() throws Exception {
        String yaml = RewardConfiguration.readBundled("loot.yaml");
        String stylesheet = RewardConfiguration.readBundled("rewards.css");
        var ids = new HashMap<String, Integer>();
        var nextId = new AtomicInteger(3000000);
        var artifacts = new ArrayList<ArtifactResolver.Variant>();
        var nextArtifactId = new AtomicInteger(4000000);
        // Artifact template definitions carry set placeholders. Resolve actual set names
        // from the CSS invocations and cover every possible native slot/rarity/substat.
        Pattern.compile("world_artifact_[a-z0-9_-]+<([^>]+)>")
                .matcher(stylesheet).results()
                .flatMap(match -> java.util.Arrays.stream(match.group(1).split(",")))
                .map(String::trim)
                .filter(name -> !name.matches("[0-9]+"))
                .distinct().forEach(name -> {
                    for (int rarity = 1; rarity <= 5; rarity++) {
                        for (int slot = 1; slot <= 5; slot++) {
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
        var css = CssRewards.parse(stylesheet, new ItemIdRegistry(known, categories),
                loot);
        var config = new RewardConfiguration(loot, css);
        String identityCss = stylesheet.substring(
                stylesheet.indexOf("/* Boss identities:"),
                stylesheet.indexOf("/* These 25 Boss identities"));
        var bosses = identityCss.lines()
                .filter(line -> line.startsWith("boss[type=\"world\"][tag=\""))
                .map(line -> line.substring(
                        line.indexOf("[tag=\"") + 6, line.indexOf("\\"] {")))
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
            assertNull(config.rollWorldBoss(boss, 105, new java.util.Random(71)),
                    "Lv.105+ must retain the native fallback: " + boss);
        }
        var pureMaterial = css.resolve("boss", Map.of(
                "type", "world", "tag", "precision_clockwork", "level", "1"));
        assertEquals(1, pureMaterial.rangedItems().size());
        assertTrue(pureMaterial.loot().isEmpty());
        assertTrue(pureMaterial.rangedLoot().isEmpty());
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
