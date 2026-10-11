package emu.grasscutter.server.born;

/**
 * The single fresh-player lifecycle from character creation to the first playable quest.
 *
 * <p>Native selection and automatic birth differ only in their world-entry trigger. Both must
 * complete the same scene-ready protocol before Quest 351 may start. Transitions are synchronized
 * because native world login runs on a worker while scene requests arrive on the logic thread.
 */
public final class FreshPlayerLifecycle {
    public enum Mode { NATIVE_INTRO, SCENE_READY_ONLY }
    public enum Phase {
        WAITING_FOR_NATIVE_HANDOFF,
        WORLD_LOGIN,
        WORLD_LOGIN_ACTIVE,
        SCENE_READY,
        QUEST_BOOTSTRAP,
        COMPLETE
    }

    private final Mode mode;
    private Phase phase;
    private int completedPauseCycles;
    private boolean sawUnpaused;

    public FreshPlayerLifecycle(Mode mode) {
        this.mode = mode;
        this.phase = mode == Mode.NATIVE_INTRO
                ? Phase.WAITING_FOR_NATIVE_HANDOFF : Phase.WORLD_LOGIN;
    }

    public Mode mode() {
        return mode;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized int completedPauseCycles() {
        return completedPauseCycles;
    }

    /** Returns true only on the second native false-to-true pause boundary. */
    public synchronized boolean observeNativePause(boolean paused) {
        if (mode != Mode.NATIVE_INTRO || phase != Phase.WAITING_FOR_NATIVE_HANDOFF)
            return false;
        if (!paused) {
            sawUnpaused = true;
            return false;
        }
        if (!sawUnpaused) return false;
        sawUnpaused = false;
        completedPauseCycles++;
        if (completedPauseCycles < 2) return false;
        phase = Phase.WORLD_LOGIN;
        return true;
    }

    /** The initial scene-ready request is held while cold world login is running. */
    public synchronized boolean mustDeferSceneReady() {
        return phase == Phase.WAITING_FOR_NATIVE_HANDOFF
                || phase == Phase.WORLD_LOGIN
                || phase == Phase.WORLD_LOGIN_ACTIVE;
    }

    /** Claims the world-login worker exactly once, even if the client repeats the login request. */
    public synchronized boolean claimWorldLogin() {
        if (phase != Phase.WORLD_LOGIN) return false;
        phase = Phase.WORLD_LOGIN_ACTIVE;
        return true;
    }

    public synchronized boolean worldLoginCompleted() {
        if (phase != Phase.WORLD_LOGIN_ACTIVE) return false;
        phase = Phase.SCENE_READY;
        return true;
    }

    /** Only the first post-enter-scene handshake owns the quest bootstrap. */
    public synchronized boolean claimQuestBootstrap() {
        if (phase != Phase.SCENE_READY) return false;
        phase = Phase.QUEST_BOOTSTRAP;
        return true;
    }

    public synchronized void questBootstrapCompleted() {
        if (phase != Phase.QUEST_BOOTSTRAP)
            throw new IllegalStateException("Quest bootstrap completed in " + phase);
        phase = Phase.COMPLETE;
    }

    public synchronized void questBootstrapFailed() {
        if (phase == Phase.QUEST_BOOTSTRAP) phase = Phase.SCENE_READY;
    }

    /** A failed world-login attempt may be retried only by a new native pause boundary. */
    public synchronized void nativeWorldLoginFailed() {
        if (mode != Mode.NATIVE_INTRO || phase != Phase.WORLD_LOGIN_ACTIVE) return;
        phase = Phase.WAITING_FOR_NATIVE_HANDOFF;
        completedPauseCycles = 0;
        sawUnpaused = false;
    }
}
