package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketFinishedParentQuestNotify;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import emu.grasscutter.server.packet.send.PacketQuestGlobalVarNotify;
import emu.grasscutter.server.packet.send.PacketQuestListNotify;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Holds the short-lived fresh-player gate between character creation and first world entry. */
public final class BornIntroGate {
    private static final Map<GameSession, State> AWAITING_NATIVE_INTRO =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        private final long armedAtNanos = System.nanoTime();
        private boolean sawUnpaused;
        private int completedPauseCycles;
        private boolean cutoverStarted;
        private boolean earlySceneEntrySent;
        private boolean worldLoginComplete;
        private boolean questStarted;
    }

    private BornIntroGate() {}

    public static void arm(GameSession session) {
        if (session != null) {
            AWAITING_NATIVE_INTRO.put(session, new State());
        }
    }

    public static boolean isAwaiting(GameSession session) {
        return session != null && AWAITING_NATIVE_INTRO.containsKey(session);
    }

    /**
     * Player.onLogin normally emits the login PlayerEnterSceneNotify near the end of a long init
     * tail. Fresh-born 7.1 sends that notify at the native intro boundary instead, so suppress the
     * later duplicate without changing its already-issued enter-scene token.
     */
    public static boolean shouldSuppressLoginSceneEntry(GameSession session) {
        if (session == null) return false;
        synchronized (AWAITING_NATIVE_INTRO) {
            State state = AWAITING_NATIVE_INTRO.get(session);
            return state != null && state.earlySceneEntrySent;
        }
    }

    /** Logs every non-ping packet while the native post-born intro is running. */
    public static void traceInbound(GameSession session, int opcode, byte[] payload) {
        State state = session == null ? null : AWAITING_NATIVE_INTRO.get(session);
        if (state == null || opcode == PacketOpcodes.PingReq || opcode == PacketOpcodes.PingRsp) {
            return;
        }

        long elapsedMs = (System.nanoTime() - state.armedAtNanos) / 1_000_000L;
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-trace] +{}ms uid={} opcode={} ({}) len={} hex={}",
                        elapsedMs,
                        player == null ? 0 : player.getUid(),
                        opcode,
                        PacketOpcodesUtils.getOpcodeName(opcode),
                        payload == null ? 0 : payload.length,
                        toHex(payload));
    }

    /**
     * The 7.1 native second intro emits two false->true PlayerSetPauseReq cycles after 26105. The
     * second cycle finishes exactly when the intro hands control back to the born page, so release
     * scene entry there rather than guessing a duration.
     */
    public static void notePause(GameSession session, boolean paused) {
        if (session == null) return;

        int completedCycles = 0;
        boolean enterWorld = false;
        synchronized (AWAITING_NATIVE_INTRO) {
            State state = AWAITING_NATIVE_INTRO.get(session);
            if (state == null || state.cutoverStarted) return;

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
            enterWorld(session);
        }
    }

    public static void noteCutsceneEnd(GameSession session, byte[] payload) {
        if (!isAwaiting(session)) return;
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-cutover] observed CutSceneEndNotify(472) uid={} len={} hex={}; native born cutover still uses pause cycles.",
                        player == null ? 0 : player.getUid(),
                        payload == null ? 0 : payload.length,
                        toHex(payload));
    }

    public static void noteCutsceneFinish(GameSession session, int cutsceneId, byte[] payload) {
        if (!isAwaiting(session)) return;
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-cutover] observed CutSceneFinishNotify(21200) uid={} cutsceneId={} len={} hex={}; native born cutover still uses pause cycles.",
                        player == null ? 0 : player.getUid(),
                        cutsceneId,
                        payload == null ? 0 : payload.length,
                        toHex(payload));
    }

    private static void enterWorld(GameSession session) {
        var player = session.getPlayer();
        if (player == null) {
            AWAITING_NATIVE_INTRO.remove(session);
            return;
        }

        Grasscutter.getLogger()
                .info(
                        "[intro-cutover] uid={} second native pause cycle complete; sending scene entry before login tail.",
                        player.getUid());

        synchronized (player) {
            try {
                // The client needs scene-entry immediately at the native intro boundary. The full
                // login tail can take tens of seconds on a cold fresh account, while this packet only
                // needs persisted player position/scene/world-level state. Send it first and retain its
                // token; Player.onLogin's later duplicate is suppressed by PacketPlayerEnterSceneNotify.
                session.send(new PacketPlayerEnterSceneNotify(player));
                synchronized (AWAITING_NATIVE_INTRO) {
                    State state = AWAITING_NATIVE_INTRO.get(session);
                    if (state != null) state.earlySceneEntrySent = true;
                }

                player.onLogin();

                synchronized (AWAITING_NATIVE_INTRO) {
                    State state = AWAITING_NATIVE_INTRO.get(session);
                    if (state != null) state.worldLoginComplete = true;
                }
                Grasscutter.getLogger()
                        .info(
                                "[intro-cutover] uid={} login initialization complete; waiting for PostEnterSceneReq before starting fresh-player quests.",
                                player.getUid());
            } catch (Throwable t) {
                synchronized (AWAITING_NATIVE_INTRO) {
                    State state = AWAITING_NATIVE_INTRO.get(session);
                    if (state != null) state.cutoverStarted = false;
                }
                Grasscutter.getLogger()
                        .error(
                                "[intro-cutover] uid={} failed while entering world after native intro boundary.",
                                player.getUid(),
                                t);
            }
        }
    }

    /** Starts the fresh-player quest lifecycle exactly once after the client finished scene entry. */
    public static void finishOnSceneReady(GameSession session) {
        if (session == null) return;

        State state;
        synchronized (AWAITING_NATIVE_INTRO) {
            state = AWAITING_NATIVE_INTRO.get(session);
            if (state == null || !state.worldLoginComplete || state.questStarted) return;
            state.questStarted = true;
        }

        var player = session.getPlayer();
        if (player == null) {
            AWAITING_NATIVE_INTRO.remove(session);
            return;
        }

        try {
            player.getQuestManager().onPlayerBorn();
            session.send(new PacketFinishedParentQuestNotify(player));
            session.send(new PacketQuestListNotify(player));
            session.send(new PacketQuestGlobalVarNotify(player));
            AWAITING_NATIVE_INTRO.remove(session);
            Grasscutter.getLogger()
                    .info(
                            "[intro-cutover] uid={} PostEnterScene ready; fresh-player quests started.",
                            player.getUid());
        } catch (Throwable t) {
            synchronized (AWAITING_NATIVE_INTRO) {
                State current = AWAITING_NATIVE_INTRO.get(session);
                if (current != null) current.questStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "[intro-cutover] uid={} failed to start fresh-player quests after scene entry.",
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
