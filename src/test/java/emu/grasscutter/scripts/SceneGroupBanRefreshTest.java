package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A protected combat suite must be able to return to the quest's ambient suite. */
final class SceneGroupBanRefreshTest {
    @Test
    void firstProtectedToOrdinaryRefreshDefersOnlyOnce() {
        assertTrue(SceneScriptManager.shouldDeferBanRefresh(true, 0, 2));
        assertFalse(SceneScriptManager.shouldDeferBanRefresh(true, 2, 2));
    }

    @Test
    void switchingDestinationsReestablishesPendingTarget() {
        assertTrue(SceneScriptManager.shouldDeferBanRefresh(true, 1, 2));
    }

    @Test
    void ordinaryOrProtectedTargetsDoNotNeedDeferral() {
        assertFalse(SceneScriptManager.shouldDeferBanRefresh(false, 0, 2));
        assertFalse(SceneScriptManager.shouldDeferBanRefresh(false, 0, 1));
    }
}
