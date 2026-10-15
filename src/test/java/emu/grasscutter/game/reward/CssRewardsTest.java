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

    /** Pure table definitions: no region, level, object type or selector logic. */
    private final YamlLootTables loot = YamlLootTables.parse("""
            astaps.loot.currency:
              - {reward: genshin.item.mora, count: 100}
            astaps.loot.experience:
              - {reward: genshin.item.heros_wit}
            """, items::resolve);

    @Test
    void oneObjectCanSelectAndExecuteMultipleTables() {
        var css = CssRewards.parse("""
                chest[kind="common"] {
                    --astaps-loot-currency: 2;
                    --astaps-loot-experience: 1;
                    --genshin-item-primogem: 2;
                }
                """, items, loot);

        var selected = css.resolve("chest", Map.of("kind", "common"));
        assertEquals(Map.of(
                "astaps.loot.currency", 2,
                "astaps.loot.experience", 1), selected.loot());
        assertEquals(Map.of(201, 2), selected.items());
        assertEquals(Map.of(202, 200, 104003, 1, 201, 2),
                RewardRoller.roll(selected, loot, new Random(1)));
    }

    @Test
    void objectPropertiesOnlySelectAndOverrideExecutionCounts() {
        var css = CssRewards.parse("""
                chest[kind="common"] {
                    --astaps-loot-currency: 1;
                }
                chest[kind="common"][level="6"] {
                    --astaps-loot-currency: 2;
                    --astaps-loot-experience: 1;
                }
                chest[kind="common"][level="6"][scene="3"] {
                    --astaps-loot-experience: 0;
                }
                """, items, loot);

        assertEquals(Map.of("astaps.loot.currency", 1),
                css.resolve("chest", Map.of("kind", "common", "level", "1")).loot());
        assertEquals(Map.of("astaps.loot.currency", 2, "astaps.loot.experience", 1),
                css.resolve("chest", Map.of("kind", "common", "level", "6")).loot());

        var disabled = css.resolve("chest", Map.of("kind", "common", "level", "6", "scene", "3"));
        assertEquals(Map.of("astaps.loot.currency", 2, "astaps.loot.experience", 0),
                disabled.loot());
        assertEquals(Map.of(202, 200), RewardRoller.roll(disabled, loot, new Random(1)));
    }

    @Test
    void theSameTableMayServeUnrelatedRewardSources() {
        var css = CssRewards.parse("""
                monster[rank="elite"] { --astaps-loot-currency: 1; }
                chest[kind="common"] { --astaps-loot-currency: 3; }
                """, items, loot);
        assertEquals(Map.of(202, 100),
                RewardRoller.roll(css.resolve("monster", Map.of("rank", "elite")), loot,
                        new Random(1)));
        assertEquals(Map.of(202, 300),
                RewardRoller.roll(css.resolve("chest", Map.of("kind", "common")), loot,
                        new Random(1)));
    }

    @Test
    void fixedItemsReplaceRolledTotalsAndZeroSuppressesAnItem() {
        var css = CssRewards.parse("""
                chest {
                    --astaps-loot-currency: 3;
                    --astaps-loot-experience: 1;
                    --genshin-item-mora: 25;
                    --genshin-item-primogem: 2;
                }
                chest[scene="3"] {
                    --genshin-item-mora: 0;
                }
                """, items, loot);

        assertEquals(Map.of(202, 25, 201, 2, 104003, 1),
                RewardRoller.roll(css.resolve("chest", Map.of()), loot, new Random(2)));
        assertEquals(Map.of(201, 2, 104003, 1),
                RewardRoller.roll(css.resolve("chest", Map.of("scene", "3")), loot,
                        new Random(2)));
    }

    @Test
    void specificityBeatsLaterSourceOrder() {
        var css = CssRewards.parse("""
                chest[kind="common"][scene="3"] {
                    --astaps-loot-currency: 3;
                }
                chest[kind="common"] {
                    --astaps-loot-currency: 1;
                    --astaps-loot-experience: 2;
                }
                """, items, loot);

        assertEquals(Map.of("astaps.loot.currency", 3, "astaps.loot.experience", 2),
                css.resolve("chest", Map.of("kind", "common", "scene", "3")).loot());
    }

    @Test
    void rejectsUnknownTablesAndNonItemOrLootProperties() {
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --astaps-loot-not_defined: 1; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --genshin-avatar-amber: 1; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { --genshin-item-not_defined: 1; }
                """, items, loot));
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse("""
                chest { mora: 1; }
                """, items, loot));
    }

    @Test
    void laterCustomPropertyValueWinsInSameRule() {
        var css = CssRewards.parse("""
                chest {
                    --astaps-loot-currency: 1;
                    --astaps-loot-currency: 2;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 2),
                css.resolve("chest", Map.of()).loot());
    }
}
