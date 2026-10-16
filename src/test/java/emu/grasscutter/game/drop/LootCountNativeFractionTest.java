package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LootCountNativeFractionTest {
    @Test
    void nativeFractionalExpectationsBecomeExplicitBonusRolls() {
        assertEquals("1+1:12.2", LootCount.fromNativeCount("1.122"));
        assertEquals("2+1:55.56", LootCount.fromNativeCount("2.5556"));
        assertEquals("3+1:18.45", LootCount.fromNativeCount("3.1845"));
        assertEquals("3", LootCount.fromNativeCount("3.000"));
        assertEquals("0+1:0.1", LootCount.fromNativeCount("0.001"));
        assertEquals("2;3", LootCount.fromNativeCount("2;3"));

        var sample = LootCount.parse(LootCount.fromNativeCount("2.5556"));
        var random = new Random(71);
        int high = 0;
        for (int i = 0; i < 30_000; i++) {
            int value = sample.sample(random);
            assertTrue(value == 2 || value == 3);
            if (value == 3) high++;
        }
        assertEquals(0.5556, high / 30_000.0, 0.015);
    }

    @Test
    void optionalAdditionalExecutionsHaveExactBoundaries() {
        var random = new Random(1);
        assertEquals(2, LootCount.parse("2+1:0").sample(random));
        assertEquals(3, LootCount.parse("2+1:100").sample(random));
        assertEquals(4, LootCount.parse("2+2:100").sample(random));
        assertEquals(0, LootCount.parse("0").sample(random));
        assertEquals(0, LootCount.parse("0+1:0").sample(random));
        assertEquals(1, LootCount.parse("0+1:100").sample(random));
    }

    @Test
    void decimalProbabilitiesAreAcceptedButDecimalCountsAreNot() {
        var roll = LootCount.parse("1:12.5,2:87.5");
        var random = new Random(12);
        for (int i = 0; i < 500; i++) {
            int value = roll.sample(random);
            assertTrue(value == 1 || value == 2);
        }
        for (String value : new String[]{"1.122", "3.0", "-1.25", "1.2.3",
                "2+1:100.01", "2+1:-1", "2+1:abc", "2+1:55.56:5",
                "2+0:50", "100000+1:1", "1+1:10.5.5"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LootCount.parse(value), value);
        }
        for (String value : new String[]{"-1.25", "1.2.3", "100000.5"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LootCount.fromNativeCount(value), value);
        }
    }
}
