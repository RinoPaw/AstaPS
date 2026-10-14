package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ChestRandomInlineCssTest {
    private static String template() throws Exception {
        try (var in = ChestRandomInlineCssTest.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void bundledCommonFallbackRollsWhileNativePoolDoesNot() throws Exception {
        var config = ChestRewardConfig.parse(template());
        var nativeChest = config.resolve(ChestRewardConfig.Kind.COMMON, 3, 1, 0);
        var missingPool = config.resolve(ChestRewardConfig.Kind.COMMON, 3, 1, 0, true);
        assertTrue(nativeChest.randomRolls().isEmpty());
        assertEquals(3, missingPool.randomRolls().size());
        assertEquals(1000, missingPool.amounts().get(202));
        assertFalse(missingPool.amounts().containsKey(104011));
        assertEquals(Map.of(104011, 1, 104001, 1, 104002, 1),
                ChestRandomDrops.sample(missingPool.randomRolls(), new Random() {
                    @Override public int nextInt(int bound) { return 0; }
                }));
        assertEquals(Map.of(104011, 3),
                ChestRandomDrops.sample(missingPool.randomRolls(), new Random() {
                    @Override public int nextInt(int bound) { return bound - 1; }
                }));
    }

    @Test
    void moreSpecificFixedZeroSuppressesRandomItem() throws Exception {
        var config = ChestRewardConfig.parse(template() + """
                chest[kind="common"][pool="missing"][scene="3"] {
                    item-104001: 0;
                }
                """);
        var selected = config.resolve(ChestRewardConfig.Kind.COMMON, 3, 1, 0, true);
        assertEquals(0, selected.amounts().get(104001));
        assertTrue(selected.randomRolls().stream().noneMatch(r -> r.itemId() == 104001));
    }

    @Test
    void rejectsInvalidProbabilitiesAndAccidentalNativePoolRolls() throws Exception {
        for (String declaration : new String[] {
                "random-104001: 1 3 10001;",
                "random-104001: 3 1 5000;",
                "random-104001: 1 2;",
                "random-104001: -1 2 100;",
                "random-102: 1 2 10000;"
        }) {
            assertThrows(IllegalArgumentException.class, () -> ChestRewardConfig.parse(
                    template() + "chest[kind=\"common\"][pool=\"missing\"] {"
                            + declaration + "}"));
        }
        assertThrows(IllegalArgumentException.class, () -> ChestRewardConfig.parse(
                template() + "chest[kind=\"common\"] { random-104001: 1 3 5000; }"));
    }
}
