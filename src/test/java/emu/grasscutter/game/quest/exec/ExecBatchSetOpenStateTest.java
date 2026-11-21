package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ExecBatchSetOpenStateTest {
    @Test
    void parsesTheResourceBatchWithoutDroppingTrailingEntries() {
        var pairs = ExecBatchSetOpenState.parsePairs("7010,7026,7060", "0,1,0");
        assertNotNull(pairs);
        assertArrayEquals(new int[] {7010, 0}, pairs[0]);
        assertArrayEquals(new int[] {7026, 1}, pairs[1]);
        assertArrayEquals(new int[] {7060, 0}, pairs[2]);
    }

    @Test
    void rejectsTheWholeBatchBeforeAnyStateCanChange() {
        assertNull(ExecBatchSetOpenState.parsePairs("7010,7026", "0"));
        assertNull(ExecBatchSetOpenState.parsePairs("7010,", "0,1"));
        assertNull(ExecBatchSetOpenState.parsePairs("7010,7026", "0,bad"));
        assertNull(ExecBatchSetOpenState.parsePairs("7010,-1", "0,1"));
    }
}
