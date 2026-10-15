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
    void namespacedItemPropertiesCascadeIndependently() {
        var css = CssItemRewards.parse("""
                chest[kind="common"] {
                    --genshin-item-mora: 300;
                    --genshin-item-heros_wit: 1;
                    --genshin-item-primogem: 2;
                }
                chest[kind="common"][scene="3"] {
                    --genshin-item-mora: 0;
                }
                chest[kind="exquisite"] {
                    --genshin-item-mora: 500;
                }
                transPoint[kind="waypoint"] {
                    --genshin-item-adventure_exp: 50;
                }
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
    void sourceTypesAndAttributesAreOpen() {
        var css = CssItemRewards.parse("""
                monster[rank="elite"] {
                    --genshin-item-mora: 25;
                }
                monster[rank="elite"][area="mondstadt"] {
                    --genshin-item-mora: 40;
                }
                """, items);
        assertEquals(Map.of(202, 25), css.resolve("monster", Map.of("rank", "elite")));
        assertEquals(Map.of(202, 40),
                css.resolve("monster", Map.of("rank", "elite", "area", "mondstadt")));
        assertTrue(css.resolve("monster", Map.of("rank", "normal")).isEmpty());
    }

    @Test
    void selectorSpecificityBeatsSourceOrder() {
        var css = CssItemRewards.parse("""
                chest[kind="common"][scene="3"] {
                    --genshin-item-mora: 75;
                }
                chest[kind="common"] {
                    --genshin-item-mora: 300;
                    --genshin-item-primogem: 2;
                }
                """, items);
        assertEquals(Map.of(202, 75, 201, 2),
                css.resolve("chest", Map.of("kind", "common", "scene", "3")));
    }

    @Test
    void noLegacyOrRepeatedRewardProperty() {
        for (String property : new String[]{
                "mora", "primogems", "adventureExp", "item-202", "reward",
                "--genshin--item--mora"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> CssItemRewards.parse("chest { " + property + ": 300; }", items));
        }
    }

    @Test
    void typedReferencesResolveOnlyItems() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { --genshin-avatar-amber: 1; }
                """, items));
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { --genshin-item-missing: 1; }
                """, items));
    }

    @Test
    void sameCustomPropertyInOneRuleUsesLastValue() {
        var css = CssItemRewards.parse("""
                chest {
                    --genshin-item-mora: 10;
                    --genshin-item-mora: 20;
                }
                """, items);
        assertEquals(Map.of(202, 20), css.resolve("chest", Map.of()));
    }

    @Test
    void invalidQuantityFailsParsing() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { --genshin-item-mora: -1; }
                """, items));
    }
}
