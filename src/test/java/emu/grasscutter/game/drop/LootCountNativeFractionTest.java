package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LootCountNativeFractionTest {
    @Test
    void nativeFractionalExpectationsBecomeExplicitDistributions() {
        assertEquals("1:87.8, 2:12.2", LootCount.fromNativeCount("1.122"));
        assertEquals("2:44.44, 3:55.56", LootCount.fromNativeCount("2.5556"));
        assertEquals("3:81.55, 4:18.45", LootCount.fromNativeCount("3.1845"));
        assertEquals("3", LootCount.fromNativeCount("3.000"));
        assertEquals("0:99.9, 1:0.1", LootCount.fromNativeCount("0.001"));
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
    void explicitOutcomesHaveExactBoundaries() {
        var random = new Random(1);
        assertEquals(2, LootCount.parse("2:1,3:0").sample(random));
        assertEquals(3, LootCount.parse("2:0,3:1").sample(random));
        assertEquals(4, LootCount.parse("2:0,4:1").sample(random));
        assertEquals(0, LootCount.parse("0").sample(random));
        assertEquals(0, LootCount.parse("0:1,1:0").sample(random));
        assertEquals(1, LootCount.parse("0:0,1:1").sample(random));
    }

    @Test
    void decimalProbabilitiesAreAcceptedButDecimalCountsAreNot() {
        var roll = LootCount.parse("1:1, 2:7");
        var random = new Random(12);
        for (int i = 0; i < 500; i++) {
            int value = roll.sample(random);
            assertTrue(value == 1 || value == 2);
        }
        assertEquals(2, LootCount.parse("2:7").sample(random));
        for (String invalid : new String[] {
                "2, 3:41.4", "2:58.6, 3", "2:0, 3:0", "2:1, 2:2"
        }) {
            assertThrows(IllegalArgumentException.class, () -> LootCount.parse(invalid), invalid);
        }
        for (String value : new String[]{"1.122", "3.0", "-1.25", "1.2.3",
                "2+1:100.01", "2+1:-1", "2+1:abc", "2+1:55.56:5",
                "2+0:50", "100000+1:1", "1+1:10.5.5"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LootCount.parse(value), value);
        }
        assertEquals("100000:50, 100001:50", LootCount.fromNativeCount("100000.5"));
        assertEquals("100001", LootCount.fromNativeCount("100001"));
        assertEquals("2147483646:50, 2147483647:50",
                LootCount.fromNativeCount("2147483646.5"));
        assertEquals("2147483647", LootCount.fromNativeCount("2147483647.0"));
        for (String value : new String[]{"-1.25", "1.2.3", "2147483647.5", "2147483648"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LootCount.fromNativeCount(value), value);
        }
    }
}
