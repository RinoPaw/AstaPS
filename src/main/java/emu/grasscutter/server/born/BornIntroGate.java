package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEnterScenePeerNotify;
import emu.grasscutter.server.packet.send.PacketEnterSceneReadyRsp;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates the fresh-player handoff from birth data to the first Quest 351 scene. */
public final class BornIntroGate {
    private enum Mode {
        /** Wait for the 7.1 client-side post-born intro before entering the world. */
        NATIVE_INTRO,
        /** Character selection/intro was skipped; only wait for the normal scene-ready boundary. */
        SCENE_READY_ONLY
    }

    // Key by UID rather than GameSession. The 7.1 client may reconnect while a cold first login is
    // still finishing; UID scope keeps the fresh-player bootstrap attached to the replacement session.
    private static final Map<Integer, State> FRESH_PLAYER_BOOTSTRAPS = new ConcurrentHashMap<>();

    private static final class State {
        private final Mode mode;
        private final long armedAtNanos = System.nanoTime();
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

        private State(Mode mode, GameSession originSession) {
            this.mode = mode;
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
        return uid > 0 ? FRESH_PLAYER_BOOTSTRAPS.get(uid) : null;
    }

    private static void remove(GameSession session) {
        int uid = uidOf(session);
        if (uid > 0) FRESH_PLAYER_BOOTSTRAPS.remove(uid);
    }

    /** Begins the native 7.1 post-born intro gate after SetPlayerBornData succeeds. */
    public static void armNativeIntro(GameSession session) {
        int uid = uidOf(session);
        if (uid <= 0) return;

        FRESH_PLAYER_BOOTSTRAPS.put(uid, new State(Mode.NATIVE_INTRO, session));
        Grasscutter.getLogger()
                .info(
                        "[born-flow] uid={} native birth complete; waiting for client intro handoff.",
                        uid);
    }

    /**
     * Begins the auto-birth path. World login may start immediately, while Quest 351 remains gated
     * until PostEnterSceneRsp so its quest actors see the same scene-ready ordering as native birth.
     */
    public static void armSceneReady(GameSession session) {
        int uid = uidOf(session);
        if (uid <= 0) return;

        State state = new State(Mode.SCENE_READY_ONLY, session);
        state.cutoverStarted = true;
        FRESH_PLAYER_BOOTSTRAPS.put(uid, state);
        Grasscutter.getLogger()
                .info(
                        "[born-flow] uid={} intro skipped; preserving scene-ready Quest 351 bootstrap.",
                        uid);
    }

    /** Marks the synchronous auto-birth Player.onLogin tail complete and resumes an early scene-ready. */
    public static void markWorldLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null) return;

        GameSession deferredReadySession = null;
        synchronized (state) {
            state.worldLoginComplete = true;
            if (state.sceneReadyDeferred) {
                deferredReadySession = state.deferredSceneReadySession;
                state.sceneReadyDeferred = false;
                state.deferredSceneReadySession = null;
            }
        }

        int uid = uidOf(session);
        Grasscutter.getLogger()
                .info(
                        "[born-flow] uid={} world login complete; waiting for PostEnterSceneReq before Quest 351.",
                        uid);

        if (deferredReadySession != null) {
            resumeSceneReady(deferredReadySession);
        }
    }

    /** True for either native-intro or auto-birth while the first scene bootstrap is unfinished. */
    public static boolean isFreshPlayerBootstrap(GameSession session) {
        return stateFor(session) != null;
    }

    /** True only while the client-side native post-born intro path is active. */
    public static boolean isNativeIntro(GameSession session) {
        State state = stateFor(session);
        return state != null && state.mode == Mode.NATIVE_INTRO;
    }

    /** Compatibility alias for older call sites; use the explicit predicate in new code. */
    @Deprecated
    public static boolean isAwaiting(GameSession session) {
        return isFreshPlayerBootstrap(session);
    }

    /**
     * Native birth sends scene-entry early at the intro cutover. Suppress only the duplicate login
     * scene-entry emitted later by Player.onLogin on that same connection. Auto-birth never uses this.
     */
    public static boolean shouldSuppressLoginSceneEntry(GameSession session) {
        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return false;
        synchronized (state) {
            return state.earlySceneEntrySent && state.earlySceneEntrySession == session;
        }
    }

    /** Logs non-ping packets while a fresh-player bootstrap is active. */
    public static void traceInbound(GameSession session, int opcode, byte[] payload) {
        State state = stateFor(session);
        if (state == null || opcode == PacketOpcodes.PingReq || opcode == PacketOpcodes.PingRsp) {
            return;
        }

        long elapsedMs = (System.nanoTime() - state.armedAtNanos) / 1_000_000L;
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-trace] +{}ms uid={} mode={} opcode={} ({}) len={} hex={}",
                        elapsedMs,
                        player == null ? 0 : player.getUid(),
                        state.mode,
                        opcode,
                        PacketOpcodesUtils.getOpcodeName(opcode),
                        payload == null ? 0 : payload.length,
                        toHex(payload));
    }

    /**
     * The 7.1 native second intro emits two false->true PlayerSetPauseReq cycles after born data.
     * The second cycle is the protocol-visible world-entry boundary.
     */
    public static void notePause(GameSession session, boolean paused) {
        if (session == null) return;

        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return;

        int completedCycles = 0;
        boolean enterWorld = false;
        synchronized (state) {
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
                .info(
                        "[intro-cutover] uid={} completed native pause cycle {}/2.",
                        player == null ? 0 : player.getUid(),
                        completedCycles);

        if (enterWorld) {
            enterWorldAfterNativeIntro(session);
        }
    }

    public static void noteCutsceneEnd(GameSession session, byte[] payload) {
        if (!isNativeIntro(session)) return;
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-cutover] observed CutSceneEndNotify(472) uid={} len={} hex={}; native cutover still uses pause cycles.",
                        player == null ? 0 : player.getUid(),
                        payload == null ? 0 : payload.length,
                        toHex(payload));
    }

    public static void noteCutsceneFinish(GameSession session, int cutsceneId, byte[] payload) {
        if (!isNativeIntro(session)) return;
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-cutover] observed CutSceneFinishNotify(21200) uid={} cutsceneId={} len={} hex={}; native cutover still uses pause cycles.",
                        player == null ? 0 : player.getUid(),
                        cutsceneId,
                        payload == null ? 0 : payload.length,
                        toHex(payload));
    }

    private static void enterWorldAfterNativeIntro(GameSession session) {
        var player = session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return;

        Grasscutter.getLogger()
                .info(
                        "[intro-cutover] uid={} native intro complete; sending first scene entry.",
                        player.getUid());

        try {
            session.send(new PacketPlayerEnterSceneNotify(player));
            synchronized (state) {
                state.earlySceneEntrySent = true;
                state.earlySceneEntrySession = session;
            }

            // Cold first login can be slow. Keep packet handling alive while the world is built.
            Grasscutter.getThreadPool().submit(() -> completeNativeWorldLogin(session, player));
        } catch (Throwable t) {
            synchronized (state) {
                state.cutoverStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[intro-cutover] uid={} failed to schedule world login after native intro.",
                            player.getUid(),
                            t);
        }
    }

    private static void completeNativeWorldLogin(
            GameSession session, emu.grasscutter.game.player.Player player) {
        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return;

        try {
            synchronized (player) {
                player.onLogin();

                // Legacy login still seeds scene 3 point 7 / area 1. Keep the native-unlock probe's
                // fresh-account invariant until that legacy compatibility behavior is removed.
                boolean removedStarterStatue = player.getUnlockedScenePoints(3).remove(7);
                boolean removedStarterArea = player.getUnlockedSceneAreas(3).remove(1);
                player.getForceLockedScenePoints(3).remove(7);
                if (removedStarterStatue || removedStarterArea) {
                    player.save();
                    Grasscutter.getLogger()
                            .info(
                                    "[statue-probe] uid={} removed legacy starter unlocks point=3:7 area=3:1 pointRemoved={} areaRemoved={}.",
                                    player.getUid(),
                                    removedStarterStatue,
                                    removedStarterArea);
                }
            }

            markWorldLoginComplete(session);
        } catch (Throwable t) {
            synchronized (state) {
                state.cutoverStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[intro-cutover] uid={} failed while entering world after native intro.",
                            player.getUid(),
                            t);
        }
    }

    /**
     * Returns true when EnterSceneReadyReq was retained until the first Player.onLogin tail completed.
     */
    public static boolean deferSceneReadyUntilLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null) return false;

        synchronized (state) {
            if (state.worldLoginComplete) return false;
            state.sceneReadyDeferred = true;
            state.deferredSceneReadySession = session;
        }

        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[born-flow] uid={} EnterSceneReadyReq arrived during first login; deferring response.",
                        player == null ? 0 : player.getUid());
        return true;
    }

    private static void resumeSceneReady(GameSession session) {
        if (session == null) return;
        var player = session.getPlayer();
        if (player == null || player.getWorld() == null) return;

        Grasscutter.getLogger()
                .info(
                        "[born-flow] uid={} first login complete; resuming deferred EnterSceneReadyReq.",
                        player.getUid());
        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }

    /** Starts the fresh-player quest lifecycle exactly once after PostEnterSceneRsp. */
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
            // Player.onLogin already sent the full empty/new-player quest snapshot. onPlayerBorn
            // creates Quest 351 through the normal incremental update path; do not reload it with a
            // second full QuestListNotify after its first subquest starts.
            player.getQuestManager().onPlayerBorn();
            remove(session);
            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} PostEnterScene acknowledged; Quest 351 bootstrap started.",
                            player.getUid());
        } catch (Throwable t) {
            synchronized (state) {
                state.questStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[born-flow] uid={} failed to start fresh-player quests after scene entry.",
                            player.getUid(),
                            t);
        }
    }

    private static String toHex(byte[] payload) {
        if (payload == null || payload.length == 0) return "";
        int limit = Math.min(payload.length, 64);
        StringBuilder out = new StringBuilder(limit * 2 + (payload.length > limit ? 3 : 0));
        for (int i = 0; i < limit; i++) {
            out.append(String.format("%02x", payload[i]));
        }
        if (payload.length > limit) out.append("...");
        return out.toString();
    }
}
