package emu.grasscutter.server.born;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FreshPlayerLifecycleTest {
    @Test
    void nativeIntroWaitsForTwoPauseCyclesThenSceneReadyAndBootstrapsOnce() {
        var flow = new FreshPlayerLifecycle(FreshPlayerLifecycle.Mode.NATIVE_INTRO);
        assertTrue(flow.mustDeferSceneReady());
        assertFalse(flow.observeNativePause(true));
        assertFalse(flow.observeNativePause(false));
        assertFalse(flow.observeNativePause(true));
        assertEquals(1, flow.completedPauseCycles());
        assertFalse(flow.observeNativePause(false));
        assertTrue(flow.observeNativePause(true));
        assertFalse(flow.observeNativePause(false));
        assertEquals(FreshPlayerLifecycle.Phase.WORLD_LOGIN, flow.phase());
        assertFalse(flow.claimQuestBootstrap());
        assertTrue(flow.claimWorldLogin());
        assertFalse(flow.claimWorldLogin());
        assertTrue(flow.worldLoginCompleted());
        assertFalse(flow.mustDeferSceneReady());
        assertTrue(flow.claimQuestBootstrap());
        assertFalse(flow.claimQuestBootstrap());
        flow.questBootstrapCompleted();
        assertEquals(FreshPlayerLifecycle.Phase.COMPLETE, flow.phase());
        assertFalse(flow.claimQuestBootstrap());
    }

    @Test
    void automaticBirthUsesTheSameSceneReadyQuestBoundary() {
        var flow = new FreshPlayerLifecycle(FreshPlayerLifecycle.Mode.SCENE_READY_ONLY);
        assertEquals(FreshPlayerLifecycle.Phase.WORLD_LOGIN, flow.phase());
        assertFalse(flow.observeNativePause(false));
        assertFalse(flow.observeNativePause(true));
        assertTrue(flow.mustDeferSceneReady());
        assertFalse(flow.claimQuestBootstrap());
        assertTrue(flow.claimWorldLogin());
        assertTrue(flow.mustDeferSceneReady());
        assertTrue(flow.worldLoginCompleted());
        assertTrue(flow.claimQuestBootstrap());
        flow.questBootstrapCompleted();
        assertFalse(flow.worldLoginCompleted());
    }

    @Test
    void failedBootstrapCanRetryButCannotRunConcurrently() {
        var flow = new FreshPlayerLifecycle(FreshPlayerLifecycle.Mode.SCENE_READY_ONLY);
        assertTrue(flow.claimWorldLogin());
        assertTrue(flow.worldLoginCompleted());
        assertTrue(flow.claimQuestBootstrap());
        assertFalse(flow.claimQuestBootstrap());
        flow.questBootstrapFailed();
        assertTrue(flow.claimQuestBootstrap());
        flow.questBootstrapCompleted();
        assertThrows(IllegalStateException.class, flow::questBootstrapCompleted);
    }

    @Test
    void failedNativeWorldEntryReturnsToTheNativeBoundary() {
        var flow = new FreshPlayerLifecycle(FreshPlayerLifecycle.Mode.NATIVE_INTRO);
        flow.observeNativePause(false);
        flow.observeNativePause(true);
        flow.observeNativePause(false);
        assertTrue(flow.observeNativePause(true));
        assertTrue(flow.claimWorldLogin());
        flow.nativeWorldLoginFailed();
        assertEquals(FreshPlayerLifecycle.Phase.WAITING_FOR_NATIVE_HANDOFF, flow.phase());
        assertEquals(0, flow.completedPauseCycles());
    }
}
