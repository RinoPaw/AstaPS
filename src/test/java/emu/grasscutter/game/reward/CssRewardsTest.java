package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CssRewardsTest {
    private final ItemIdRegistry items = new ItemIdRegistry(Map.of(
            102, "Adventure EXP",
            201, "Primogem",
            202, "Mora",
            104003, "Hero's Wit"));

    /** Table content has no knowledge of region, level, source type or selector. */
    private final YamlLootTables loot = YamlLootTables.parse("""
            currency:
              - {reward: genshin.item.mora, count: 100}
            经验书:
              - {reward: genshin.item.heros_wit}
            surprise:
              - {reward: genshin.item.mora, count: 30}
              - {reward: genshin.item.heros_wit}
            """, items::resolve);

    @Test
    void oneChestCanExecuteSeveralNamedLootTables() {
        var css = CssRewards.parse("""
                chest[kind="common"] {
                    --loot: currency, 经验书 2;
                    --genshin-item-primogem: 2;
                }
                """, items, loot);

        var selected = css.resolve("chest", Map.of("kind", "common"));
        assertEquals(Map.of("astaps.loot.currency", 1, "astaps.loot.经验书", 2),
                selected.loot());
        assertEquals(Map.of(201, 2), selected.items());
        assertEquals(Map.of(202, 100, 104003, 2, 201, 2),
                RewardRoller.roll(selected, loot, new Random(1)));
    }

    @Test
    void aQualifiedNameResolvesToTheSameTable() {
        var css = CssRewards.parse("""
                chest { --loot: "astaps.loot.surprise" 3; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 3),
                css.resolve("chest", Map.of()).loot());
        assertEquals(Map.of(202, 90, 104003, 3),
                RewardRoller.roll(css.resolve("chest", Map.of()), loot, new Random(1)));
    }

    @Test
    void listCascadesAsOnePropertyRatherThanAppending() {
        var css = CssRewards.parse("""
                chest[kind="common"] {
                    --loot: currency, 经验书;
                    --genshin-item-primogem: 2;
                }
                chest[kind="common"][level="6"] {
                    --loot: surprise 2;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1, "astaps.loot.经验书", 1),
                css.resolve("chest", Map.of("kind", "common", "level", "1")).loot());

        var selected = css.resolve("chest", Map.of("kind", "common", "level", "6"));
        assertEquals(Map.of("astaps.loot.surprise", 2), selected.loot());
        assertEquals(Map.of(201, 2), selected.items());
        assertEquals(Map.of(202, 60, 104003, 2, 201, 2),
                RewardRoller.roll(selected, loot, new Random(1)));
    }

    @Test
    void noneClearsLootButPreservesFixedItems() {
        var css = CssRewards.parse("""
                chest { --loot: currency, 经验书; --genshin-item-primogem: 2; }
                chest[level="6"] { --loot: none; }
                """, items, loot);
        var selected = css.resolve("chest", Map.of("level", "6"));
        assertEquals(Map.of(), selected.loot());
        assertEquals(Map.of(201, 2),
                RewardRoller.roll(selected, loot, new Random(1)));
    }

    @Test
    void aTableCanBeUsedByUnrelatedObjects() {
        var css = CssRewards.parse("""
                monster[rank="elite"] { --loot: currency; }
                chest[kind="common"] { --loot: currency 3; }
                """, items, loot);
        assertEquals(Map.of(202, 100),
                RewardRoller.roll(css.resolve("monster", Map.of("rank", "elite")), loot,
                        new Random(1)));
        assertEquals(Map.of(202, 300),
                RewardRoller.roll(css.resolve("chest", Map.of("kind", "common")), loot,
                        new Random(1)));
    }

    @Test
    void fixedItemQuantitiesOverrideRolledTotals() {
        var css = CssRewards.parse("""
                chest {
                    --loot: currency, 经验书;
                    --genshin-item-mora: 25;
                    --genshin-item-primogem: 2;
                }
                chest[scene="3"] { --genshin-item-mora: 0; }
                """, items, loot);
        assertEquals(Map.of(202, 25, 104003, 1, 201, 2),
                RewardRoller.roll(css.resolve("chest", Map.of()), loot, new Random(1)));
        assertEquals(Map.of(104003, 1, 201, 2),
                RewardRoller.roll(css.resolve("chest", Map.of("scene", "3")), loot,
                        new Random(1)));
    }

    @Test
    void selectorSpecificityBeatsLaterSourceOrder() {
        var css = CssRewards.parse("""
                chest[kind="common"][scene="3"] { --loot: surprise; }
                chest[kind="common"] { --loot: currency, 经验书; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 1),
                css.resolve("chest", Map.of("kind", "common", "scene", "3")).loot());
    }

    @Test
    void anElementSelectorOutranksAnEqualAttributeCountUniversalSelector() {
        var css = CssRewards.parse("""
                chest[kind="common"] { --loot: currency; }
                *[kind="common"] { --loot: surprise; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1),
                css.resolve("chest", Map.of("kind", "common")).loot());
    }

    @Test
    void unsupportedSelectorFamiliesAreRejectedRatherThanSilentlyMisprioritized() {
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest.special { --loot: currency; }", items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "#chest { --loot: currency; }", items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest > monster { --loot: currency; }", items, loot));
    }

    @Test
    void repeatedTablesInTheListAccumulateExecutionCount() {
        var css = CssRewards.parse("""
                chest { --loot: currency, currency 2; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 3),
                css.resolve("chest", Map.of()).loot());
    }

    @Test
    void laterLootDeclarationReplacesTheEarlierList() {
        var css = CssRewards.parse("""
                chest {
                    --loot: currency;
                    --loot: surprise;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 1),
                css.resolve("chest", Map.of()).loot());
    }

    @Test
    void invalidReferencesAndOldTablePropertiesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --loot: missing; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --loot: genshin.item.mora; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --astaps-loot-currency: 1; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --genshin-avatar-amber: 1; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { mora: 1; }
                """, items, loot));
    }

    @Test
    void quotedLevelRangesCoverTheSixSourceIntervals() {
        var css = CssRewards.parse("""
                chest[kind="common"] { --loot: currency; }
                chest[kind="common"][level="1~5"] { --loot: surprise; }
                chest[kind="common"][level="6~10"] { --loot: currency 2; }
                chest[kind="common"][level="11~15"] { --loot: surprise 3; }
                chest[kind="common"][level="16~20"] { --loot: currency 4; }
                chest[kind="common"][level="21~25"] { --loot: surprise 5; }
                chest[kind="common"][level="26~"] { --loot: currency 6; }
                """, items, loot);
        int[] levels = {1, 5, 6, 10, 11, 15, 16, 20, 21, 25, 26, 99};
        String[] names = {
                "surprise", "surprise", "currency", "currency",
                "surprise", "surprise", "currency", "currency",
                "surprise", "surprise", "currency", "currency"
        };
        int[] times = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6};
        for (int i = 0; i < levels.length; i++) {
            assertEquals(Map.of("astaps.loot." + names[i], times[i]),
                    css.resolve("chest", Map.of("kind", "common",
                            "level", Integer.toString(levels[i]))).loot(),
                    "level " + levels[i]);
        }
        for (String value : new String[]{"0", "unknown"}) {
            assertEquals(Map.of("astaps.loot.currency", 1),
                    css.resolve("chest", Map.of("kind", "common", "level", value)).loot());
        }
        assertEquals(Map.of("astaps.loot.currency", 1),
                css.resolve("chest", Map.of("kind", "common")).loot());
    }

    @Test
    void quotedRangesPreserveSelectorSpecificityAndExactLevelEquality() {
        var css = CssRewards.parse("""
                chest { --loot: currency; }
                chest[level="6~10"] { --loot: surprise 2; }
                chest[level="11~16"] { --loot: currency 3; }
                chest[kind="common"][level="16~20"] { --loot: surprise 4; }
                chest[level="30"] { --loot: currency 5; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1),
                css.resolve("chest", Map.of("level", "5")).loot());
        assertEquals(Map.of("astaps.loot.surprise", 2),
                css.resolve("chest", Map.of("level", "6")).loot());
        assertEquals(Map.of("astaps.loot.surprise", 2),
                css.resolve("chest", Map.of("level", "10")).loot());
        assertEquals(Map.of("astaps.loot.currency", 3),
                css.resolve("chest", Map.of("level", "11")).loot());
        assertEquals(Map.of("astaps.loot.surprise", 4),
                css.resolve("chest", Map.of("kind", "common", "level", "16")).loot());
        assertEquals(Map.of("astaps.loot.currency", 1),
                css.resolve("chest", Map.of("kind", "common", "level", "21")).loot());
        assertEquals(Map.of("astaps.loot.currency", 5),
                css.resolve("chest", Map.of("level", "30")).loot());
    }

    @Test
    void invalidRangesAreRejected() {
        for (String range : new String[]{"10~5", "a~5", "2~x", "~5"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    CssRewards.parse(
                            "chest[level=\"" + range + "\"] { --loot: currency; }",
                            items, loot), range);
        }
    }
    @Test
    void cssRangedCurrencyRollsInclusivelyAndOverridesLoot() {
        var css = CssRewards.parse("""
                chest {
                    --loot: currency;
                    --genshin-item-mora: 3~5;
                }
                """, items, loot);
        var selected = css.resolve("chest", Map.of());
        var observed = new java.util.HashSet<Integer>();
        var random = new Random(214);
        for (int i = 0; i < 100; i++) {
            int mora = RewardRoller.roll(selected, loot, random).get(202);
            assertTrue(mora >= 3 && mora <= 5);
            observed.add(mora);
        }
        assertEquals(java.util.Set.of(3, 4, 5), observed);
    }

    @Test
    void cssFixedAndRangedQuantitiesRespectCascadePrecedence() {
        var css = CssRewards.parse("""
                chest { --genshin-item-mora: 3~5; }
                chest[city="1"] { --genshin-item-mora: 9; }
                chest[city="1"][area="2"] { --genshin-item-mora: 11~12; }
                """, items, loot);
        assertEquals(9, RewardRoller.roll(
                css.resolve("chest", Map.of("city", "1")), loot, new Random(1))
                .get(202).intValue());
        int value = RewardRoller.roll(css.resolve("chest",
                Map.of("city", "1", "area", "2")), loot, new Random(2)).get(202);
        assertTrue(value == 11 || value == 12);
    }

}
