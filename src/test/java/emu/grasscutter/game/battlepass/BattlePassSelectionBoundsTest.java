package emu.grasscutter.game.battlepass;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

final class BattlePassSelectionBoundsTest {
    @Test
    void rejectsOutOfRangeSelectionBeforeAnyRewardLookup() {
        assertTrue(BattlePassSelectChestHelper.resolve(12345, 0).isEmpty());
        assertTrue(BattlePassSelectChestHelper.resolve(12345, -1).isEmpty());
        assertTrue(BattlePassSelectChestHelper.resolve(12345, 129).isEmpty());
        assertTrue(BattlePassSelectChestHelper.resolve(12345, Integer.MAX_VALUE).isEmpty());
    }

    @Test
    void groupedCompatibilityPermitsOnlyVerifiedChestOptions() {
        var prefix = List.of(2001, 2002, 2003);
        assertTrue(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 61, prefix));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 62, prefix));
        assertTrue(BattlePassSelectChestHelper.permitsGroupedExtension(115016, 24, prefix));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(115016, 25, prefix));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(999999, 4, prefix));
    }

    @Test
    void rejectsGapsInSourceOptionsBeforeExtrapolating() {
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 10,
                List.of(2001, 2003, 2004)));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 4,
                List.of(2001, 2002, 2003, 2004)));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 5,
                List.of(2001, 2002, -1)));
    }
}
