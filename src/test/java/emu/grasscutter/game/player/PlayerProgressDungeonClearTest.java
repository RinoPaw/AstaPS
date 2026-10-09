package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlayerProgressDungeonClearTest {
    @Test
    void repeatSuccessEmitsQuestEventAgainButKeepsOneHistoryRecord() {
        var completed = new IntArrayList();
        var events = new ArrayList<Integer>();
        assertTrue(PlayerProgress.recordDungeonClear(
                completed, 1001, () -> events.add(1001)));
        assertFalse(PlayerProgress.recordDungeonClear(
                completed, 1001, () -> events.add(1001)));
        assertEquals(IntArrayList.wrap(new int[] {1001}), completed);
        assertEquals(List.of(1001, 1001), events);
    }

    @Test
    void threeDifferentDungeonsRetainHistoryAndEachRunEmits() {
        var completed = new IntArrayList();
        var events = new ArrayList<Integer>();
        for (int id : new int[] {1001, 1, 1003, 1}) {
            PlayerProgress.recordDungeonClear(completed, id, () -> events.add(id));
        }
        assertEquals(IntArrayList.wrap(new int[] {1001, 1, 1003}), completed);
        assertEquals(List.of(1001, 1, 1003, 1), events);
    }

    @Test
    void invalidDungeonIdsDoNotEmitOrMutateHistory() {
        var completed = new IntArrayList();
        var events = new ArrayList<Integer>();
        assertFalse(PlayerProgress.recordDungeonClear(completed, 0, () -> events.add(0)));
        assertFalse(PlayerProgress.recordDungeonClear(completed, -2, () -> events.add(-2)));
        assertTrue(completed.isEmpty());
        assertTrue(events.isEmpty());
    }
}
