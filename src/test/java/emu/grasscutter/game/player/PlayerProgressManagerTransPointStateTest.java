package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

final class PlayerProgressManagerTransPointStateTest {
    @Test
    void questLockPreservesPermanentUnlockState() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();
        unlocked.add(1720);

        assertTrue(PlayerProgressManager.applyQuestPointLock(forceLocked, 1720));
        assertTrue(unlocked.contains(1720));
        assertTrue(forceLocked.contains(1720));
    }

    @Test
    void releasingQuestLockDoesNotCreateASecondFirstUnlock() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();
        unlocked.add(1720);
        forceLocked.add(1720);

        var transition =
                PlayerProgressManager.applyTransPointUnlock(unlocked, forceLocked, 1720);

        assertTrue(transition.changed());
        assertFalse(transition.newlyUnlocked());
        assertTrue(transition.wasForceLocked());
        assertTrue(unlocked.contains(1720));
        assertFalse(forceLocked.contains(1720));
    }

    @Test
    void genuineFirstUnlockIsStillReportedAsNew() {
        var unlocked = new HashSet<Integer>();
        var forceLocked = new HashSet<Integer>();

        var transition =
                PlayerProgressManager.applyTransPointUnlock(unlocked, forceLocked, 6);

        assertTrue(transition.changed());
        assertTrue(transition.newlyUnlocked());
        assertFalse(transition.wasForceLocked());
        assertTrue(unlocked.contains(6));
    }
}
