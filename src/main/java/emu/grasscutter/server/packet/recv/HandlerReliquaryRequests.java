package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.game.systems.ReliquarySystem;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

/** 同一套圣遗物状态共用处理器，协议号在注册时传入。 */
public final class HandlerReliquaryRequests extends PacketHandler {
    private final int opcode;

    public HandlerReliquaryRequests(int opcode) {
        this.opcode = opcode;
    }

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int sequence = header == null ? 0 : PacketHead.parseFrom(header).getClientSequenceId();
        var system = new ReliquarySystem(session.getPlayer(), sequence);
        try {
            system.handle(opcode, payload == null ? new byte[0] : payload);
        } catch (InvalidProtocolBufferException
                | IllegalArgumentException
                | ArithmeticException e) {
            system.reject(opcode);
        } catch (IllegalStateException e) {
            emu.grasscutter.Grasscutter.getLogger().warn("圣遗物配置或状态异常，协议号 {}", opcode, e);
            system.reject(opcode);
        }
    }
}
