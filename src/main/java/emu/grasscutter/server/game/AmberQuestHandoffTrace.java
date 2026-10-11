package emu.grasscutter.server.game;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgressManager;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.NpcTalkRspOuterClass.NpcTalkRsp;
import emu.grasscutter.net.proto.QuestListUpdateNotifyOuterClass.QuestListUpdateNotify;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read-only wire-handoff evidence for the 7.1 Amber dialogue input lock.
 *
 * <p>Arm only on talk 35601, never mutate quest or UI states. The 45-second window is indexed by
 * the originating session to prevent cross-login/other-player attribution. Sends are observed
 * after the KCP write, not at client receipt; concurrent quest events can overlap the talk reply.
 */
public final class AmberQuestHandoffTrace {
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(45);
    private static final int AMBER_TALK = 35601;
    private static final int UNKNOWN_INTERACTION_CMD = 27447;
    private static final int UNKNOWN_POSTTALK_CMD = 2178;
    private static final int[] OPEN_STATE_IDS = {1, 2, 7, 8, 16, 17, 24, 60};
    private static final ConcurrentHashMap<GameSession, Trace> ACTIVE = new ConcurrentHashMap<>();

    private AmberQuestHandoffTrace() {}

    private static final class Trace {
        final long start = System.nanoTime();
        final int uid;
        final AtomicInteger ordinal = new AtomicInteger();

        Trace(int uid) {
            this.uid = uid;
        }
    }

    public static void begin(GameSession session, int talkId) {
        if (session == null || talkId != AMBER_TALK) return;
        Player player = session.getPlayer();
        if (player == null) return;
        var trace = new Trace(player.getUid());
        ACTIVE.put(session, trace);
        String fields = describeOpenStates(
                player.getOpenStates(), PlayerProgressManager.DEFAULT_OPEN_STATES);
        Grasscutter.getLogger().info(
                "[AmberHandoff] start uid={} talk={} questingActive={} opens=[{}]",
                trace.uid, talkId, QuestManager.isQuestingActive(), fields);
    }

    public static void observeSent(GameSession session, BasePacket packet) {
        var trace = active(session);
        if (trace == null || packet == null) return;
        try {
            if (packet.getOpcode() == PacketOpcodes.NpcTalkRsp) {
                int talkId = NpcTalkRsp.parseFrom(packet.getData()).getCurTalkId();
                if (talkId == AMBER_TALK) recordWire(trace, "sent=NpcTalkRsp talk=" + talkId);
            } else if (packet.getOpcode() == PacketOpcodes.QuestListUpdateNotify) {
                var notify = QuestListUpdateNotify.parseFrom(packet.getData());
                var detail = formatQuestEntries(notify);
                if (!detail.equals("(none)")) {
                    recordWire(trace, "sent=QuestListUpdateNotify entries=" + detail);
                } else if (notify.getQuestListCount() == 0) {
                    recordWire(trace, "sent=QuestListUpdateNotify entries=(none)");
                }
            }
        } catch (Exception e) {
            record(trace, "sent=decode_error type=" + packet.getClass().getSimpleName());
        }
    }

    public static void observeReceived(GameSession session, int opcode, byte[] payload) {
        var trace = active(session);
        if (trace == null) return;
        if (opcode == UNKNOWN_INTERACTION_CMD) {
            record(trace, "recv=27447 field7=" + readOptionalVarint(payload, 7));
        } else if (opcode == UNKNOWN_POSTTALK_CMD) {
            record(trace, "recv=2178 field15=" + readOptionalVarint(payload, 15));
        } else if (opcode == PacketOpcodes.PlayerSetPauseReq) {
            record(trace, "recv=PlayerSetPauseReq field11=" + readOptionalVarint(payload, 11));
        }
    }

    public static void onDisconnect(GameSession session) {
        var trace = ACTIVE.remove(session);
        if (trace != null) {
            Grasscutter.getLogger().info(
                    "[AmberHandoff] disconnect uid={} observedEvents={}",
                    trace.uid, trace.ordinal.get());
        }
    }

    private static Trace active(GameSession session) {
        if (session == null) return null;
        var trace = ACTIVE.get(session);
        if (trace == null) return null;
        if (System.nanoTime() - trace.start < WINDOW_NANOS) return trace;
        if (ACTIVE.remove(session, trace)) {
            var player = session.getPlayer();
            var main = player == null ? null : player.getQuestManager();
            var world = player == null ? null : player.getWorld();
            Grasscutter.getLogger().info(
                    "[AmberHandoff] end uid={} observedEvents={} quest35601={} quest35602={} quest35603={} worldPaused={} playerPaused={} timeLocked={}",
                    trace.uid, trace.ordinal.get(),
                    main == null ? "unknown" : questState(main.getQuestById(35601)),
                    main == null ? "unknown" : questState(main.getQuestById(35602)),
                    main == null ? "unknown" : questState(main.getQuestById(35603)),
                    world == null ? "unknown" : world.isPaused(),
                    player == null ? "unknown" : player.isPaused(),
                    world == null ? "unknown" : world.isTimeLocked());
        }
        return null;
    }

    private static String questState(emu.grasscutter.game.quest.GameQuest quest) {
        return quest == null ? "absent" : quest.getState().name();
    }

    private static void recordWire(Trace trace, String event) {
        if (trace.ordinal.get() >= 200) return;
        int ordinal = trace.ordinal.incrementAndGet();
        // Preserve the wire-evidence grammar consumed by Genshin-Reverse's cross-source tool.
        Grasscutter.getLogger().info(
                "[AmberGuideOrder] uid={} +{}ms observation={} {}",
                trace.uid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - trace.start),
                ordinal, event);
    }

    private static void record(Trace trace, String event) {
        if (trace.ordinal.get() >= 200) return;
        int ordinal = trace.ordinal.incrementAndGet();
        Grasscutter.getLogger().info(
                "[AmberHandoff] uid={} index={} elapsedMs={} {}",
                trace.uid, ordinal, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - trace.start),
                event);
    }

    /** Absent is not the same as an explicitly stored zero or a resource default. */
    static String describeOpenStates(
            java.util.Map<Integer, Integer> stored, java.util.Set<Integer> defaults) {
        var result = new java.util.StringJoiner(" ");
        for (int id : OPEN_STATE_IDS) {
            String value;
            if (stored.containsKey(id)) value = "stored:" + stored.get(id);
            else if (defaults.contains(id)) value = "default:1";
            else value = "absent";
            result.add(id + "=" + value);
        }
        return result.toString();
    }

    /** Format only the three quest IDs involved in the tutorial, retaining wire parent/state. */
    static String formatQuestEntries(QuestListUpdateNotify notify) {
        var result = new java.util.StringJoiner(", ");
        for (var quest : notify.getQuestListList()) {
            if (quest.getQuestId() >= 35601 && quest.getQuestId() <= 35603) {
                result.add(quest.getQuestId() + "(parent=" + quest.getParentQuestId()
                        + ",state=" + quest.getState() + ")");
            }
        }
        return result.length() == 0 ? "(none)" : result.toString();
    }

    /**
     * Decodes one scalar only. Missing proto3 fields are reported as absent, never treated as
     * explicit false. These unknown CmdIds have no proven guide-unlock semantics.
     */
    static String readOptionalVarint(byte[] payload, int field) {
        if (payload == null) return "malformed";
        try {
            var in = CodedInputStream.newInstance(payload);
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                if (tag == 0) break;
                if (WireFormat.getTagFieldNumber(tag) == field
                        && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_VARINT) {
                    return Long.toUnsignedString(in.readUInt64());
                }
                if (!in.skipField(tag)) break;
            }
            return "absent";
        } catch (IOException | IllegalArgumentException e) {
            return "malformed";
        }
    }
}
