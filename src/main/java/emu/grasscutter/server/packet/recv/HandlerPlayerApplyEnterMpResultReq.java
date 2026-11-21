package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultReqOuterClass.PlayerApplyEnterMpResultReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerApplyEnterMpResultReq)
public class HandlerPlayerApplyEnterMpResultReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerApplyEnterMpResultReq req = PlayerApplyEnterMpResultReq.parseFrom(payload);
        var host = session.getPlayer();
        if (host == null || req.getApplyUid() == 0) return;
        session.getServer().getMultiplayerSystem()
                .applyEnterMpReply(host, req.getApplyUid(), req.getIsAgreed());
    }
}
