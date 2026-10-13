package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ChestRewardSourceTest {
    @Test
    void explicitChestIdTakesPrecedenceOverTag() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(ChestRewardSource.grant(1000100, 3, "battle chest", 26,
                (id, count) -> { calls.incrementAndGet(); return id == 1000100 && count == 3; },
                (tag, level) -> { fail("tag must not override the exact chest ID"); return true; }));
        assertEquals(1, calls.get());
    }

    @Test
    void invalidExplicitIdDoesNotFallBackToAnotherRewardPool() {
        assertFalse(ChestRewardSource.grant(1000100, 1, "battle chest", 26,
                (id, count) -> false,
                (tag, level) -> { fail("invalid native ID must remain unclaimable"); return true; }));
    }

    @Test
    void tagIsUsedWhenNoExplicitChestIdExists() {
        assertTrue(ChestRewardSource.grant(0, 0, "battle chest", 26,
                (id, count) -> { fail("no native ID exists"); return false; },
                (tag, level) -> tag.equals("battle chest") && level == 26));
    }

    @Test
    void absentRewardMetadataCannotProduceFreeLoot() {
        assertFalse(ChestRewardSource.grant(0, 1, "  ", 0,
                (id, count) -> { fail("missing ID"); return true; },
                (tag, level) -> { fail("missing tag"); return true; }));
    }

    @Test
    void malformedZeroCountsAndLevelsNormalizeForConfiguredSources() {
        assertTrue(ChestRewardSource.grant(123, 0, null, 0,
                (id, count) -> id == 123 && count == 1, (tag, level) -> false));
        assertTrue(ChestRewardSource.grant(0, 0, "tag", 0,
                (id, count) -> false, (tag, level) -> tag.equals("tag") && level == 1));
    }
}
