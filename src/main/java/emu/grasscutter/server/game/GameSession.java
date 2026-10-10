package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.SendPacketEvent;
import emu.grasscutter.utils.*;
import io.netty.buffer.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import lombok.*;

public class GameSession implements GameSessionManager.KcpChannel {
    private final GameServer server;
    private GameSessionManager.KcpTunnel tunnel;

    @Getter @Setter private Account account;
    @Getter private Player player;

    @Getter private long encryptSeed = Crypto.ENCRYPT_SEED;
    private byte[] encryptKey = Crypto.ENCRYPT_KEY;

    @Setter private boolean useSecretKey;

    /** Whether this session has already reported a frame that would not decrypt. */
    private boolean reportedBadMagic;

    // Temporary, session-local diagnostic for the 7.1 Amber dialogue input lock.
    // Record only packet names, never payloads or authentication data.
    private volatile long amberTraceStartNanos;
    private final java.util.Set<Integer> amberTraceRecvOpcodes =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Integer> amberTraceSendOpcodes =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger>
            amberPacketCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean amberSummaryReported =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ConcurrentMap<String, Boolean> amberLastTimeNotifyPaused =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void beginAmberInputTrace() {
        amberTraceRecvOpcodes.clear();
        amberTraceSendOpcodes.clear();
        amberPacketCounts.clear();
        amberLastTimeNotifyPaused.clear();
        amberSummaryReported.set(false);
        amberTraceStartNanos = System.nanoTime();
        var world = player != null ? player.getWorld() : null;
        Grasscutter.getLogger().info(
                "[AmberWire] Starting 45-second dialogue handoff trace uid={} playerPaused={} worldPaused={} timeLocked={}",
                player != null ? player.getUid() : 0,
                player != null && player.isPaused(),
                world != null && world.isPaused(),
                world != null && world.isTimeLocked());
    }

    /**
     * Record the reconnect half of the Amber 35601 lock comparison. The talk-triggered trace
     * belongs to the previous (now closed) session; enable a new one once scene loading completes
     * if 35601 persisted as finished but the following quest is still in progress.
     */
    public void beginAmberReconnectTraceIfNeeded() {
        if (amberTraceStartNanos != 0 || player == null || player.getQuestManager() == null) {
            return;
        }
        var completedTalk = player.getQuestManager().getQuestById(35601);
        var nextQuest = player.getQuestManager().getQuestById(35603);
        if (completedTalk == null || nextQuest == null
                || completedTalk.getState()
                        != emu.grasscutter.game.quest.enums.QuestState.QUEST_STATE_FINISHED
                || nextQuest.getState()
                        != emu.grasscutter.game.quest.enums.QuestState.QUEST_STATE_UNFINISHED) {
            return;
        }
        beginAmberInputTrace();
        Grasscutter.getLogger().info(
                "[AmberWire] Reconnect comparison armed uid={} sceneId={} quest35601={} quest35603={}",
                player.getUid(), player.getSceneId(),
                completedTalk.getState(), nextQuest.getState());
    }

    private boolean isAmberTraceActive() {
        long started = amberTraceStartNanos;
        return started != 0
                && System.nanoTime() - started <= java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
    }

    private void reportAmberSummary(String reason) {
        if (amberTraceStartNanos == 0 || !amberSummaryReported.compareAndSet(false, true)) return;
        var world = player != null ? player.getWorld() : null;
        var quests = player != null ? player.getQuestManager() : null;
        var quest35602 = quests != null ? quests.getQuestById(35602) : null;
        var quest35603 = quests != null ? quests.getQuestById(35603) : null;
        Grasscutter.getLogger().info(
                "[AmberWire] summary reason={} uid={} worldPaused={} playerPaused={} timeLocked={} quest35602={} quest35603={} packets=[{}]",
                reason, player != null ? player.getUid() : 0,
                world != null && world.isPaused(),
                player != null && player.isPaused(),
                world != null && world.isTimeLocked(),
                quest35602 == null ? "NOT_STARTED" : quest35602.getState().name(),
                quest35603 == null ? "NOT_STARTED" : quest35603.getState().name(),
                amberPacketCounts.entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue().get())
                        .collect(java.util.stream.Collectors.joining(", ")));
    }

    // Exact Global 7.1 native serializer NDAJDBCBAAE writes bool field 7 (tag 0x38).
    // Caller provenance: InteractionManager.OnCreateTalkFinish / ResumeGameTime / ClearOnDisconnect.
    // A null result means the native flag was not present; it is not assumed false.
    static Boolean readAmberInteraction27447Flag(byte[] payload) throws java.io.IOException {
        var input = com.google.protobuf.CodedInputStream.newInstance(payload);
        Boolean flag = null;
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) break;
            if (tag == 0x38) {
                flag = input.readBool();
            } else if (!input.skipField(tag)) {
                break;
            }
        }
        return flag;
    }

    private void traceAmberPacket(String direction, int opcode, byte[] payload) {
        long started = amberTraceStartNanos;
        if (started == 0) return;
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (!isAmberTraceActive()) {
            reportAmberSummary("elapsed");
            return;
        }

        var name = PacketOpcodesUtils.getOpcodeName(opcode);
        amberPacketCounts.computeIfAbsent(direction + ":" + name,
                        ignored -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        var seen = "RECV".equals(direction) ? amberTraceRecvOpcodes : amberTraceSendOpcodes;
        if (seen.add(opcode)) {
            Grasscutter.getLogger().info(
                    "[AmberWire] uid={} +{}ms {} {} ({})",
                    player != null ? player.getUid() : 0, elapsedMs, direction, name, opcode);
        }
        // Pause requests and cutscene acknowledgements can occur more than once; their ordering
        // and values matter more than the first-seen packet list when client input stays locked.
        if ("RECV".equals(direction) && opcode == 27447) {
            try {
                Grasscutter.getLogger().info(
                        "[AmberWire] uid={} +{}ms client InteractionManager bool(field7)={} cmd=27447",
                        player != null ? player.getUid() : 0, elapsedMs,
                        readAmberInteraction27447Flag(payload));
            } catch (java.io.IOException e) {
                Grasscutter.getLogger().warn(
                        "[AmberWire] Could not decode client CmdId 27447 bool field 7", e);
            }
        } else if ("RECV".equals(direction) && opcode == PacketOpcodes.PlayerSetPauseReq) {
            try {
                var request = emu.grasscutter.net.proto.PlayerSetPauseReqOuterClass.PlayerSetPauseReq.parseFrom(payload);
                var world = player != null ? player.getWorld() : null;
                Grasscutter.getLogger().info(
                        "[AmberWire] uid={} +{}ms pause request={} serverWorldPaused={} playerPaused={} timeLocked={}",
                        player != null ? player.getUid() : 0, elapsedMs, request.getIsPaused(),
                        world != null && world.isPaused(),
                        player != null && player.isPaused(),
                        world != null && world.isTimeLocked());
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                Grasscutter.getLogger().warn("[AmberWire] Could not decode pause request", e);
            }
        } else if ("SEND".equals(direction) && opcode == PacketOpcodes.SceneTimeNotify) {
            try {
                var message = emu.grasscutter.net.proto.SceneTimeNotifyOuterClass.SceneTimeNotify.parseFrom(payload);
                var previous = amberLastTimeNotifyPaused.put("scene", message.getIsPaused());
                if (previous == null || previous != message.getIsPaused()) {
                    Grasscutter.getLogger().info(
                            "[AmberWire] uid={} +{}ms SceneTimeNotify sceneId={} paused={} sceneTime={}",
                            player != null ? player.getUid() : 0, elapsedMs,
                            message.getSceneId(), message.getIsPaused(), message.getSceneTime());
                }
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                Grasscutter.getLogger().warn("[AmberWire] Could not decode SceneTimeNotify", e);
            }
        } else if ("SEND".equals(direction) && opcode == PacketOpcodes.PlayerTimeNotify) {
            try {
                var message = emu.grasscutter.net.proto.PlayerTimeNotifyOuterClass.PlayerTimeNotify.parseFrom(payload);
                var previous = amberLastTimeNotifyPaused.put("player", message.getIsPaused());
                if (previous == null || previous != message.getIsPaused()) {
                    Grasscutter.getLogger().info(
                            "[AmberWire] uid={} +{}ms PlayerTimeNotify paused={} playerTime={}",
                            player != null ? player.getUid() : 0, elapsedMs,
                            message.getIsPaused(), message.getPlayerTime());
                }
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                Grasscutter.getLogger().warn("[AmberWire] Could not decode PlayerTimeNotify", e);
            }
        } else if ("RECV".equals(direction) && opcode == PacketOpcodes.CutSceneFinishNotify) {
            try {
                var request = emu.grasscutter.net.proto.CutSceneFinishNotifyOuterClass.CutSceneFinishNotify.parseFrom(payload);
                Grasscutter.getLogger().info(
                        "[AmberWire] uid={} +{}ms cutscene finish id={}",
                        player != null ? player.getUid() : 0, elapsedMs, request.getCutsceneId());
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                Grasscutter.getLogger().warn("[AmberWire] Could not decode cutscene finish", e);
            }
        } else if ("SEND".equals(direction) && opcode == PacketOpcodes.CutSceneEndNotify) {
            try {
                var response = emu.grasscutter.net.proto.CutSceneEndNotifyOuterClass.CutSceneEndNotify.parseFrom(payload);
                Grasscutter.getLogger().info(
                        "[AmberWire] uid={} +{}ms cutscene end ack id={} retcode={}",
                        player != null ? player.getUid() : 0, elapsedMs,
                        response.getCutsceneId(), response.getRetcode());
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                Grasscutter.getLogger().warn("[AmberWire] Could not decode cutscene end ack", e);
            }
        }
    }

    /** Packet classes already reported as having no 7.0 CmdId, so each is said once. */
    private static final java.util.Set<String> missingCmdIdReported =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Getter @Setter private SessionState state;

    @Getter private int clientTime;
    @Getter private long lastPingTime;
    private int lastClientSeq = 10;

    public GameSession(GameServer server) {
        this.server = server;
        this.state = SessionState.WAITING_FOR_TOKEN;
        this.lastPingTime = System.currentTimeMillis();

        if (GAME_INFO.useUniquePacketKey) {
            this.encryptKey = new byte[4096];
            this.encryptSeed = Crypto.generateEncryptKeyAndSeed(this.encryptKey);
        }
    }

    public GameServer getServer() {
        return server;
    }

    public InetSocketAddress getAddress() {
        try {
            return tunnel.getAddress();
        } catch (Throwable ignore) {
            return null;
        }
    }

    public boolean useSecretKey() {
        return useSecretKey;
    }

    public String getAccountId() {
        return this.getAccount().getId();
    }

    public synchronized void setPlayer(Player player) {
        this.player = player;
        this.player.setSession(this);
        this.player.setAccount(this.getAccount());
    }

    public boolean isLoggedIn() {
        return this.getPlayer() != null;
    }

    public void updateLastPingTime(int clientTime) {
        this.clientTime = clientTime;
        this.lastPingTime = System.currentTimeMillis();
    }

    public int getNextClientSequence() {
        return ++lastClientSeq;
    }

    public void replayPacket(int opcode, String name) {
        Path filePath = FileUtils.getPluginPath(name);
        File p = filePath.toFile();

        if (!p.exists()) return;

        byte[] packet = FileUtils.read(p);

        BasePacket basePacket = new BasePacket(opcode);
        basePacket.setData(packet);

        send(basePacket);
    }

    private static final java.util.Set<Integer> MUTED_LOG_OPCODES = java.util.Set.of(2497);

    public void logPacket(String sendOrRecv, int opcode, byte[] payload) {
        if (MUTED_LOG_OPCODES.contains(opcode)) return;
        Grasscutter.getLogger()
                .info(sendOrRecv + ": " + PacketOpcodesUtils.getOpcodeName(opcode) + " (" + opcode + ")");
        if (GAME_INFO.isShowPacketPayload) System.out.println(Utils.bytesToHex(payload));
    }

    public void send(BasePacket packet) {

        // The last word on cutscenes. The two places that build one already check this, so nothing
        // should reach here - it is logged rather than dropped silently precisely so that a
        // cutscene the server did send stops being invisible.
        if (GAME_OPTIONS.disableCutscenes && packet.getOpcode() == PacketOpcodes.CutSceneBeginNotify) {
            Grasscutter.getLogger()
                    .info("Suppressed a CutSceneBeginNotify: game.disableCutscenes is on.");
            return;
        }

        if (packet.getOpcode() <= 0) {
            if (isAmberTraceActive()
                    && packet instanceof emu.grasscutter.server.packet.send.PacketPlayerSetPauseRsp) {
                Grasscutter.getLogger().warn(
                        "[AmberWire] PlayerSetPauseRsp dropped: 7.1 response CmdId is unknown; client receives no pause acknowledgement");
            }
            // A non-positive opcode is a message 7.1 has no known CmdId for. Named once per packet
            // class, at debug: there are dozens of them and none is actionable in normal play.
            if (missingCmdIdReported.add(packet.getClass().getSimpleName())) {
                Grasscutter.getLogger()
                        .debug(
                                "{} has no 7.1 CmdId, so it is not being sent.",
                                packet.getClass().getSimpleName());
            }
            return;
        }

        if (packet.shouldBuildHeader()) {
            packet.buildHeader(this.getNextClientSequence());
        }

        switch (GAME_INFO.logPackets) {
            case ALL -> {
                if ((!PacketOpcodesUtils.LOOP_PACKETS.contains(packet.getOpcode())
                        || GAME_INFO.isShowLoopPackets)
                        && !PacketOpcodes.BANNED_PACKETS.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getData());
                }
            }
            case WHITELIST -> {
                if (SERVER.debugWhitelist.contains(packet.getOpcode())
                        && !PacketOpcodes.BANNED_PACKETS.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getData());
                }
            }
            case BLACKLIST -> {
                if (!SERVER.debugBlacklist.contains(packet.getOpcode())
                        && !PacketOpcodes.BANNED_PACKETS.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getData());
                }
            }
            default -> {}
        }

        SendPacketEvent event = new SendPacketEvent(this, packet);
        event.call();
        if (!event.isCanceled()) {
            try {
                packet = event.getPacket();
                var bytes = packet.build();
                if (packet.shouldEncrypt) {
                    if (Grasscutter.getConfig().server.game.useXorEncryption) {
                        Crypto.xor(bytes, packet.useDispatchKey() || !useSecretKey() ? Crypto.DISPATCH_KEY : this.encryptKey);
                    }
                }
                tunnel.writeData(bytes);
                traceAmberPacket("SEND", packet.getOpcode(), packet.getData());
            } catch (Exception e) {
                Grasscutter.getLogger()
                        .debug("Unable to send packet {} to client: {}", packet.getOpcode(), e.toString());
            }
        }
    }

    @Override
    public void onConnected(GameSessionManager.KcpTunnel tunnel) {
        this.tunnel = tunnel;
        Grasscutter.getLogger().info(translate("messages.game.connect", this.getAddress().toString()));
    }

    /**
     * Decrypts a frame in place, working out which key it was sent under rather than assuming.
     *
     * <p>The token exchange has a step where the two sides disagree about the key: the client moves
     * to the session key the moment it accepts the response, and the server only knows that happened
     * when a frame arrives. Guessing wrong in either direction is silent - the frame decrypts to
     * noise and is dropped - and on a version migration that is indistinguishable from a client that
     * never replied, which is exactly the false trail this cost us before.
     *
     * <p>The frame magic settles it: XOR is its own inverse, so a wrong key can be undone and the
     * other one tried, and whichever produces the magic is the key the client is actually using.
     * The session then latches onto it, so this costs one extra XOR only while the two sides are out
     * of step.
     */
    private void decryptWithEitherKey(byte[] bytes) {
        var primary = useSecretKey() ? this.encryptKey : Crypto.DISPATCH_KEY;
        Crypto.xor(bytes, primary);
        if (bytes.length >= 2 && readMagic(bytes) == 17767) return;

        // The session key only exists once a seed has been negotiated, so before that there is
        // nothing to fall back to and the frame really is undecryptable.
        var fallback = useSecretKey() ? Crypto.DISPATCH_KEY : this.encryptKey;
        if (fallback == null || fallback == primary) return;

        Crypto.xor(bytes, primary); // undo
        Crypto.xor(bytes, fallback);
        if (bytes.length >= 2 && readMagic(bytes) == 17767) {
            this.setUseSecretKey(!useSecretKey());
            Grasscutter.getLogger()
                    .info(
                            "Client {} is now talking on the {} key - following it.",
                            this.getAddress(),
                            useSecretKey() ? "session" : "dispatch");
            return;
        }
        // Neither key works, so hand the caller the primary decode and let it report the bad magic.
        Crypto.xor(bytes, fallback);
        Crypto.xor(bytes, primary);
    }

    private static int readMagic(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    @Override
    public void handleReceive(byte[] bytes) {
        if (Grasscutter.getConfig().server.game.useXorEncryption) {
            decryptWithEitherKey(bytes);
        }
        ByteBuf packet = Unpooled.wrappedBuffer(bytes);

        try {
            boolean allDebug = GAME_INFO.logPackets == ServerDebugMode.ALL;
            int prevOpcode = -1, prevHeaderLen = -1, prevPayloadLen = -1, prevStart = -1;
            while (packet.readableBytes() > 0) {
                if (packet.readableBytes() < 12) {
                    return;
                }
                int pktStart = packet.readerIndex();
                int const1 = packet.readShort();
                if (const1 != 17767) {
                    // A frame that does not start with the magic usually means the key is wrong, not
                    // the packet: a game update can ship a new dispatch key, and then every frame
                    // decrypts to noise. With packet logging off that failure was completely silent,
                    // which reads exactly like "the client never connected" - a different problem with
                    // a different fix. So say it once per session regardless, and hand over the first
                    // bytes, which is what a key has to be recovered from.
                    if (!this.reportedBadMagic) {
                        this.reportedBadMagic = true;
                        Grasscutter.getLogger()
                                .warn(
                                        "A {}-byte frame from {} did not decrypt (magic {}, expected 17767) - the {} key does not match this client. First bytes: {}",
                                        bytes.length,
                                        this.getAddress(),
                                        const1,
                                        useSecretKey() ? "session" : "dispatch",
                                        Utils.bytesToHex(
                                                java.util.Arrays.copyOf(bytes, Math.min(bytes.length, 32))));
                    }
                    if (allDebug) {
                        int badOffset = packet.readerIndex() - 2;
                        Grasscutter.getLogger()
                                .error("Bad Data Package Received: got {} ,expect 17767 (bad magic at offset {} of {}-byte frame; prev packet opcode={} headerLen={} payloadLen={} startedAt={})", const1, badOffset, bytes.length, prevOpcode, prevHeaderLen, prevPayloadLen, prevStart);
                        Grasscutter.getLogger()
                                .error("RAW FRAME HEX: {}", Utils.bytesToHex(bytes));
                    }
                    return; // Bad packet
                }
                int opcode = packet.readShort();
                int headerLength = packet.readShort();
                int payloadLength = packet.readInt();
                byte[] header = new byte[headerLength];
                byte[] payload = new byte[payloadLength];

                packet.readBytes(header);
                packet.readBytes(payload);
                int const2 = packet.readShort();
                if (const2 != -30293) {
                    if (allDebug) {
                        Grasscutter.getLogger()
                                .error("Bad Data Package Received: got {} ,expect -30293", const2);
                    }
                    return; // Bad packet
                }

                prevOpcode = opcode;
                prevHeaderLen = headerLength;
                prevPayloadLen = payloadLength;
                prevStart = pktStart;

                switch (GAME_INFO.logPackets) {
                    case ALL -> {
                        if ((!PacketOpcodesUtils.LOOP_PACKETS.contains(opcode) || GAME_INFO.isShowLoopPackets)
                                && !PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    case WHITELIST -> {
                        if (SERVER.debugWhitelist.contains(opcode)
                                && !PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    case BLACKLIST -> {
                        if (!(SERVER.debugBlacklist.contains(opcode))
                                && !PacketOpcodes.BANNED_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    default -> {}
                }

                traceAmberPacket("RECV", opcode, payload);
                getServer().getPacketHandler().handle(this, opcode, header, payload);
                if (opcode == PacketOpcodes.PlayerSetPauseReq && isAmberTraceActive()) {
                    var world = player != null ? player.getWorld() : null;
                    Grasscutter.getLogger().info(
                            "[AmberWire] pause handled uid={} playerPaused={} worldPaused={} timeLocked={}",
                            player != null ? player.getUid() : 0,
                            player != null && player.isPaused(),
                            world != null && world.isPaused(),
                            world != null && world.isTimeLocked());
                }
            }
        } catch (Throwable e) {
            // The rest of this datagram is lost either way, but printed to the console it never
            // reached the log, so a dropped packet left the player's action unexplained.
            Grasscutter.getLogger()
                    .error(
                            "Dropped an inbound packet from {}.",
                            this.getPlayer() != null ? this.getPlayer().getUid() : this.getAddress(),
                            e);
        } finally {

            packet.release();
        }
    }

        @Override
        public void handleClose() {
            // The client may be force-closed before the 45-second timer expires. Capture the
            // pending quest and pause state before logout resets the session.
            reportAmberSummary("disconnect");
            setState(SessionState.INACTIVE);

            Grasscutter.getLogger()
                    .info(translate("messages.game.disconnect", this.getAddress().toString()));

            if (this.isLoggedIn()) {
                Player player = getPlayer();

                player.onLogout();
            }
            try {
                send(new BasePacket(PacketOpcodes.ServerDisconnectClientNotify));
            } catch (Throwable ignore) {
                Grasscutter.getLogger().warn("closing {} error", getAddress().getAddress().getHostAddress());
            }
            tunnel = null;
        }

    public void close() {
        // Already disconnected (a second login from the same account closes the first
        // session, which may have gone away on its own).
        var tunnel = this.tunnel;
        if (tunnel != null) tunnel.close();
    }

    public boolean isActive() {
        return getState() == SessionState.ACTIVE;
    }

    /**
     * Whether the connection is still open, which is a different question from {@link #isActive()} -
     * that one asks whether the player has finished logging in. Anything working the handshake, in
     * front of login, has to ask this one instead.
     */
    public boolean isConnected() {
        return this.tunnel != null;
    }

    public enum SessionState {
        INACTIVE,
        WAITING_FOR_TOKEN,
        WAITING_FOR_LOGIN,
        PICKING_CHARACTER,
        ACTIVE,
        ACCOUNT_BANNED,
        /** Turned away because the server is already at its player limit. */
        SERVER_MAX_PLAYER_OVERFLOW,
        /** Turned away because the database save queues are backed up. */
        DB_OVERLOAD
    }
}
