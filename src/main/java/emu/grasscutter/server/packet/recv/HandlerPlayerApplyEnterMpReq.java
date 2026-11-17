package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerApplyEnterMpReqOuterClass.PlayerApplyEnterMpReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerApplyEnterMpRsp;

@Opcodes(PacketOpcodes.PlayerApplyEnterMpReq)
public class HandlerPlayerApplyEnterMpReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerApplyEnterMpReq req = PlayerApplyEnterMpReq.parseFrom(payload);
        int targetUid = req.getTargetUid();

        if (targetUid == 0) {
            session.send(new PacketPlayerApplyEnterMpRsp(0));
            return;
        }

        session.getServer().getMultiplayerSystem().applyEnterMp(session.getPlayer(), targetUid);
        session.send(new PacketPlayerApplyEnterMpRsp(targetUid));
    }
}
