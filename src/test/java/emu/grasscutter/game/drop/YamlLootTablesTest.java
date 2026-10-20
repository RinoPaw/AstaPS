package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.ItemIdRegistry;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class YamlLootTablesTest {
    private final YamlLootTables.ItemResolver resolver = name -> switch (name) {
        case "genshin.currency.mora" -> 202;
        case "genshin.avatar_up.heros_wit" -> 104003;
        case "genshin.avatar_up.adventurers_experience" -> 104002;
        default -> -2; // -1 is reserved for the regional-sigil placeholder.
    };

    private YamlLootTables parse(String yaml) {
        return YamlLootTables.parse(yaml, resolver);
    }

    @Test
    void artifactRarityPoolsPreserveMembersAndComputeSubstats() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        String[] sets = {
                "instructor", "the_exile", "prayers_for_wisdom",
                "traveling_doctor", "gladiators_finale", "wanderers_troupe"
        };
        for (int set = 0; set < sets.length; set++) {
            for (int grade = 3; grade <= 4; grade++) {
                if ((grade == 3 && set >= 4) || (grade == 4 && set == 3)) continue;
                for (int slot = 1; slot <= 5; slot++) {
                    if (set == 2 && slot != 5) continue;
                    for (int sub = grade - 2; sub <= grade - 1; sub++) {
                        variants.add(new ArtifactResolver.Variant(
                                1000000 + set * 10000 + grade * 1000 + slot * 10 + sub,
                                sets[set], slot, grade, sub));
                    }
                }
            }
        }
        var yaml = """
                artifacts_prayers_wisdom_3star: &artifacts_prayers_wisdom_3star
                  - {reward: genshin.artifact.instructor, properties: {slot: "1~5", rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: "1~5", rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                  - {reward: genshin.artifact.prayers_for_wisdom, properties: {slot: circlet, rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                  - {reward: genshin.artifact.traveling_doctor, properties: {slot: "1~5", rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                artifacts_prayers_wisdom_4star: &artifacts_prayers_wisdom_4star
                  - {reward: genshin.artifact.instructor, properties: {slot: "1~5", rarity: 4, substats: "2:66, 3:34"}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: "1~5", rarity: 4, substats: "2:66, 3:34"}, weight: 1}
                  - {reward: genshin.artifact.prayers_for_wisdom, properties: {slot: circlet, rarity: 4, substats: "2:66, 3:34"}, weight: 1}
                  - {reward: genshin.artifact.gladiators_finale, properties: {slot: "1~5", rarity: 4, substats: "2:66, 3:34"}, weight: 1}
                  - {reward: genshin.artifact.wanderers_troupe, properties: {slot: "1~5", rarity: 4, substats: "2:66, 3:34"}, weight: 1}
                boss3:
                  - {reward: *artifacts_prayers_wisdom_3star, count: 2}
                boss4:
                  - {reward: *artifacts_prayers_wisdom_4star, count: 2}
                """;
        var loot = YamlLootTables.parse(yaml, resolver, new ArtifactResolver(variants));
        assertEquals(4, loot.names().size());
        for (int grade = 3; grade <= 4; grade++) {
            int[] chosenSets = new int[sets.length];
            int low = 0;
            var random = new Random(grade);
            for (int n = 0; n < 8_000; n++) {
                var output = loot.roll("astaps.loot.boss" + grade, random);
                assertEquals(2, output.values().stream().mapToInt(Integer::intValue).sum());
                for (var entry : output.entrySet()) {
                    int id = entry.getKey() - 1000000;
                    int set = id / 10000;
                    int rarity = (id % 10000) / 1000;
                    int slot = (id % 1000) / 10;
                    int sub = id % 10;
                    assertEquals(grade, rarity);
                    if (set == 2) assertEquals(5, slot);
                    assertTrue(sub == grade - 2 || sub == grade - 1);
                    chosenSets[set] += entry.getValue();
                    if (sub == grade - 2) low += entry.getValue();
                }
            }
            assertEquals(0.66, low / 16_000.0, 0.025);
            for (int set = 0; set < sets.length; set++) {
                boolean included = set <= 2 || (grade == 3 ? set == 3 : set >= 4);
                assertEquals(included ? 1.0 / (grade == 3 ? 4 : 5) : 0,
                        chosenSets[set] / 16_000.0, 0.025);
            }
        }
    }

    @Test
    void lootTemplateExpressionsRejectUnsupportedSyntaxAndCycles() {
        String template = """
                item<rarity>:
                  - {reward: genshin.currency.mora, count: "%s"}
                boss:
                  - {reward: astaps.loot.item<3>}
                """;
        for (String bad : java.util.List.of("${unknown}", "${rarity * 2}",
                "${rarity - }", "${rarity + 2147483647}", "${rarity")) {
            assertThrows(IllegalArgumentException.class, () -> parse(template.formatted(bad)), bad);
        }
        assertThrows(IllegalArgumentException.class,
                () -> parse(template.formatted("${rarity + 1}").replace("item<3>", "item<2147483648>")));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                recursive<grade>:
                  - {reward: astaps.loot.recursive<3>}
                boss:
                  - {reward: astaps.loot.recursive<3>}
                """));
        // Template bodies must be lists, not special common/variants mappings.
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid<chance>:
                  common:
                    - {reward: genshin.currency.mora, count: "${chance}"}
                  variants: {21: []}
                boss:
                  - {reward: astaps.loot.invalid<21>}
                """));
    }

    @Test
    void lootTemplateExpansionPreservesYamlAnchorIdentity() {
        var loot = parse("""
                choices: &choices
                  - {reward: genshin.avatar_up.heros_wit, weight: 1}
                  - {reward: genshin.avatar_up.adventurers_experience, weight: 1}
                award<rarity>:
                  - {reward: *choices, count: "${rarity - 2}"}
                boss_a:
                  - {reward: astaps.loot.award<3>}
                boss_b:
                  - {reward: astaps.loot.award<3>}
                """);
        assertEquals(3, loot.names().size());
        assertEquals(1, loot.roll("astaps.loot.boss_a", new Random(1))
                .values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, loot.roll("astaps.loot.boss_b", new Random(2))
                .values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void canonicalTalentBookPathsAwardMatchingNativeItems() {
        var books = new ItemIdRegistry(Map.of(
                7201, "Teachings of Freedom",
                7202, "Guide to Freedom",
                7203, "Philosophies of Freedom"));
        var loot = YamlLootTables.parse("""
                freedom:
                  - {reward: genshin.talent_up.freedom.teachings}
                  - {reward: genshin.talent_up.freedom.guide}
                  - {reward: genshin.talent_up.freedom.philosophies}
                """, books::resolve);
        assertEquals(Map.of(7201, 1, 7202, 1, 7203, 1),
                loot.roll("astaps.loot.freedom", new Random(7)));
        assertThrows(IllegalArgumentException.class, () ->
                YamlLootTables.parse("""
                        legacy:
                          - {reward: genshin.talent_up.guide_to_freedom}
                        """, books::resolve));
    }

    @Test
    void nestedMappingPreservesIndependentGemRollsFromRemovedSingletonTable() {
        var items = new ItemIdRegistry(Map.of(
                8001, "Vayuda Turquoise Sliver",
                8002, "Vayuda Turquoise Fragment",
                8003, "Vayuda Turquoise Chunk"));
        var former = YamlLootTables.parse("""
                gems: &gems
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:75, 3:20, 4:5"}, count: 3, weight: 1}
                boss:
                  - {reward: *gems, count: "2+1:17.602"}
                """, items::resolve);
        var inlined = YamlLootTables.parse("""
                boss:
                  - reward: {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:75, 3:20, 4:5"}, count: 3}
                    count: "2+1:17.602"
                """, items::resolve);
        var flattened = YamlLootTables.parse("""
                boss:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:75, 3:20, 4:5"}, count: "6+3:17.602"}
                """, items::resolve);

        var originalRandom = new Random(81);
        var inlinedRandom = new Random(91);
        var flatRandom = new Random(101);
        long originalTotal = 0, inlinedTotal = 0;
        int originalMixed = 0, inlinedMixed = 0;
        for (int i = 0; i < 20_000; i++) {
            var original = former.roll("astaps.loot.boss", originalRandom);
            var compact = inlined.roll("astaps.loot.boss", inlinedRandom);
            var flat = flattened.roll("astaps.loot.boss", flatRandom);
            int originalCount = original.values().stream().mapToInt(Integer::intValue).sum();
            int compactCount = compact.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(originalCount == 6 || originalCount == 9);
            assertTrue(compactCount == 6 || compactCount == 9);
            assertEquals(1, flat.size(), "A direct count makes copies of one chosen grade");
            originalTotal += originalCount;
            inlinedTotal += compactCount;
            if (original.size() > 1) originalMixed++;
            if (compact.size() > 1) inlinedMixed++;
        }
        assertEquals(originalTotal / 20_000.0, inlinedTotal / 20_000.0, 0.05);
        assertEquals(6 + 3 * 0.17602, inlinedTotal / 20_000.0, 0.06);
        assertTrue(originalMixed > 5000, "Former nested pool chooses multiple grades");
        assertTrue(inlinedMixed > 5000, "Nested mapping must reroll each execution");
        assertEquals(originalMixed / 20_000.0, inlinedMixed / 20_000.0, 0.02);
    }

    @Test
    void directCountAwardsCopiesWhileNestedCountRerollsItemIdentity() {
        var books = new ItemIdRegistry(Map.of(
                8101, "Teachings of Freedom", 8102, "Guide to Freedom"));
        var loot = YamlLootTables.parse("""
                copies:
                  - {reward: genshin.talent_up.freedom, properties: {rarity: "2:50, 3:50"}, count: 2}
                rerolls:
                  - reward: {reward: genshin.talent_up.freedom, properties: {rarity: "2:50, 3:50"}}
                    count: 2
                """, books::resolve);

        var random = new Random(82);
        boolean split = false;
        for (int i = 0; i < 100; i++) {
            var copies = loot.roll("astaps.loot.copies", random);
            assertEquals(1, copies.size());
            assertEquals(2, copies.values().iterator().next());
            var rerolls = loot.roll("astaps.loot.rerolls", random);
            assertEquals(2, rerolls.values().stream().mapToInt(Integer::intValue).sum());
            if (rerolls.size() == 2) split = true;
        }
        assertTrue(split, "Rerolled entries must allow different rarities");
    }

    @Test
    void numericGradeAliasesWorkInsideLootYaml() {
        var registry = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Gemstone",
                8201, "Teachings of Freedom",
                8202, "Philosophies of Freedom"));
        var tables = YamlLootTables.parse("""
                numbered:
                  - {reward: genshin.avatar_ascension.anemo.2}
                  - {reward: genshin.avatar_ascension.vayuda_turquoise.5}
                  - {reward: genshin.talent_up.freedom.2}
                  - {reward: genshin.talent_up.freedom.4}
                """, registry::resolve);
        assertEquals(Map.of(8101, 1, 8102, 1, 8201, 1, 8202, 1),
                tables.roll("astaps.loot.numbered", new Random(17)));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - {reward: genshin.talent_up.freedom.5}
                """, registry::resolve));
    }

    @Test
    void gemAndTalentFamilyRarityPropertiesAcceptMinimalTwoDecimalPercentages() {
        var inventory = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Fragment",
                8103, "Vayuda Turquoise Chunk",
                8104, "Vayuda Turquoise Gemstone",
                8201, "Teachings of Freedom",
                8202, "Guide to Freedom",
                8203, "Philosophies of Freedom"));
        var tables = YamlLootTables.parse("""
                mixed:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:74.63, 3:19.9, 4:4.97, 5:0.5"}, weight: 1}
                  - {reward: genshin.talent_up.freedom, properties: {rarity: "2:20, 3:30, 4:50"}, weight: 1}
                repeated:
                  - {reward: genshin.talent_up.freedom, properties: {rarity: "2:50, 3:50"}, count: 4}
                rerolled:
                  - reward: {reward: genshin.talent_up.freedom, properties: {rarity: "2:50, 3:50"}, count: 4}
                    count: 4
                """, inventory::resolve);
        var counts = new java.util.HashMap<Integer, Integer>();
        var random = new Random(71);
        for (int i = 0; i < 60_000; i++) {
            var chosen = tables.roll("astaps.loot.mixed", random);
            assertEquals(1, chosen.values().stream().mapToInt(Integer::intValue).sum());
            int id = chosen.keySet().iterator().next();
            counts.merge(id, 1, Integer::sum);
        }
        int gems = 0, books = 0;
        for (int id = 8101; id <= 8104; id++) gems += counts.getOrDefault(id, 0);
        for (int id = 8201; id <= 8203; id++) books += counts.getOrDefault(id, 0);
        assertEquals(0.50, gems / 60_000.0, 0.02);
        assertEquals(0.50, books / 60_000.0, 0.02);
        assertEquals(0.7463, counts.getOrDefault(8101, 0) / (double) gems, 0.015);
        assertEquals(0.0050, counts.getOrDefault(8104, 0) / (double) gems, 0.003);
        assertEquals(0.30, counts.getOrDefault(8202, 0) / (double) books, 0.015);

        boolean split = false;
        for (int i = 0; i < 50; i++) {
            var grouped = tables.roll("astaps.loot.repeated", random);
            assertEquals(1, grouped.size(), "One entry execution chooses one grade");
            assertEquals(4, grouped.values().iterator().next());
            var rerolled = tables.roll("astaps.loot.rerolled", random);
            assertEquals(16, rerolled.values().stream().mapToInt(Integer::intValue).sum());
            if (rerolled.size() == 2) split = true;
        }
        assertTrue(split, "Separate nested executions must select their grades independently");
    }

    @Test
    void compressedFixedCountGemPoolPreservesOriginalWeightedDistribution() {
        var gems = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Fragment",
                8103, "Vayuda Turquoise Chunk",
                8104, "Vayuda Turquoise Gemstone"));
        var pools = YamlLootTables.parse("""
                original:
                  - {reward: genshin.avatar_ascension.anemo.2, count: 3, weight: 5697}
                  - {reward: genshin.avatar_ascension.anemo.3, count: 3, weight: 1519}
                  - {reward: genshin.avatar_ascension.anemo.4, count: 3, weight: 380}
                compact:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:75, 3:20, 4:5"}, count: 3, weight: 1}
                nines:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:75, 3:20, 4:5"}, count: 9, weight: 1}
                """, gems::resolve);
        for (String name : java.util.List.of("original", "compact", "nines")) {
            var counts = new int[3];
            var random = new Random(35601);
            int amount = "nines".equals(name) ? 9 : 3;
            for (int i = 0; i < 20_000; i++) {
                var reward = pools.roll("astaps.loot." + name, random);
                assertEquals(1, reward.size(), "Every fixed-count roll uses one gem tier");
                assertEquals(amount, reward.values().iterator().next());
                int id = reward.keySet().iterator().next();
                counts[id - 8101]++;
            }
            assertEquals(0.75, counts[0] / 20_000.0, 0.02, name);
            assertEquals(0.20, counts[1] / 20_000.0, 0.02, name);
            assertEquals(0.05, counts[2] / 20_000.0, 0.01, name);
        }
    }

    @Test
    void elementalGemFamilySamplesEveryCopyWithFixedRarity() {
        var gems = new ItemIdRegistry(Map.of(
                8101, "Vajrada Amethyst Fragment",
                8102, "Shivada Jade Fragment",
                8103, "Varunada Lazurite Fragment"));
        var tables = YamlLootTables.parse("""
                weekly:
                  - {reward: genshin.avatar_ascension, properties: {element: "electro:33.33, cryo:33.33, hydro:33.34", rarity: 3}, count: "1+1:2"}
                pair:
                  - {reward: genshin.avatar_ascension, properties: {element: "electro:33.33, cryo:33.33, hydro:33.34", rarity: 3}, count: 2}
                """, gems::resolve);
        int[] items = new int[3];
        int extraRolls = 0;
        var random = new Random(35601);
        for (int i = 0; i < 90_000; i++) {
            var rewards = tables.roll("weekly", random);
            int total = rewards.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total == 1 || total == 2);
            extraRolls += total - 1;
            for (var entry : rewards.entrySet()) {
                assertTrue(entry.getKey() >= 8101 && entry.getKey() <= 8103);
                items[entry.getKey() - 8101] += entry.getValue();
            }
        }
        int all = java.util.Arrays.stream(items).sum();
        assertEquals(0.3333, items[0] / (double) all, 0.012);
        assertEquals(0.3333, items[1] / (double) all, 0.012);
        assertEquals(0.3334, items[2] / (double) all, 0.012);
        assertEquals(0.02, extraRolls / 90_000.0, 0.004);

        int mixed = 0;
        for (int i = 0; i < 1_000; i++) {
            var rewards = tables.roll("pair", random);
            assertEquals(2, rewards.values().stream().mapToInt(Integer::intValue).sum());
            if (rewards.size() > 1) mixed++;
        }
        assertTrue(mixed > 550, "An element is selected afresh for every awarded gem");
    }

    @Test
    void invalidElementalGemDistributionsAreRejected() {
        var gems = new ItemIdRegistry(Map.of(
                8101, "Vajrada Amethyst Fragment",
                8102, "Shivada Jade Fragment",
                8103, "Varunada Lazurite Fragment"));
        for (String properties : java.util.List.of(
                "{element: \"electro:50, cryo:40\", rarity: 3}",
                "{element: \"electro:70, electro:30\", rarity: 3}",
                "{element: \"electro:101\", rarity: 3}",
                "{element: \"invalid:100\", rarity: 3}",
                "{element: \"pyro:100\", rarity: 3}",
                "{element: \"electro:100\", rarity: 6}",
                "{element: \"electro:100\", rarity: \"3\"}",
                "{element: \"electro:100\", rarity: 3, extra: true}")) {
            assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                    invalid:
                      - {reward: genshin.avatar_ascension, properties: %s}
                    """.formatted(properties), gems::resolve), properties);
        }
    }

    @Test
    void fixedAdvancementRarityNumbersPreserveGuaranteedTiers() {
        var items = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Fragment",
                8103, "Vayuda Turquoise Gemstone",
                8201, "Teachings of Freedom",
                8202, "Guide to Freedom",
                8203, "Philosophies of Freedom"));
        var tables = YamlLootTables.parse("""
                guaranteed:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: 2}, count: 3}
                  - {reward: genshin.avatar_ascension.vayuda_turquoise, properties: {rarity: 3}}
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: 5}}
                  - {reward: genshin.talent_up.freedom, properties: {rarity: 2}, count: 2}
                  - {reward: genshin.talent_up.freedom, properties: {rarity: 3}}
                  - {reward: genshin.talent_up.freedom, properties: {rarity: 4}}
                legacy:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:100"}, count: 3}
                  - {reward: genshin.talent_up.freedom, properties: {rarity: "3:100"}}
                """, items::resolve);
        var expected = Map.of(8101, 3, 8102, 1, 8103, 1, 8201, 2, 8202, 1, 8203, 1);
        for (int i = 0; i < 100; i++) {
            assertEquals(expected, tables.roll("guaranteed", new Random(i)));
        }
        assertEquals(Map.of(8101, 3, 8202, 1),
                tables.roll("legacy", new Random(71)));
    }

    @Test
    void invalidFixedAdvancementRarityNumbersAreRejected() {
        var items = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Fragment",
                8201, "Teachings of Freedom"));
        for (String spec : java.util.List.of(
                "1", "0", "6", "-1", "2.5", "\"2\"", "true", "null",
                "[2]", "{grade: 2}")) {
            assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                    invalid:
                      - {reward: genshin.avatar_ascension.anemo, properties: {rarity: %s}}
                    """.formatted(spec), items::resolve), spec);
        }
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - {reward: genshin.talent_up.freedom, properties: {rarity: 5}}
                """, items::resolve));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: 4}}
                """, items::resolve));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - {reward: genshin.currency.mora, properties: {rarity: 2}}
                """, items::resolve));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: 2, extra: 1}}
                """, items::resolve));
    }

    @Test
    void invalidAdvancementRarityDistributionsAreRejected() {
        var items = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Fragment",
                8201, "Teachings of Freedom",
                8202, "Guide to Freedom"));
        for (String spec : java.util.List.of(
                "2:60, 3:30", // missing 10%
                "2:70, 3:40", // exceeds 100%
                "2:50, 2:50", // duplicate tier
                "2:99, 5:1", // 5-star talent book does not exist
                "2:foo, 3:100")) {
            assertThrows(IllegalArgumentException.class, () ->
                    YamlLootTables.parse("""
                            invalid:
                              - reward: genshin.talent_up.freedom
                                properties: {rarity: "%s"}
                            """.formatted(spec), items::resolve), spec);
        }
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:50, 3:50"}}
                """, id -> {
                    if (id.equals("genshin.avatar_ascension.anemo.2")) return 8101;
                    return -2;
                }));
        assertThrows(IllegalArgumentException.class, () ->
                YamlLootTables.parse("""
                        invalid:
                          - {reward: genshin.currency.mora, properties: {rarity: "2:100"}}
                        """, items::resolve));
    }

    @Test
    void yamlShortKeysBecomeGlobalLootIds() {
        var tables = parse("""
                surprise:
                  - {reward: genshin.currency.mora, count: 300}
                经验书:
                  - {reward: genshin.avatar_up.heros_wit}
                """);
        assertEquals(java.util.List.of("astaps.loot.surprise", "astaps.loot.经验书"),
                tables.names());
        assertEquals("astaps.loot.surprise", tables.resolveName("surprise"));
        assertEquals("astaps.loot.surprise",
                tables.resolveName("astaps.loot.surprise"));
        assertEquals("astaps.loot.经验书", tables.resolveName("经验书"));
        assertEquals(Map.of(202, 300),
                tables.roll("astaps.loot.surprise", new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> tables.resolveName("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> tables.resolveName("genshin.currency.mora"));
        assertEquals(Map.of(202, 300), tables.roll("surprise", new Random(0)));
        assertDoesNotThrow(() -> parse("""
                astaps.loot.surprise:
                  - {reward: genshin.currency.mora}
                """));
    }

    @Test
    void artifactPropertiesChooseNativeIdsIndependentlyPerAward() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        for (int slot = 1; slot <= 5; slot++) {
            for (int substats = 1; substats <= 2; substats++) {
                variants.add(new ArtifactResolver.Variant(55300 + slot * 10 + substats,
                        "berserker", slot, 3, substats));
            }
        }
        var artifactResolver = new ArtifactResolver(variants);
        var loot = YamlLootTables.parse("""
                chest:
                  - reward: genshin.artifact.berserker
                    properties: {slot: "1~5", rarity: 3, substats: "1:80,2:20"}
                    count: 2
                """, resolver, artifactResolver);
        int one = 0, two = 0;
        var random = new Random(101);
        for (int i = 0; i < 5000; i++) {
            var rewards = loot.roll("astaps.loot.chest", random);
            assertEquals(2, rewards.values().stream().mapToInt(Integer::intValue).sum());
            for (var entry : rewards.entrySet()) {
                int slot = (entry.getKey() / 10) % 10;
                int substats = entry.getKey() % 10;
                assertTrue(slot >= 1 && slot <= 5);
                if (substats == 1) one += entry.getValue();
                else if (substats == 2) two += entry.getValue();
                else fail("Unexpected native artifact variant");
            }
        }
        assertEquals(10_000, one + two);
        assertEquals(0.80, one / 10_000.0, 0.025);
        assertEquals(0.20, two / 10_000.0, 0.025);
    }

    @Test
    void fullFiveSlotArtifactSubtableCanBeInlinedWithoutChangingProbabilities() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        for (int slot = 1; slot <= 5; slot++) {
            variants.add(new ArtifactResolver.Variant(57000 + slot,
                    "the_exile", slot, 4, 2));
        }
        var artifacts = new ArtifactResolver(variants);
        var nested = YamlLootTables.parse("""
                slots: &slots
                  - {reward: genshin.artifact.the_exile, properties: {slot: goblet, rarity: 4, substats: 2}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: plume, rarity: 4, substats: 2}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: circlet, rarity: 4, substats: 2}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: flower, rarity: 4, substats: 2}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: sands, rarity: 4, substats: 2}, weight: 1}
                choice:
                  - {reward: *slots, weight: 2}
                  - {reward: genshin.currency.mora, weight: 3}
                """, resolver, artifacts);
        var flat = YamlLootTables.parse("""
                choice:
                  - {reward: genshin.artifact.the_exile, properties: {slot: "1~5", rarity: 4, substats: 2}, weight: 2}
                  - {reward: genshin.currency.mora, weight: 3}
                """, resolver, artifacts);
        assertFalse(flat.contains("astaps.loot.slots"));
        for (var pool : java.util.List.of(nested, flat)) {
            var counts = new java.util.HashMap<Integer, Integer>();
            var random = new Random(71);
            for (int i = 0; i < 30_000; i++) {
                var reward = pool.roll("astaps.loot.choice", random);
                assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
                int id = reward.keySet().iterator().next();
                counts.merge(id, 1, Integer::sum);
            }
            assertEquals(0.60, counts.getOrDefault(202, 0) / 30_000.0, 0.02);
            for (int slot = 1; slot <= 5; slot++) {
                assertEquals(0.08, counts.getOrDefault(57000 + slot, 0) / 30_000.0,
                        0.02, "slot " + slot);
            }
        }
    }

    @Test
    void optionalCountHasSameDropProbabilitiesAsWeightedNullSelection() {
        var tables = parse("""
                billets: &billets
                  - {reward: genshin.avatar_up.heros_wit, weight: 1}
                  - {reward: genshin.avatar_up.adventurers_experience, weight: 1}
                old_item:
                  - reward:
                      - {reward: null, weight: 67}
                      - {reward: genshin.currency.mora, weight: 33}
                new_item:
                  - {reward: genshin.currency.mora, count: "0:67, 1:33"}
                old_pool:
                  - reward:
                      - {reward: null, weight: 22}
                      - {reward: *billets, weight: 3}
                new_pool:
                  - {reward: *billets, count: "0:22, 1:3"}
                """);
        for (String name : new String[]{"old_item", "new_item", "old_pool", "new_pool"}) {
            var random = new Random(71);
            int received = 0;
            for (int i = 0; i < 30_000; i++) {
                var result = tables.roll("astaps.loot." + name, random);
                int total = result.values().stream().mapToInt(Integer::intValue).sum();
                assertTrue(total == 0 || total == 1);
                if (total > 0) received++;
            }
            double expected = name.endsWith("item") ? 0.33 : 0.12;
            assertEquals(expected, received / 30_000.0, 0.015, name);
        }
    }

    @Test
    void nestedSubstatWeightsCanBeMovedIntoArtifactProperties() {
        var variants = new java.util.ArrayList<ArtifactResolver.Variant>();
        String[] sets = {"berserker", "instructor", "the_exile"};
        for (int set = 0; set < sets.length; set++) {
            for (int slot = 1; slot <= 5; slot++) {
                for (int substats = 1; substats <= 2; substats++) {
                    variants.add(new ArtifactResolver.Variant(
                            65000 + set * 100 + slot * 10 + substats,
                            sets[set], slot, 3, substats));
                }
            }
        }
        var artifacts = new ArtifactResolver(variants);
        var nested = YamlLootTables.parse("""
                choice:
                  - reward:
                      - {reward: genshin.artifact.berserker, properties: {slot: "1~5", rarity: 3, substats: 1}, weight: 1}
                      - {reward: genshin.artifact.instructor, properties: {slot: "1~5", rarity: 3, substats: 1}, weight: 1}
                      - {reward: genshin.artifact.the_exile, properties: {slot: "1~5", rarity: 3, substats: 1}, weight: 1}
                    weight: 33
                  - reward:
                      - {reward: genshin.artifact.berserker, properties: {slot: "1~5", rarity: 3, substats: 2}, weight: 1}
                      - {reward: genshin.artifact.instructor, properties: {slot: "1~5", rarity: 3, substats: 2}, weight: 1}
                      - {reward: genshin.artifact.the_exile, properties: {slot: "1~5", rarity: 3, substats: 2}, weight: 1}
                    weight: 17
                """, resolver, artifacts);
        var flat = YamlLootTables.parse("""
                choice:
                  - {reward: genshin.artifact.berserker, properties: {slot: "1~5", rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                  - {reward: genshin.artifact.instructor, properties: {slot: "1~5", rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                  - {reward: genshin.artifact.the_exile, properties: {slot: "1~5", rarity: 3, substats: "1:66, 2:34"}, weight: 1}
                """, resolver, artifacts);

        for (var table : java.util.List.of(nested, flat)) {
            int[][] counts = new int[3][2];
            var random = new Random(2026);
            for (int i = 0; i < 30_000; i++) {
                var reward = table.roll("astaps.loot.choice", random);
                assertEquals(1, reward.size());
                int id = reward.keySet().iterator().next() - 65000;
                int set = id / 100;
                int slot = id % 100 / 10;
                int substats = id % 10;
                assertTrue(set >= 0 && set < 3);
                assertTrue(slot >= 1 && slot <= 5);
                assertTrue(substats == 1 || substats == 2);
                counts[set][substats - 1]++;
            }
            for (int set = 0; set < 3; set++) {
                assertEquals(0.66 / 3, counts[set][0] / 30_000.0, 0.02);
                assertEquals(0.34 / 3, counts[set][1] / 30_000.0, 0.02);
            }
        }
    }

    @Test
    void missingArtifactPropertiesSelectOnlyAvailableNativeVariants() {
        var artifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(60140, "adventurer", 1, 1, 0),
                new ArtifactResolver.Variant(60110, "adventurer", 4, 1, 0)));
        var loot = YamlLootTables.parse("""
                chest:
                  - reward: genshin.artifact.adventurer
                    properties: {rarity: 1}
                """, resolver, artifacts);
        for (int i = 0; i < 100; i++) {
            var reward = loot.roll("astaps.loot.chest", new Random(i));
            // The native test inventory has two slots; only their IDs can appear.
            // An explicitly selected unavailable slot fails instead of inventing an ID.
            assertTrue(reward.containsKey(60140) || reward.containsKey(60110));
        }
        assertThrows(IllegalArgumentException.class, () ->
                YamlLootTables.parse("""
                        bad:
                          - reward: genshin.artifact.adventurer
                            properties: {invalid: 1}
                        """, resolver, artifacts));
        assertThrows(IllegalArgumentException.class, () ->
                YamlLootTables.parse("""
                        bad:
                          - reward: genshin.currency.mora
                            properties: {rarity: 1}
                        """, resolver, artifacts));
    }

    @Test
    void aDifferentLoaderNamespaceStillUsesShortYamlNames() {
        var other = YamlLootTables.parse("""
                surprise:
                  - {reward: genshin.currency.mora, count: 7}
                """, "my_mod", resolver);
        assertEquals(java.util.List.of("my_mod.loot.surprise"), other.names());
        assertEquals("my_mod.loot.surprise",
                other.resolveName("my_mod.loot.surprise"));
        assertEquals(Map.of(202, 7),
                other.roll("my_mod.loot.surprise", new Random(0)));
        assertEquals("my_mod.loot.surprise", other.resolveName("surprise"));
    }

    @Test
    void fixedItemsAndOmittedCount() {
        var tables = parse("""
                common_chest:
                  - {reward: genshin.currency.mora, count: 300}
                  - {reward: genshin.avatar_up.heros_wit}
                """);
        assertEquals(Map.of(202, 300, 104003, 1), tables.roll("astaps.loot.common_chest", new Random(1)));
    }

    @Test
    void parsedPoolCanProduceAstapsItemParamData() {
        var tables = parse("""
                basic:
                  - {reward: genshin.currency.mora, count: 300}
                  - {reward: genshin.avatar_up.heros_wit}
                """);
        assertTrue(tables.contains("astaps.loot.basic"));
        assertFalse(tables.contains("astaps.loot.unknown"));
        var reward = tables.rollItems("astaps.loot.basic", new Random(1));
        assertEquals(2, reward.size());
        assertEquals(202, reward.get(0).getId());
        assertEquals(300, reward.get(0).getCount());
        assertEquals(104003, reward.get(1).getId());
        assertEquals(1, reward.get(1).getCount());
    }

    @Test
    void quotedCountSupportsRangesAndRelativeWeights() {
        var tables = parse("""
                chest:
                  - {reward: genshin.currency.mora, count: "2~4"}
                  - {reward: genshin.avatar_up.heros_wit, count: "0:20, 1:80"}
                  - {reward: genshin.avatar_up.adventurers_experience, count: "2:80,3:20"}
                """);
        for (int i = 0; i < 300; i++) {
            var rewards = tables.roll("astaps.loot.chest", new Random(i));
            assertTrue(rewards.get(202) >= 2 && rewards.get(202) <= 4);
            assertTrue(rewards.getOrDefault(104003, 0) <= 1);
            assertTrue(rewards.get(104002) == 2 || rewards.get(104002) == 3);
        }
    }

    @Test
    void fractionalPercentagesAreSupportedButDecimalWeightsAreRejected() {
        assertDoesNotThrow(() -> LootCount.parse("2+1:55.56"));
        assertDoesNotThrow(() -> LootCount.parse("1:12.5,2:87.5"));
        assertDoesNotThrow(() -> LootCount.parse("1:60,2:60"));
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2.5556"));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                bad:
                  - {reward: genshin.currency.mora, weight: 1.5}
                """));
    }

    @Test
    void mutuallyExclusiveWeightedChoiceAndAlias() {
        var tables = parse("""
                experience: &exp
                  - {reward: genshin.avatar_up.heros_wit, weight: 1}
                  - {reward: genshin.avatar_up.adventurers_experience, count: 2, weight: 3}
                chest:
                  - {reward: genshin.currency.mora, count: 300}
                  - {reward: *exp, count: "0:30, 1:70"}
                """);
        int none = 0, big = 0, small = 0;
        Random random = new Random(42);
        for (int i = 0; i < 40_000; i++) {
            var r = tables.roll("astaps.loot.chest", random);
            assertEquals(300, r.get(202).intValue());
            if (!r.containsKey(104003) && !r.containsKey(104002)) none++;
            else if (r.containsKey(104003)) {
                assertEquals(1, r.get(104003).intValue());
                assertFalse(r.containsKey(104002));
                big++;
            } else {
                assertEquals(2, r.get(104002).intValue());
                small++;
            }
        }
        assertEquals(0.30, none / 40_000.0, 0.018);
        assertEquals(0.175, big / 40_000.0, 0.018);
        assertEquals(0.525, small / 40_000.0, 0.018);
    }

    @Test
    void nestedCountRepeatsIndependentRolls() {
        var tables = parse("""
                experience: &exp
                  - {reward: genshin.avatar_up.heros_wit, weight: 1}
                  - {reward: genshin.avatar_up.adventurers_experience, weight: 1}
                chest:
                  - {reward: *exp, count: 2}
                """);
        int mixed = 0;
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            var r = tables.roll("astaps.loot.chest", random);
            assertEquals(2, r.values().stream().mapToInt(Integer::intValue).sum());
            if (r.containsKey(104003) && r.containsKey(104002)) mixed++;
        }
        assertTrue(mixed > 800 && mixed < 1200, "Must reroll the nested pool twice");
    }

    @Test
    void bonusCountRerollsNestedWeightedPoolIndependently() {
        var tables = parse("""
                experience: &exp
                  - {reward: genshin.avatar_up.heros_wit, weight: 1}
                  - {reward: genshin.avatar_up.adventurers_experience, weight: 1}
                chest:
                  - {reward: *exp, count: "2+1:55.56"}
                """);
        Random random = new Random(71);
        int third = 0;
        int mixed = 0;
        for (int i = 0; i < 30_000; i++) {
            var reward = tables.roll("astaps.loot.chest", random);
            int total = reward.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total == 2 || total == 3);
            if (total == 3) third++;
            if (reward.containsKey(104003) && reward.containsKey(104002)) mixed++;
        }
        assertEquals(0.5556, third / 30_000.0, 0.015);
        assertTrue(mixed > 12_000, "Every execution must reroll the nested pool");
    }

    @Test
    void explicitEmptyChoiceAndNullItem() {
        var tables = parse("""
                chance:
                  - reward:
                      - {reward: genshin.avatar_up.heros_wit, weight: 5}
                      - {reward: genshin.avatar_up.adventurers_experience, weight: 3}
                    count: "0:20, 1:80"
                empty:
                  - {reward: null}
                  - {reward: []}
                weighted_empty:
                  - {reward: null, weight: 1}
                  - {reward: genshin.currency.mora, weight: 1}
                """);
        assertEquals(Map.of(), tables.roll("astaps.loot.empty", new Random(0)));
        for (int i = 0; i < 100; i++) {
            var rewards = tables.roll("astaps.loot.weighted_empty", new Random(i));
            assertTrue(rewards.isEmpty() || rewards.equals(Map.of(202, 1)));
        }
    }

    @Test
    void numericAndNullWeightsCannotMix() {
        var error = assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.currency.mora, weight: 0}
                  - {reward: genshin.avatar_up.heros_wit}
                """));
        assertTrue(error.getMessage().contains("cannot mix"));
    }

    @Test
    void explicitlyNumericZeroWeightIsAllowedWhenAnotherIsPositive() {
        var tables = parse("""
                pool:
                  - {reward: genshin.currency.mora, weight: 0}
                  - {reward: genshin.avatar_up.heros_wit, weight: 1}
                """);
        assertEquals(Map.of(104003, 1), tables.roll("astaps.loot.pool", new Random(0)));
    }

    @Test
    void failsClosedOnBrokenContent() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: 202}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: "202"}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: missing_item}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.currency.mora, count: "1:60,2"}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.currency.mora, unknown: true}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                invalid:
                  - {reward: genshin.currency.mora, weight: 0}
                """));
        assertThrows(RuntimeException.class, () -> parse("""
                recursive: &self
                  - {reward: *self}
                """));
    }

    @Test
    void omittedZeroCountMustBeExplicit() {
        var distribution = LootCount.parse("0:80, 1:20");
        int[] outcomes = new int[2];
        var random = new Random(2026);
        for (int i = 0; i < 20_000; i++) {
            int count = distribution.sample(random);
            assertTrue(count == 0 || count == 1);
            outcomes[count]++;
        }
        assertEquals(0.80, outcomes[0] / 20_000.0, 0.015);
        assertEquals(0.20, outcomes[1] / 20_000.0, 0.015);

        var table = parse("""
                chance:
                  - {reward: genshin.currency.mora, count: "0:80, 1:20"}
                """);
        int awarded = 0;
        for (int i = 0; i < 2_000; i++) {
            awarded += table.roll("astaps.loot.chance", random).getOrDefault(202, 0);
        }
        assertEquals(0.20, awarded / 2_000.0, 0.04);
    }

    @Test
    void countDistributionUsesExplicitRelativeWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> LootCount.parse("0:40,1,2:18"));
        assertEquals(1, LootCount.parse("1:20").sample(new Random(71)));
        var distribution = LootCount.parse("0:40,1:42,2:18");
        int[] outcome = new int[3];
        var rng = new Random(9876);
        for (int i = 0; i < 50_000; i++) outcome[distribution.sample(rng)]++;
        assertEquals(0.40, outcome[0] / 50_000.0, 0.015);
        assertEquals(0.42, outcome[1] / 50_000.0, 0.015);
        assertEquals(0.18, outcome[2] / 50_000.0, 0.015);

        var nullable = parse("""
                pool:
                  - {reward: null, weight: 2}
                  - {reward: genshin.currency.mora, weight: 3}
                """);
        int empty = 0;
        rng = new Random(123);
        for (int i = 0; i < 5_000; i++) if (nullable.roll("astaps.loot.pool", rng).isEmpty()) empty++;
        assertEquals(0.4, empty / 5000.0, 0.04);
    }

    @Test
    void sharedItemRegistryIsUsedByYaml() {
        var registry = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        var tables = YamlLootTables.parse("""
                chest:
                  - {reward: genshin.currency.mora, count: 300}
                  - {reward: genshin.avatar_up.heros_wit}
                """, registry::resolve);
        assertEquals(Map.of(202, 300, 104003, 1), tables.roll("astaps.loot.chest", new Random(1)));

        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                chest:
                  - {reward: genshin.avatar.amber}
                """, registry::resolve));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                chest:
                  - {item: genshin.currency.mora}
                """, registry::resolve));
    }

    @Test
    void itemClassificationUsesGameResourceTypes() {
        assertEquals("Weapons", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_WEAPON,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_NONE));
        assertEquals("Artifacts", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_RELIQUARY,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_NONE));
        assertEquals("Character EXP Materials", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_EXP_FRUIT));
        assertEquals("Food", ItemIdRegistry.categoryOf(
                emu.grasscutter.game.inventory.ItemType.ITEM_MATERIAL,
                emu.grasscutter.game.inventory.MaterialType.MATERIAL_FOOD));
        assertEquals(104003, new ItemIdRegistry(java.util.Collections.singletonMap(104003, null)).resolve("genshin.avatar_up.heros_wit"));
    }

    @Test
    void itemNamesKeepEnglishNamesAndDetectAmbiguity() {
        var names = new ItemIdRegistry(Map.of(
                104003, "Hero's Wit",
                104002, "Adventurer's Experience",
                202, "Mora"));
        assertEquals("genshin.avatar_up.heros_wit", names.describe(104003).key());
        assertEquals("Character EXP Materials", names.describe(104003).category());
        assertEquals(104003, names.resolve("Hero's Wit"));
        assertEquals(104003, names.resolve("genshin.avatar_up.heros_wit"));
        assertEquals(202, names.resolve("202"));
        var colliding = new ItemIdRegistry(Map.of(1, "Same", 2, "Same"),
                Map.of(1, "Materials", 2, "Materials"));
        assertThrows(IllegalArgumentException.class, () -> colliding.resolve("genshin.material.same"));
    }
    @Test
    void hierarchicalGemFamilyAliasesResolveToOneNativeItem() {
        var inventory = new emu.grasscutter.data.ItemIdRegistry(
                java.util.Map.of(8001, "Vayuda Turquoise Sliver"));
        var tables = YamlLootTables.parse("""
                two_gems:
                  - {reward: genshin.avatar_ascension.anemo.sliver, count: 2}
                  - {reward: genshin.avatar_ascension.vayuda_turquoise.sliver}
                """, inventory::resolve);
        var rewards = tables.roll("astaps.loot.two_gems", new java.util.Random(7));
        assertEquals(3, rewards.get(8001));
    }

    @Test
    void nestedLocalLootPathsResolveWithinOwningNamespace() {
        var tables = YamlLootTables.parse("""
                boss.gem:
                  - {reward: genshin.currency.mora, count: 3}
                """, name -> {
                    if (name.equals("genshin.currency.mora")) return 202;
                    throw new IllegalArgumentException(name);
                });
        assertEquals("astaps.loot.boss.gem", tables.resolveName("boss.gem"));
        assertEquals("astaps.loot.boss.gem", tables.resolveName("astaps.loot.boss.gem"));
        assertEquals(3, tables.roll("boss.gem", new java.util.Random(7)).get(202));
    }

}
