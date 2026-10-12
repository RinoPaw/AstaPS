package emu.grasscutter.game.battlepass;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class BattlePassSelectionBoundsTest {
    @Test
    void groupIndexMustAppearInThe7Point1ClientSelection() {
        assertTrue(BattlePassSelectChestHelper.isAllowedGroupedIndex(116018, 61));
        assertFalse(BattlePassSelectChestHelper.isAllowedGroupedIndex(116018, 62));
        assertTrue(BattlePassSelectChestHelper.isAllowedGroupedIndex(115016, 24));
        assertFalse(BattlePassSelectChestHelper.isAllowedGroupedIndex(115016, 25));
        assertFalse(BattlePassSelectChestHelper.isAllowedGroupedIndex(115016, -1));
        assertFalse(BattlePassSelectChestHelper.isAllowedGroupedIndex(999999, 129));
    }

    @Test
    void noUnknownChestOrGapCanExtendRewardIds() {
        var known = List.of(2001, 2002, 2003);
        assertTrue(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 61, known));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(116018, 62, known));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(999999, 4, known));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(
                116018, 4, List.of(2001, 2003, 2004)));
        assertFalse(BattlePassSelectChestHelper.permitsGroupedExtension(
                116018, 3, known));
    }
}
