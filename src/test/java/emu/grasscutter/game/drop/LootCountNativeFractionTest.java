package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LootCountNativeFractionTest {
    @Test
    void fractionalNativeCountsUseStochasticRounding() {
        var sample = LootCount.parse("1.122");
        var random = new Random(71);
        int high = 0;
        for (int i = 0; i < 30_000; i++) {
            int value = sample.sample(random);
            assertTrue(value == 1 || value == 2);
            if (value == 2) high++;
        }
        assertEquals(0.122, high / 30_000.0, 0.015);
        assertEquals(3, LootCount.parse("3.0").sample(random));
        assertEquals(0, LootCount.parse("0").sample(random));
    }

    @Test
    void malformedNativeCountsMustNotBeRoundedSilently() {
        for (String value : new String[]{"-1.25", "1.2.3", "100000.5"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LootCount.parse(value), value);
        }
    }
}
