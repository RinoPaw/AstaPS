package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CssItemRewardsTest {
    private final ItemIdRegistry items = new ItemIdRegistry(Map.of(
            102, "Adventure EXP",
            201, "Primogem",
            202, "Mora",
            104003, "Hero's Wit"));

    @Test
    void parsesNamespacedItemsAndPerItemOverrides() {
        var css = CssItemRewards.parse("""
                chest[kind="common"] {
                    reward: "genshin:item:mora" 300;
                    reward: "genshin:item:heros_wit" 1;
                    primogems: 2;
                }
                chest[kind="common"][scene="3"] {
                    reward: "genshin:item:mora" 0;
                }
                chest[kind="exquisite"] { item-202: 500; }
                transPoint[kind="waypoint"] { adventureExp: 50; }
                """, items);
        assertEquals(Map.of(202, 300, 104003, 1, 201, 2),
                css.resolve("chest", Map.of("kind", "common")));
        assertEquals(Map.of(202, 0, 104003, 1, 201, 2),
                css.resolve("chest", Map.of("kind", "common", "scene", "3")));
        assertEquals(Map.of(202, 500),
                css.resolve("chest", Map.of("kind", "exquisite")));
        assertEquals(Map.of(102, 50),
                css.resolve("transPoint", Map.of("kind", "waypoint")));
    }

    @Test
    void rejectsOtherResourceTypesAndUnknownItems() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "genshin:avatar:amber" 1; }
                """, items));
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "genshin:item:missing" 1; }
                """, items));
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "other:item:mora" 1; }
                """, items));
    }

    @Test
    void rejectsDuplicateReferencesToSameItem() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest {
                    reward: "genshin:item:mora" 10;
                    item-202: 20;
                }
                """, items));
    }

    @Test
    void rejectsBadAmountsAndUnsupportedSelectors() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "genshin:item:mora" -1; }
                """, items));
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest > transPoint { reward: "genshin:item:mora" 1; }
                """, items));
    }

    @Test
    void acceptsBareNumericItemIdsForMigration() {
        var css = CssItemRewards.parse("""
                chest[kind="common"] { reward: 104003 2; }
                """, items);
        assertEquals(Map.of(104003, 2), css.resolve("chest", Map.of("kind", "common")));
    }
}
