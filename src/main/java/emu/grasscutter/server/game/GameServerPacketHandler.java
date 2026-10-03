package emu.grasscutter.server.game;

import emu.grasscutter.Grasscutter;
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
    }

    public void handle(GameSession session, int opcode, byte[] header, byte[] payload) {
        // During the native fresh-player intro, log every inbound packet before handler lookup and
        // before session-state filtering. This intentionally includes loop and unknown opcodes.
        BornIntroGate.traceInbound(session, opcode, payload);

        PacketHandler handler = this.handlers.get(opcode);

        if (handler != null) {
            try {
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
        }
    }
}
