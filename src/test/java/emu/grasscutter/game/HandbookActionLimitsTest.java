package emu.grasscutter.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HandbookActionLimitsTest {
    @Test
    void itemAmountRejectsInvalidAndHugeValues() {
        assertEquals(-1, HandbookActionLimits.itemAmount(Long.MAX_VALUE));
        assertEquals(-1, HandbookActionLimits.itemAmount(HandbookActionLimits.MAX_ITEM_AMOUNT + 1L));
        assertEquals(-1, HandbookActionLimits.itemAmount(0));
        assertEquals(-1, HandbookActionLimits.itemAmount(-1));
        assertEquals(
                HandbookActionLimits.MAX_ITEM_AMOUNT,
                HandbookActionLimits.itemAmount(HandbookActionLimits.MAX_ITEM_AMOUNT));
    }

    @Test
    void spawnAmountRejectsInvalidAndHugeValues() {
        assertEquals(-1, HandbookActionLimits.spawnAmount(Long.MAX_VALUE));
        assertEquals(-1, HandbookActionLimits.spawnAmount(HandbookActionLimits.MAX_SPAWN_AMOUNT + 1L));
        assertEquals(-1, HandbookActionLimits.spawnAmount(0));
        assertEquals(-1, HandbookActionLimits.spawnAmount(-1));
        assertEquals(
                HandbookActionLimits.MAX_SPAWN_AMOUNT,
                HandbookActionLimits.spawnAmount(HandbookActionLimits.MAX_SPAWN_AMOUNT));
    }
}
