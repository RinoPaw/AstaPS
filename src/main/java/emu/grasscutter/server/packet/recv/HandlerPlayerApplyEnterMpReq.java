package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerApplyEnterMpRsp;

@Opcodes(MultiplayerProtocol71.PLAYER_APPLY_ENTER_MP_REQ)
public class HandlerPlayerApplyEnterMpReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = MultiplayerProtocol71.decodeApplyEnterMpRequest(payload);
        int targetUid = req.targetUid();

        if (targetUid == 0) {
            session.send(new PacketPlayerApplyEnterMpRsp(0));
            return;
        }

        session.getServer().getMultiplayerSystem().applyEnterMp(session.getPlayer(), targetUid);
        session.send(new PacketPlayerApplyEnterMpRsp(targetUid));
    }
}
