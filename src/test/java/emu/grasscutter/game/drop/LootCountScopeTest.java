package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LootCountScopeTest {
    private static final int MORA = 202;
    private static final int WIT = 104003;

    private static final YamlLootTables.ItemResolver ITEMS = name -> switch (name) {
        case "genshin.currency.mora" -> MORA;
        case "genshin.avatar_up.heros_wit" -> WIT;
        default -> -2;
    };

    @Test
    void typeAndPoolShareRelativeWeightSampling() {
        var tables = YamlLootTables.parse("""
                weapon: {type: "genshin.currency.mora:3, genshin.avatar_up.heros_wit:1"}
                same: {type: "genshin.currency.mora:3, genshin.avatar_up.heros_wit:1", count: 2}
                repeated: {type: weapon, count: 2}
                """, ITEMS);
        var random = new Random(165);
        int sameMora = 0, mixed = 0;
        for (int i = 0; i < 12_000; i++) {
            var same = tables.roll("same", random);
            assertEquals(2, same.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(same.equals(Map.of(MORA, 2)) || same.equals(Map.of(WIT, 2)));
            if (same.containsKey(MORA)) sameMora++;
            var repeated = tables.roll("repeated", random);
            assertEquals(2, repeated.values().stream().mapToInt(Integer::intValue).sum());
            if (repeated.size() == 2) mixed++;
        }
        assertEquals(0.75, sameMora / 12_000.0, 0.03);
        assertEquals(0.375, mixed / 12_000.0, 0.03);
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: "genshin.currency.mora:3, genshin.avatar_up.heros_wit"}
                """, ITEMS));
    }

    @Test
    void artifactSlotUsesTheSameWeightedExpression() {
        var artifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(900001, "berserker", 1, 3, 1),
                new ArtifactResolver.Variant(900002, "berserker", 2, 3, 1)));
        var tables = YamlLootTables.parse("""
                artifacts:
                  - {type: genshin.artifact.berserker, properties: {slot: "flower:3, plume:1", rarity: 3, substats: 1}}
                """, ITEMS, artifacts);
        int flowers = 0;
        var random = new Random(166);
        for (int i = 0; i < 12_000; i++) {
            var reward = tables.roll("artifacts", random);
            assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
            if (reward.containsKey(900001)) flowers++;
        }
        assertEquals(0.75, flowers / 12_000.0, 0.03);
    }

    @Test
    void directEntryFieldsShareTheSameWeightedCompiler() {
        var names = (YamlLootTables.ItemResolver) item -> switch (item) {
            case "genshin.avatar_ascension.anemo.2" -> 2102;
            case "genshin.avatar_ascension.anemo.3" -> 2103;
            case "genshin.avatar_ascension.pyro.2" -> 2202;
            case "genshin.avatar_ascension.pyro.3" -> 2203;
            case "genshin.avatar_ascension.hydro.2" -> 2302;
            case "genshin.avatar_ascension.hydro.3" -> 2303;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                ascension: {type: genshin.avatar_ascension.anemo, rarity: "2:3, 3:1"}
                mixed:
                  type: genshin.avatar_ascension
                  element: "pyro:3, hydro:1"
                  rarity: "2:3, 3:1"
                  count: "2:1, 3:1"
                """, names);
        var random = new Random(77);
        int slivers = 0, pyro = 0, rarity2 = 0, doubles = 0;
        for (int i = 0; i < 20_000; i++) {
            var first = tables.roll("ascension", random);
            assertEquals(1, first.values().stream().mapToInt(Integer::intValue).sum());
            if (first.containsKey(2102)) slivers++;
            var second = tables.roll("mixed", random);
            int total = second.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total == 2 || total == 3);
            if (total == 2) doubles++;
            pyro += second.entrySet().stream().filter(e -> e.getKey() / 100 == 22)
                    .mapToInt(Map.Entry::getValue).sum();
            rarity2 += second.entrySet().stream().filter(e -> e.getKey() % 10 == 2)
                    .mapToInt(Map.Entry::getValue).sum();
        }
        assertEquals(0.75, slivers / 20_000.0, 0.025);
        assertEquals(0.5, doubles / 20_000.0, 0.025);
        assertEquals(0.75, pyro / 50_000.0, 0.025);
        assertEquals(0.75, rarity2 / 50_000.0, 0.025);
    }

    @Test
    void invalidDirectAttributesFailWithoutNullPointerExceptions() {
        for (String expression : java.util.List.of("null", "true", "0")) {
            assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                    bad: {type: genshin.avatar_ascension.anemo, rarity: %s}
                    """.formatted(expression), name -> 2102));
        }
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: genshin.currency.mora, newField: "1:2, 2:3"}
                """, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                bad: {type: genshin.avatar_ascension.anemo, rarity: 2, properties: {rarity: 3}}
                """, name -> 2102));
    }

    @Test
    void complexWeightedBranchesUseTheSamePoolChooser() {
        var tables = YamlLootTables.parse("""
                grouped:
                  - [{type: genshin.currency.mora, count: 2}, {type: genshin.avatar_up.heros_wit, count: 3}]: 3
                  - {type: genshin.currency.mora, count: 7}: 1
                """, ITEMS);
        var random = new Random(85);
        int grouped = 0;
        for (int i = 0; i < 12_000; i++) {
            var result = tables.roll("grouped", random);
            if (result.equals(Map.of(MORA, 2, WIT, 3))) grouped++;
            else assertEquals(Map.of(MORA, 7), result);
        }
        assertEquals(0.75, grouped / 12_000.0, 0.03);
    }

    @Test
    void complexWeightedBranchesCannotMixWithUnweightedSiblings() {
        assertThrows(IllegalArgumentException.class, () -> YamlLootTables.parse("""
                invalid:
                  - [{type: genshin.currency.mora}]: 3
                  - {type: genshin.avatar_up.heros_wit}
                """, ITEMS));
    }

    @Test
    void uppercaseTemplateFieldsShareWeightedSemantics() {
        var itemNames = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "A" -> MORA;
            case "B" -> WIT;
            case "genshin.currency.mora" -> MORA;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                boss<MORA, FIRST, SECOND, N>:
                  - {type: genshin.currency.mora, count: "<MORA>"}
                  - {type: "<FIRST>:3, <SECOND>:1", count: "<N>"}
                boss_a: {type: "boss<1000, A, B, 2>"}
                """, itemNames);
        var rng = new Random(33);
        int bothMora = 0;
        int bothWit = 0;
        for (int i = 0; i < 12_000; i++) {
            var result = tables.roll("boss_a", rng);
            int mora = result.getOrDefault(MORA, 0);
            int wit = result.getOrDefault(WIT, 0);
            assertEquals(1002, mora + wit);
            if (mora == 1002) bothMora++;
            else {
                assertEquals(1000, mora);
                assertEquals(2, wit);
                bothWit++;
            }
        }
        assertEquals(0.75, bothMora / 12_000.0, 0.03);
        assertEquals(0.25, bothWit / 12_000.0, 0.03);
    }

    @Test
    void weightedTypeAndWeightedRarityEvaluateIndependently() {
        var ids = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "genshin.avatar_ascension.anemo.2" -> 3102;
            case "genshin.avatar_ascension.anemo.3" -> 3103;
            case "genshin.avatar_ascension.pyro.2" -> 3202;
            case "genshin.avatar_ascension.pyro.3" -> 3203;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                gems:
                  type: "genshin.avatar_ascension.anemo:3, genshin.avatar_ascension.pyro:1"
                  rarity: "2:3, 3:1"
                """, ids);
        var counts = new int[4];
        var rng = new Random(148);
        for (int i = 0; i < 20_000; i++) {
            var reward = tables.roll("gems", rng);
            assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
            int item = reward.keySet().iterator().next();
            counts[switch (item) {
                case 3102 -> 0;
                case 3103 -> 1;
                case 3202 -> 2;
                case 3203 -> 3;
                default -> throw new AssertionError("Unexpected reward: " + item);
            }]++;
        }
        assertEquals(0.5625, counts[0] / 20_000.0, 0.025);
        assertEquals(0.1875, counts[1] / 20_000.0, 0.025);
        assertEquals(0.1875, counts[2] / 20_000.0, 0.025);
        assertEquals(0.0625, counts[3] / 20_000.0, 0.025);
    }

    @Test
    void weightedArtifactTypesAcceptTheSameWeightedSlotField() {
        var artifacts = new ArtifactResolver(java.util.List.of(
                new ArtifactResolver.Variant(4101, "berserker", 1, 3, 1),
                new ArtifactResolver.Variant(4102, "berserker", 2, 3, 1),
                new ArtifactResolver.Variant(4201, "instructor", 1, 3, 1),
                new ArtifactResolver.Variant(4202, "instructor", 2, 3, 1)));
        var tables = YamlLootTables.parse("""
                artifacts:
                  type: "genshin.artifact.berserker:3, genshin.artifact.instructor:1"
                  slot: "flower:3, plume:1"
                  rarity: 3
                  substats: 1
                """, ITEMS, artifacts);
        var counts = new java.util.HashMap<Integer, Integer>();
        var rng = new Random(167);
        for (int i = 0; i < 20_000; i++) {
            var reward = tables.roll("artifacts", rng);
            assertEquals(1, reward.values().stream().mapToInt(Integer::intValue).sum());
            counts.merge(reward.keySet().iterator().next(), 1, Integer::sum);
        }
        assertEquals(0.5625, counts.getOrDefault(4101, 0) / 20_000.0, 0.025);
        assertEquals(0.1875, counts.getOrDefault(4102, 0) / 20_000.0, 0.025);
        assertEquals(0.1875, counts.getOrDefault(4201, 0) / 20_000.0, 0.025);
        assertEquals(0.0625, counts.getOrDefault(4202, 0) / 20_000.0, 0.025);
    }

    @Test
    void countsUseExplicitRelativeWeights() {
        var distribution = LootCount.parse("2:586, 3:414");
        var random = new Random(71);
        int threes = 0;
        for (int i = 0; i < 20_000; i++) {
            int count = distribution.sample(random);
            assertTrue(count == 2 || count == 3);
            if (count == 3) threes++;
        }
        assertEquals(0.414, threes / 20_000.0, 0.02);
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2, 3:101"));
        assertThrows(IllegalArgumentException.class, () -> LootCount.parse("2:586, 3"));
    }

    @Test
    void nestedPoolCountRerollsButLeafCountCopiesTheSelectedItem() {
        var tables = YamlLootTables.parse("""
                weapon: &weapon
                  - {reward: genshin.currency.mora, weight: 60}
                  - {reward: genshin.avatar_up.heros_wit, weight: 40}
                same:
                  - reward:
                      - {reward: genshin.currency.mora, count: 2, weight: 60}
                      - {reward: genshin.avatar_up.heros_wit, count: 2, weight: 40}
                independent:
                  - {reward: *weapon, count: 2}
                ranged_independent:
                  - {reward: *weapon, count: "2:58.6, 3:41.4"}
                """, ITEMS);

        int both = 0, sameMora = 0, independentMora = 0, independentWit = 0;
        var random = new Random(5);
        for (int i = 0; i < 20_000; i++) {
            Map<Integer, Integer> fixedChoice = tables.roll("same", random);
            assertEquals(2, fixedChoice.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(fixedChoice.equals(Map.of(MORA, 2))
                    || fixedChoice.equals(Map.of(WIT, 2)));
            if (fixedChoice.containsKey(MORA)) sameMora++;

            Map<Integer, Integer> repeated = tables.roll("independent", random);
            assertEquals(2, repeated.values().stream().mapToInt(Integer::intValue).sum());
            if (repeated.size() == 2) {
                assertEquals(Map.of(MORA, 1, WIT, 1), repeated);
                both++;
            } else if (repeated.containsKey(MORA)) independentMora++;
            else if (repeated.containsKey(WIT)) independentWit++;
            else fail("Unexpected repeated pool result: " + repeated);
        }
        assertEquals(0.60, sameMora / 20_000.0, 0.025);
        assertEquals(0.48, both / 20_000.0, 0.025);
        assertEquals(0.36, independentMora / 20_000.0, 0.025);
        assertEquals(0.16, independentWit / 20_000.0, 0.025);
    }

    @Test
    void rarityAndElementDistributionsUseRelativeWeights() {
        var itemIds = (YamlLootTables.ItemResolver) name -> switch (name) {
            case "genshin.avatar_ascension.anemo.2" -> 1202;
            case "genshin.avatar_ascension.anemo.3" -> 1203;
            case "genshin.avatar_ascension.pyro.2" -> 1204;
            case "genshin.avatar_ascension.hydro.2" -> 1205;
            default -> -2;
        };
        var tables = YamlLootTables.parse("""
                rarity:
                  - {reward: genshin.avatar_ascension.anemo, properties: {rarity: "2:3, 3:1"}}
                element:
                  - {reward: genshin.avatar_ascension, properties: {element: "pyro:3, hydro:1", rarity: 2}}
                """, itemIds);
        var random = new Random(74);
        int rarityFirst = 0, elementFirst = 0;
        for (int i = 0; i < 10_000; i++) {
            if (tables.roll("rarity", random).containsKey(1202)) rarityFirst++;
            if (tables.roll("element", random).containsKey(1204)) elementFirst++;
        }
        assertEquals(0.75, rarityFirst / 10_000.0, 0.025);
        assertEquals(0.75, elementFirst / 10_000.0, 0.025);
    }

    @Test
    void explicitDistributionControlsIndependentPoolExecutions() {
        var tables = YamlLootTables.parse("""
                weapon: &weapon
                  - {reward: genshin.currency.mora, weight: 60}
                  - {reward: genshin.avatar_up.heros_wit, weight: 40}
                ranged_independent:
                  - {reward: *weapon, count: "2:58.6, 3:41.4"}
                """, ITEMS);
        var random = new Random(41);
        int threeExecutions = 0;
        for (int i = 0; i < 20_000; i++) {
            Map<Integer, Integer> output = tables.roll("ranged_independent", random);
            int total = output.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total == 2 || total == 3);
            if (total == 3) threeExecutions++;
        }
        assertEquals(0.414, threeExecutions / 20_000.0, 0.025);
    }
}
