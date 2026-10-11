package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEnterScenePeerNotify;
import emu.grasscutter.server.packet.send.PacketEnterSceneReadyRsp;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates the fresh-player handoff from birth data to the first Quest 351 scene. */
public final class BornIntroGate {
    private static final int STARTER_SCENE_ID = 3;
    private static final int STARTER_STATUE_POINT_ID = 7;

    // Serialize overlapping first-world loads for the same player, including replacement
    // connections. Striped locks avoid unbounded per-UID lock retention.
    private static final Object[] WORLD_LOGIN_LOCKS = new Object[128];
    static {
        for (int i = 0; i < WORLD_LOGIN_LOCKS.length; i++) {
            WORLD_LOGIN_LOCKS[i] = new Object();
        }
    }

    // Index by UID for cleanup, but bind each transition to its originating session.
    // Reconnections rebuild an interrupted bootstrap rather than sharing a half-built world.
    private static final Map<Integer, State> FRESH_PLAYER_BOOTSTRAPS = new ConcurrentHashMap<>();

    private static final class State {
        private final GameSession originSession;
        private final FreshPlayerLifecycle lifecycle;
        private volatile boolean disconnected;
        private boolean earlySceneEntrySent;
        private GameSession earlySceneEntrySession;
        private boolean sceneReadyDeferred;
        private GameSession deferredSceneReadySession;

        private State(FreshPlayerLifecycle.Mode mode, GameSession originSession) {
            this.originSession = originSession;
            this.lifecycle = new FreshPlayerLifecycle(mode);
        }
    }

    private BornIntroGate() {}

    private static int uidOf(GameSession session) {
        var player = session == null ? null : session.getPlayer();
        return player == null ? 0 : player.getUid();
    }

    private static State stateFor(GameSession session) {
        int uid = uidOf(session);
        return uid > 0 ? FRESH_PLAYER_BOOTSTRAPS.get(uid) : null;
    }

    private static void remove(GameSession session) {
        int uid = uidOf(session);
        if (uid > 0) {
            var state = FRESH_PLAYER_BOOTSTRAPS.get(uid);
            if (state != null && state.originSession == session) {
                FRESH_PLAYER_BOOTSTRAPS.remove(uid, state);
            }
        }
    }

    /**
     * Returns true if an in-flight world-login worker now owns final cleanup.
     * The logic thread must not call Player.onLogout concurrently with Player.onLogin.
     */
    public static boolean onSessionDisconnected(GameSession session) {
        State state = stateFor(session);
        if (state == null || state.originSession != session) return false;
        boolean workerOwnsCleanup;
        synchronized (state) {
            workerOwnsCleanup =
                    state.lifecycle.phase() == FreshPlayerLifecycle.Phase.WORLD_LOGIN_ACTIVE;
            state.disconnected = true;
            FRESH_PLAYER_BOOTSTRAPS.remove(uidOf(session), state);
        }
        return workerOwnsCleanup;
    }

    /** Begins the native 7.1 post-born intro gate after SetPlayerBornData succeeds. */
    public static void armNativeIntro(GameSession session) {
        int uid = uidOf(session);
        if (uid <= 0) return;
        FRESH_PLAYER_BOOTSTRAPS.put(uid, new State(FreshPlayerLifecycle.Mode.NATIVE_INTRO, session));
    }

    /** Compatibility alias retained for existing native-intro callers. */
    public static void arm(GameSession session) {
        armNativeIntro(session);
    }

    /**
     * Begins the automatic/skip-intro path. World login may start immediately, while Quest 351 is
     * still held until PostEnterSceneRsp so quest actors initialize against a ready playable scene.
     */
    public static void armSceneReady(GameSession session) {
        int uid = uidOf(session);
        if (uid <= 0) return;

        State state = new State(FreshPlayerLifecycle.Mode.SCENE_READY_ONLY, session);
        FRESH_PLAYER_BOOTSTRAPS.put(uid, state);
    }

    /** Marks Player.onLogin complete for the automatic path and resumes any early scene-ready. */
    public static void markWorldLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null || state.originSession != session) return;

        GameSession deferredReadySession = null;
        synchronized (state) {
            if (!state.lifecycle.worldLoginCompleted()) return;
            if (state.sceneReadyDeferred) {
                deferredReadySession = state.deferredSceneReadySession;
                state.sceneReadyDeferred = false;
                state.deferredSceneReadySession = null;
            }
        }

        if (deferredReadySession != null) {
            resumeSceneReady(deferredReadySession);
        }
    }

    /** True while either fresh-player path still owns the first-scene Quest bootstrap. */
    public static boolean isFreshPlayerBootstrap(GameSession session) {
        State state = stateFor(session);
        return state != null && state.originSession == session;
    }

    /** Compatibility alias for older call sites. */
    public static boolean isAwaiting(GameSession session) {
        return isFreshPlayerBootstrap(session);
    }

    public static boolean isNativeIntro(GameSession session) {
        State state = stateFor(session);
        return state != null && state.originSession == session
                && state.lifecycle.mode() == FreshPlayerLifecycle.Mode.NATIVE_INTRO;
    }

    /**
     * Native birth sends scene-entry early at the intro cutover. Suppress only the duplicate login
     * scene-entry emitted later by Player.onLogin on that same connection. Automatic birth never
     * uses this suppression.
     */
    public static boolean shouldSuppressLoginSceneEntry(GameSession session) {
        State state = stateFor(session);
        if (state == null || state.originSession != session) return false;
        synchronized (state) {
            return state.earlySceneEntrySent && state.earlySceneEntrySession == session;
        }
    }

    /**
     * The native 7.1 second intro emits two false->true PlayerSetPauseReq cycles after born data.
     * The second cycle is the protocol-visible world-entry boundary. Automatic birth never feeds
     * this state machine.
     */
    public static void notePause(GameSession session, boolean paused) {
        State state = stateFor(session);
        if (state == null || state.originSession != session) return;

        boolean enterWorld;
        int completedCycles;
        synchronized (state) {
            int before = state.lifecycle.completedPauseCycles();
            enterWorld = state.lifecycle.observeNativePause(paused);
            completedCycles = state.lifecycle.completedPauseCycles();
            if (before == completedCycles) return;
        }

        var player = session.getPlayer();
        Grasscutter.getLogger().debug(
                "[intro-cutover] uid={} completed native pause cycle {}/2.",
                player == null ? 0 : player.getUid(), completedCycles);
        if (enterWorld) enterWorldAfterNativeIntro(session);
    }

    /**
     * Automatic birth and reconnect recovery use the same early scene-entry handoff as native
     * birth. Sending the scene token before the cold Player.onLogin tail keeps the client alive;
     * EnterSceneReady is deferred until the worker completes.
     */
    public static void beginSceneReadyWorldLogin(GameSession session) {
        startWorldLogin(session);
    }

    private static void enterWorldAfterNativeIntro(GameSession session) {
        startWorldLogin(session);
    }

    private static void startWorldLogin(GameSession session) {
        var player = session == null ? null : session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        State state = stateFor(session);
        if (state == null || state.originSession != session) return;
        synchronized (state) {
            if (!state.lifecycle.claimWorldLogin()) return;
        }

        try {
            session.send(new PacketPlayerEnterSceneNotify(player));
            synchronized (state) {
                state.earlySceneEntrySent = true;
                state.earlySceneEntrySession = session;
            }

            // Loading scene groups may take longer than the client's reconnect timeout.
            // The logic thread must remain available to acknowledge the first scene request.
            Grasscutter.getThreadPool().submit(() -> completeWorldLogin(session, player));
        } catch (Throwable t) {
            failWorldLogin(session, state, t);
        }
    }

    private static void failWorldLogin(GameSession session, State state, Throwable error) {
        Grasscutter.getLogger().error(
                "[born-flow] uid={} failed while initializing first world.",
                uidOf(session), error);
        if (state.lifecycle.mode() == FreshPlayerLifecycle.Mode.NATIVE_INTRO) {
            state.lifecycle.nativeWorldLoginFailed();
        } else {
            remove(session);
            session.close();
        }
    }

    private static void completeWorldLogin(
            GameSession session, emu.grasscutter.game.player.Player player) {
        State state = stateFor(session);
        if (state == null || state.originSession != session) return;

        // Another connection for this UID cannot create a second world until this worker has
        // completed and cleaned up its world. Do not hold the logic thread while loading assets.
        Object lock = WORLD_LOGIN_LOCKS[Math.floorMod(player.getUid(), WORLD_LOGIN_LOCKS.length)];
        synchronized (lock) {
            synchronized (player) {
                if (state.disconnected || !session.isConnected()) return;
                try {
                    boolean statueUnlocked =
                            player.getUnlockedScenePoints(STARTER_SCENE_ID).contains(STARTER_STATUE_POINT_ID);
                    boolean statueForceLocked =
                            player.isScenePointForceLocked(STARTER_SCENE_ID, STARTER_STATUE_POINT_ID);

                    player.onLogin();
                    restoreStarterStatueState(player, statueUnlocked, statueForceLocked);
                } catch (Throwable error) {
                    failWorldLogin(session, state, error);
                } finally {
                    // The disconnect handler deliberately defers cleanup to this worker while
                    // Player.onLogin is running. Never leave a zombie world registered after a
                    // cancelled cold login.
                    if (state.disconnected || !session.isConnected()) {
                        if (player.getWorld() != null) player.onLogout();
                    }
                }
            }
        }

        if (!state.disconnected && session.isConnected()) {
            markWorldLoginComplete(session);
        }
    }

    private static void restoreStarterStatueState(
            emu.grasscutter.game.player.Player player,
            boolean wasUnlocked,
            boolean wasForceLocked) {
        var unlocked = player.getUnlockedScenePoints(STARTER_SCENE_ID);
        var forceLocked = player.getForceLockedScenePoints(STARTER_SCENE_ID);
        boolean changed =
                unlocked.contains(STARTER_STATUE_POINT_ID) != wasUnlocked
                        || forceLocked.contains(STARTER_STATUE_POINT_ID) != wasForceLocked;

        if (wasUnlocked) unlocked.add(STARTER_STATUE_POINT_ID);
        else unlocked.remove(STARTER_STATUE_POINT_ID);

        if (wasForceLocked) forceLocked.add(STARTER_STATUE_POINT_ID);
        else forceLocked.remove(STARTER_STATUE_POINT_ID);

        if (changed) {
            player.save();
            Grasscutter.getLogger()
                    .debug(
                            "Preserved starter statue state across native-intro login uid={} unlocked={} forceLocked={}.",
                            player.getUid(),
                            wasUnlocked,
                            wasForceLocked);
        }
    }

    /** Returns true when EnterSceneReadyReq is retained until first Player.onLogin completes. */
    public static boolean deferSceneReadyUntilLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null || state.originSession != session) return false;

        synchronized (state) {
            if (!state.lifecycle.mustDeferSceneReady()) return false;
            state.sceneReadyDeferred = true;
            state.deferredSceneReadySession = session;
        }
        return true;
    }

    private static void resumeSceneReady(GameSession session) {
        if (session == null) return;
        var player = session.getPlayer();
        if (player == null || player.getWorld() == null) return;

        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }

    /** Starts the fresh-player quest lifecycle exactly once after PostEnterSceneRsp. */
    public static void finishOnSceneReady(GameSession session) {
        if (session == null) return;

        State state = stateFor(session);
        if (state == null) return;
        synchronized (state) {
            if (state.originSession != session || !state.lifecycle.claimQuestBootstrap()) return;
        }

        var player = session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        try {
            // Chapter controllers now start only through normalized prerequisite events.
            // Scene-ready is the single bootstrap entry for the fresh Quest 351 chain.
            player.getQuestManager().onPlayerBorn();
            state.lifecycle.questBootstrapCompleted();
            remove(session);
        } catch (Throwable t) {
            synchronized (state) {
                state.lifecycle.questBootstrapFailed();
            }
            Grasscutter.getLogger()
                    .error(
                            "[born-flow] uid={} failed to start fresh-player quests after scene entry.",
                            player.getUid(),
                            t);
        }
    }
}
