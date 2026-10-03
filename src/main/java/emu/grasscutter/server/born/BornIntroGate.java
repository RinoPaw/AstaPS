package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEnterScenePeerNotify;
import emu.grasscutter.server.packet.send.PacketEnterSceneReadyRsp;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds the short-lived fresh-player gate between character creation and first world entry. */
public final class BornIntroGate {
    private static final int STARTER_SCENE_ID = 3;
    private static final int STARTER_STATUE_POINT_ID = 7;

    // Key by UID rather than GameSession. The 7.1 client may reconnect while the first cold world
    // login is still finishing; tying this state to the old connection loses the fresh-player quest
    // bootstrap exactly when the replacement session reaches PostEnterSceneReq.
    private static final Map<Integer, State> AWAITING_NATIVE_INTRO = new ConcurrentHashMap<>();

    private static final class State {
        private final GameSession originSession;
        private boolean sawUnpaused;
        private int completedPauseCycles;
        private boolean cutoverStarted;
        private boolean earlySceneEntrySent;
        private GameSession earlySceneEntrySession;
        private boolean worldLoginComplete;
        private boolean questStarted;
        private boolean sceneReadyDeferred;
        private GameSession deferredSceneReadySession;

        private State(GameSession originSession) {
            this.originSession = originSession;
        }
    }

    private BornIntroGate() {}

    private static int uidOf(GameSession session) {
        var player = session == null ? null : session.getPlayer();
        return player == null ? 0 : player.getUid();
    }

    private static State stateFor(GameSession session) {
        int uid = uidOf(session);
        return uid > 0 ? AWAITING_NATIVE_INTRO.get(uid) : null;
    }

    private static void remove(GameSession session) {
        int uid = uidOf(session);
        if (uid > 0) AWAITING_NATIVE_INTRO.remove(uid);
    }

    public static void arm(GameSession session) {
        int uid = uidOf(session);
        if (uid > 0) {
            AWAITING_NATIVE_INTRO.put(uid, new State(session));
        }
    }

    public static boolean isAwaiting(GameSession session) {
        return stateFor(session) != null;
    }

    /**
     * Player.onLogin normally emits the login PlayerEnterSceneNotify near the end of a long init
     * tail. Fresh-born 7.1 sends that notify at the native intro boundary instead, so suppress the
     * later duplicate only on the connection that already received that early scene entry. A
     * replacement session still needs its own ordinary login scene entry.
     */
    public static boolean shouldSuppressLoginSceneEntry(GameSession session) {
        State state = stateFor(session);
        if (state == null) return false;
        synchronized (state) {
            return state.earlySceneEntrySent && state.earlySceneEntrySession == session;
        }
    }

    /**
     * The 7.1 native second intro emits two false->true PlayerSetPauseReq cycles after 26105. The
     * second cycle finishes exactly when the intro hands control back to the born page, so release
     * scene entry there rather than guessing a duration.
     */
    public static void notePause(GameSession session, boolean paused) {
        if (session == null) return;

        State state = stateFor(session);
        if (state == null) return;

        int completedCycles = 0;
        boolean enterWorld = false;
        synchronized (state) {
            // A replacement connection may inherit the UID-scoped state, but only the connection
            // that observed the native born intro is allowed to advance its pause-cycle counter.
            if (state.originSession != session || state.cutoverStarted) return;

            if (!paused) {
                state.sawUnpaused = true;
                return;
            }
            if (!state.sawUnpaused) return;

            state.sawUnpaused = false;
            completedCycles = ++state.completedPauseCycles;
            if (completedCycles >= 2) {
                state.cutoverStarted = true;
                enterWorld = true;
            }
        }

        var player = session.getPlayer();
        Grasscutter.getLogger()
                .debug(
                        "[intro-cutover] uid={} completed native pause cycle {}/2.",
                        player == null ? 0 : player.getUid(),
                        completedCycles);

        if (enterWorld) {
            enterWorld(session);
        }
    }

    private static void enterWorld(GameSession session) {
        var player = session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        State state = stateFor(session);
        if (state == null) return;

        try {
            // Scene-entry itself is cheap and must reach the client immediately at the native intro
            // boundary. The cold login tail can take tens of seconds, so do not run it on the packet
            // handling thread: doing so starves pings/handshake packets and makes 7.1 reconnect.
            session.send(new PacketPlayerEnterSceneNotify(player));
            synchronized (state) {
                state.earlySceneEntrySent = true;
                state.earlySceneEntrySession = session;
            }

            Grasscutter.getThreadPool().submit(() -> completeWorldLogin(session, player));
        } catch (Throwable t) {
            synchronized (state) {
                state.cutoverStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[intro-cutover] uid={} failed to schedule world login after native intro boundary.",
                            player.getUid(),
                            t);
        }
    }

    private static void completeWorldLogin(GameSession session, emu.grasscutter.game.player.Player player) {
        State state = stateFor(session);
        if (state == null) return;

        try {
            synchronized (player) {
                boolean starterStatueUnlockedBeforeLogin =
                        player.getUnlockedScenePoints(STARTER_SCENE_ID).contains(STARTER_STATUE_POINT_ID);
                boolean starterStatueForceLockedBeforeLogin =
                        player.isScenePointForceLocked(STARTER_SCENE_ID, STARTER_STATUE_POINT_ID);

                player.onLogin();
                restoreStarterStatueState(
                        player,
                        starterStatueUnlockedBeforeLogin,
                        starterStatueForceLockedBeforeLogin);
            }

            GameSession deferredReadySession = null;
            synchronized (state) {
                state.worldLoginComplete = true;
                if (state.sceneReadyDeferred) {
                    deferredReadySession = state.deferredSceneReadySession;
                    state.sceneReadyDeferred = false;
                    state.deferredSceneReadySession = null;
                }
            }

            if (deferredReadySession != null) {
                resumeSceneReady(deferredReadySession);
            }
        } catch (Throwable t) {
            synchronized (state) {
                state.cutoverStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[intro-cutover] uid={} failed while entering world after native intro boundary.",
                            player.getUid(),
                            t);
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

        if (wasUnlocked) {
            unlocked.add(STARTER_STATUE_POINT_ID);
        } else {
            unlocked.remove(STARTER_STATUE_POINT_ID);
        }
        if (wasForceLocked) {
            forceLocked.add(STARTER_STATUE_POINT_ID);
        } else {
            forceLocked.remove(STARTER_STATUE_POINT_ID);
        }

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

    /**
     * Returns true when EnterSceneReadyReq was retained until the async cold login tail completes.
     * The handler should return without sending its normal response in that case.
     */
    public static boolean deferSceneReadyUntilLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null) return false;

        synchronized (state) {
            if (state.worldLoginComplete) return false;
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

    /** Starts the fresh-player quest lifecycle exactly once after the client finished scene entry. */
    public static void finishOnSceneReady(GameSession session) {
        if (session == null) return;

        State state = stateFor(session);
        if (state == null) return;
        synchronized (state) {
            if (!state.worldLoginComplete || state.questStarted) return;
            state.questStarted = true;
        }

        var player = session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        try {
            // Player.onLogin already sent the full quest snapshot. onPlayerBorn creates Quest 351
            // through the normal incremental FinishedParentQuestUpdate/QuestListUpdate path, so a
            // second full snapshot here can reload a quest actor immediately after its sub-start.
            player.getQuestManager().onPlayerBorn();
            remove(session);
        } catch (Throwable t) {
            synchronized (state) {
                state.questStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[intro-cutover] uid={} failed to start fresh-player quests after scene entry.",
                            player.getUid(),
                            t);
        }
    }
}
