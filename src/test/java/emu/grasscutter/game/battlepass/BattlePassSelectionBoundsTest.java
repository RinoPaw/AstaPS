package emu.grasscutter.game.battlepass;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BattlePassSelectionBoundsTest {
    @Test
    void rejectsOutOfRangeSelectionBeforeAnyRewardLookup() {
        assertTrue(BattlePassSelectChestHelper.resolve(12345, 0).isEmpty());
        assertTrue(BattlePassSelectChestHelper.resolve(12345, -1).isEmpty());
        assertTrue(BattlePassSelectChestHelper.resolve(12345, 129).isEmpty());
        assertTrue(BattlePassSelectChestHelper.resolve(12345, Integer.MAX_VALUE).isEmpty());
    }
}
