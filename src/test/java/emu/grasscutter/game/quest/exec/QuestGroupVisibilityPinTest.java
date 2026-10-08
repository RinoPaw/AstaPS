package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Suite resets and failed refreshes must not permanently pin a scene group. */
final class QuestGroupVisibilityPinTest {
    @Test
    void activeOverridesAreProtectedAgainstVisibilityUnload() {
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(false, true, 2, false));
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(true, true, 1, false));
    }

    @Test
    void resetUnpinsUnlessAnotherQuestStillOwnsTheGroup() {
        assertFalse(ExecRefreshGroupSuite.pinAfterRefresh(true, true, 0, false));
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(true, true, 0, true));
    }

    @Test
    void failedRefreshNeverChangesPreviousPinState() {
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(true, false, 0, false));
        assertFalse(ExecRefreshGroupSuite.pinAfterRefresh(false, false, 2, false));
    }
}
