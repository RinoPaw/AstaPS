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
}
