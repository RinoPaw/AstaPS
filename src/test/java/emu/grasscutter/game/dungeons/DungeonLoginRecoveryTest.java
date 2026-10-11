package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.game.world.Position;
import org.junit.jupiter.api.Test;

final class DungeonLoginRecoveryTest {
    @Test
    void restoresRecordedWorldEntry() {
        assertEquals(3, DungeonLoginRecovery.chooseWorldScene(
                3, new Position(100, 200, 300), id -> id == 3));
        assertEquals(4, DungeonLoginRecovery.chooseWorldScene(
                4, new Position(100, 200, 300), id -> id == 4));
    }

    @Test
    void oldDungeonSaveWithoutEntryFallsBackToStartWorld() {
        assertEquals(3, DungeonLoginRecovery.chooseWorldScene(
                0, null, id -> true));
        assertEquals(3, DungeonLoginRecovery.chooseWorldScene(
                40771, new Position(10, 20, 30), id -> id == 3));
        assertEquals(3, DungeonLoginRecovery.chooseWorldScene(
                4, new Position(Float.NaN, 0, 1), id -> id == 4));
        assertEquals(3, DungeonLoginRecovery.chooseWorldScene(
                4, Position.ZERO, id -> id == 4));
        assertEquals(false, DungeonLoginRecovery.hasValidWorldEntry(
                3, null, id -> id == 3));
        assertEquals(true, DungeonLoginRecovery.hasValidWorldEntry(
                3, new Position(10, 20, 30), id -> id == 3));
    }
}
