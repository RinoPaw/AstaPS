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
    void namespacedRewardsCascadePerItem() {
        var css = CssItemRewards.parse("""
                chest[kind="common"] {
                    reward: "genshin:item:mora" 300;
                    reward: "genshin:item:heros_wit" 1;
                    reward: "genshin:item:primogem" 2;
                }
                chest[kind="common"][scene="3"] {
                    reward: "genshin:item:mora" 0;
                }
                chest[kind="exquisite"] {
                    reward: "genshin:item:mora" 500;
                }
                transPoint[kind="waypoint"] {
                    reward: "genshin:item:adventure_exp" 50;
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
                    reward: "genshin:item:mora" 25;
                }
                monster[rank="elite"][area="mondstadt"] {
                    reward: "genshin:item:mora" 40;
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
                    reward: "genshin:item:mora" 75;
                }
                chest[kind="common"] {
                    reward: "genshin:item:mora" 300;
                    reward: "genshin:item:primogem" 2;
                }
                """, items);
        assertEquals(Map.of(202, 75, 201, 2),
                css.resolve("chest", Map.of("kind", "common", "scene", "3")));
    }

    @Test
    void oldRewardPropertiesDoNotExist() {
        for (String property : new String[]{"mora", "primogems", "adventureExp", "item-202"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CssItemRewards.parse("chest { " + property + ": 300; }", items));
        }
    }

    @Test
    void typedReferencesStillResolveOnlyItems() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "genshin:avatar:amber" 1; }
                """, items));
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "genshin:item:missing" 1; }
                """, items));
    }

    @Test
    void sameResourceInOneRuleUsesLastValue() {
        var css = CssItemRewards.parse("""
                chest {
                    reward: "genshin:item:mora" 10;
                    reward: "genshin:item:mora" 20;
                }
                """, items);
        assertEquals(Map.of(202, 20), css.resolve("chest", Map.of()));
    }

    @Test
    void numericReferencesAreSupportedByTheSameResolver() {
        var css = CssItemRewards.parse("""
                chest { reward: 104003 2; }
                """, items);
        assertEquals(Map.of(104003, 2), css.resolve("chest", Map.of()));
    }

    @Test
    void invalidQuantityFailsParsing() {
        assertThrows(IllegalArgumentException.class, () -> CssItemRewards.parse("""
                chest { reward: "genshin:item:mora" -1; }
                """, items));
    }
}
