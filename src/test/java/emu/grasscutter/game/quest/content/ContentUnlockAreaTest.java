package emu.grasscutter.game.quest.content;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ContentUnlockAreaTest {
    @Test
    void sameSceneOtherAreaDoesNotCountAsUnlocked() {
        var unlocked = Map.of(3, Set.of(1, 3), 4, Set.of(11));
        assertTrue(ContentUnlockArea.isUnlocked(unlocked, 3, 1));
        assertTrue(ContentUnlockArea.isUnlocked(unlocked, 3, 3));
        assertFalse(ContentUnlockArea.isUnlocked(unlocked, 3, 2));
        assertFalse(ContentUnlockArea.isUnlocked(unlocked, 4, 1));
        assertTrue(ContentUnlockArea.isUnlocked(unlocked, 4, 11));
    }

    @Test
    void twoAreaConditionsAccumulateRatherThanResetOnNextAreaEvent() {
        var unlocked = new java.util.HashMap<Integer, Set<Integer>>();
        var areaIds = new java.util.HashSet<Integer>();
        unlocked.put(3, areaIds);

        areaIds.add(1);
        assertTrue(ContentUnlockArea.isUnlocked(unlocked, 3, 1));
        assertFalse(ContentUnlockArea.isUnlocked(unlocked, 3, 2));

        areaIds.add(2);
        assertTrue(ContentUnlockArea.isUnlocked(unlocked, 3, 1));
        assertTrue(ContentUnlockArea.isUnlocked(unlocked, 3, 2));
    }

    @Test
    void unsetAndMalformedInputsNeverSatisfyArea() {
        assertFalse(ContentUnlockArea.isUnlocked(null, 3, 1));
        assertFalse(ContentUnlockArea.isUnlocked(Map.of(), 3, 1));
        assertFalse(ContentUnlockArea.isUnlocked(Map.of(3, Set.of(1)), 0, 1));
        assertFalse(ContentUnlockArea.isUnlocked(Map.of(3, Set.of(1)), 3, 0));
    }
}
