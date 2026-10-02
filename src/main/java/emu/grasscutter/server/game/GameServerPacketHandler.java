package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.game.systems.ReliquaryDustSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.event.game.ReceivePacketEvent;
import emu.grasscutter.server.game.GameSession.SessionState;
import it.unimi.dsi.fastutil.ints.*;

public final class GameServerPacketHandler {

    private final Int2ObjectMap<PacketHandler> handlers;

    /** Opcodes already reported as unhandled, so each one is named once rather than per packet. */

    public GameServerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        this.handlers = new Int2ObjectOpenHashMap<>();

        this.registerHandlers(handlerClass);
    }

    public void registerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        try {
            var opcode = handlerClass.getAnnotation(Opcodes.class);
            if (opcode == null || opcode.disabled() || opcode.value() <= 0) {
                return;
            }

            var packetHandler = handlerClass.getDeclaredConstructor().newInstance();
            this.handlers.put(opcode.value(), packetHandler);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Unable to register handler {}.", handlerClass.getSimpleName(), e);
        }
    }

    public void registerHandlers(Class<? extends PacketHandler> handlerClass) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(handlerClass);
        for (var obj : handlerClasses) {
            this.registerPacketHandler(obj);
        }

        this.registerOpcodeAliases();

        Grasscutter.getLogger()
                .debug("Registered " + this.handlers.size() + " " + handlerClass.getSimpleName() + "s");
    }

    /** Wire opcodes that differ from proto CmdId or UnionCmd messageId aliases. */
    private void registerOpcodeAliases() {
        var combineHandler = this.handlers.get(PacketOpcodes.CombineReq);
        if (combineHandler != null) {
            this.handlers.put(PacketOpcodes.CombineReqUnionCmd, combineHandler);
        }
        // Ensure handbook domain refresh is handled even if Reflections misses a hot-patched class.
        if (!this.handlers.containsKey(PacketOpcodes.InteractDailyDungeonInfoNotify)) {
            this.registerPacketHandler(
                    emu.grasscutter.server.packet.recv.HandlerInteractDailyDungeonInfoNotify.class);
        }
        // SeeMonsterReq (24569) — hot-patched class may be missed by Reflections scan.
        if (!this.handlers.containsKey(PacketOpcodes.SeeMonsterReq)) {
            this.registerPacketHandler(
                    emu.grasscutter.server.packet.recv.HandlerSeeMonsterReq.class);
        }
        // ReliquaryDust (artifact reshaping / 105006)
        registerIfAbsent(
                ReliquaryDustSystem.OPCODE_DUST_REQ,
                emu.grasscutter.server.packet.recv.HandlerReliquaryDustReq.class);
        registerIfAbsent(
                ReliquaryDustSystem.OPCODE_DUST_COMPANION_REQ,
                emu.grasscutter.server.packet.recv.HandlerReliquaryDustCompanionReq.class);
        registerIfAbsent(
                ReliquaryDustSystem.OPCODE_DUST_SELECT_REQ,
                emu.grasscutter.server.packet.recv.HandlerReliquaryDustSelectReq.class);
        registerIfAbsent(
                ReliquaryDustSystem.OPCODE_DUST_CONFIRM_REQ_A,
                emu.grasscutter.server.packet.recv.HandlerReliquaryDustConfirmReqA.class);
        registerIfAbsent(
                ReliquaryDustSystem.OPCODE_DUST_CONFIRM_REQ_B,
                emu.grasscutter.server.packet.recv.HandlerReliquaryDustConfirmReqB.class);

        // Ensure the current BuyResinReq handler is registered at its confirmed wire opcode.
        if (!this.handlers.containsKey(PacketOpcodes.BuyResinReq)) {
            this.handlers.put(
                    PacketOpcodes.BuyResinReq,
                    new emu.grasscutter.server.packet.recv.HandlerBuyResinReq());
            Grasscutter.getLogger()
                    .info("Registered BuyResinReq handler at opcode {}", PacketOpcodes.BuyResinReq);
        }
        // Handbook preparation / quick-open (may be missed by Reflections after hot-patch).
        if (!this.handlers.containsKey(PacketOpcodes.DungeonQuickOpenReq)) {
            this.registerPacketHandler(
                    emu.grasscutter.server.packet.recv.HandlerDungeonQuickOpenReq.class);
        }
        // Adventurer Handbook Investigation/preparation claim rewards.
        registerIfAbsent(
                PacketOpcodes.TakeInvestigationTargetRewardReq,
                emu.grasscutter.server.packet.recv.HandlerTakeInvestigationTargetRewardReq.class);
        registerIfAbsent(
                PacketOpcodes.TakeInvestigationRewardReq,
                emu.grasscutter.server.packet.recv.HandlerTakeInvestigationRewardReq.class);
        registerIfAbsent(
                PacketOpcodes.TakeOfferingLevelRewardReq,
                emu.grasscutter.server.packet.recv.HandlerTakeOfferingLevelRewardReq.class);
        registerIfAbsent(
                PacketOpcodes.PlayerOfferingReq,
                emu.grasscutter.server.packet.recv.HandlerPlayerOfferingReq.class);
        Grasscutter.getLogger()
                .info("Registered PlayerOfferingReq handler at opcode {}", PacketOpcodes.PlayerOfferingReq);
    }

    private void registerIfAbsent(int opcode, Class<? extends PacketHandler> handlerClass) {
        if (!this.handlers.containsKey(opcode)) {
            this.registerPacketHandler(handlerClass);
        }
    }

    public void handle(GameSession session, int opcode, byte[] header, byte[] payload) {
        // During the native fresh-player intro, log every inbound packet before handler lookup and
        // before session-state filtering. This intentionally includes loop and unknown opcodes.
        BornIntroGate.traceInbound(session, opcode, payload);

        PacketHandler handler = this.handlers.get(opcode);

        if (handler != null) {
            try {
                if (session.getPlayer() != null) {
                    emu.grasscutter.game.systems.ReliquaryDustSystem.noteRecvOpcode(
                            session.getPlayer(), opcode);
                }

                SessionState state = session.getState();

                if (opcode == PacketOpcodes.PingReq) {

                } else if (opcode == PacketOpcodes.GetPlayerTokenReq) {
                    if (state != SessionState.WAITING_FOR_TOKEN) {
                        return;
                    }
                } else if (state == SessionState.ACCOUNT_BANNED) {
                    session.close();
                    return;
                } else if (opcode == PacketOpcodes.PlayerLoginReq) {
                    if (state != SessionState.WAITING_FOR_LOGIN) {
                        return;
                    }
                } else if (opcode == PacketOpcodes.SetPlayerBornDataReq) {
                    if (state != SessionState.PICKING_CHARACTER) {
                        return;
                    }
                } else {
                    if (state != SessionState.ACTIVE) {
                        return;
                    }
                }

                ReceivePacketEvent event = new ReceivePacketEvent(session, opcode, payload);
                event.call();
                if (!event.isCanceled()) handler.handle(session, header, event.getPacketData());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return;
        }

        if (!PacketOpcodesUtils.LOOP_PACKETS.contains(opcode)
                && opcode != PacketOpcodes.PingReq
                && opcode != PacketOpcodes.PingRsp) {
            String hex = "";
            if (payload != null && payload.length > 0 && payload.length <= 128) {
                StringBuilder sb = new StringBuilder(payload.length * 2);
                for (byte b : payload) {
                    sb.append(String.format("%02x", b));
                }
                hex = " hex=" + sb;
            }
            // Unhandled requests go to debug: a 7.1 client sends dozens this server has no
            // handler for, and listing them at every login was noise.
            var logger = Grasscutter.getLogger();
            if (logger.isDebugEnabled()) {
                var line = "Unhandled packet opcode {} ({}) len={} from {}{}";
                Object[] args = {
                    opcode,
                    PacketOpcodesUtils.getOpcodeName(opcode),
                    payload == null ? 0 : payload.length,
                    session.getAddress(),
                    hex
                };
                logger.debug(line, args);
            }
            if (session.getPlayer() != null) {
                emu.grasscutter.game.systems.ReliquaryDustSystem.noteRecvOpcode(
                        session.getPlayer(), opcode);
            }
        }
    }

    private static boolean shouldDump(GameSession session, int opcode) {
        if (PacketOpcodes.BANNED_PACKETS.contains(opcode)) return false;
        return switch (GAME_INFO.logPackets) {
            case ALL -> !PacketOpcodesUtils.LOOP_PACKETS.contains(opcode) || GAME_INFO.isShowLoopPackets;
            case WHITELIST -> SERVER.debugWhitelist.contains(opcode);
            case BLACKLIST -> !SERVER.debugBlacklist.contains(opcode);
            default -> false;
        };
    }
}
