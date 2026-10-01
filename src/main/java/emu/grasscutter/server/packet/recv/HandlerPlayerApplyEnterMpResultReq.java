package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(MultiplayerProtocol71.PLAYER_APPLY_ENTER_MP_RESULT_REQ)
public class HandlerPlayerApplyEnterMpResultReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = MultiplayerProtocol71.decodeApplyEnterMpResultRequest(payload);
        if (req.applyUid() == 0) {
            return;
        }

        session
                .getServer()
                .getMultiplayerSystem()
                .applyEnterMpReply(session.getPlayer(), req.applyUid(), req.isAgreed());

        // PlayerApplyEnterMpResultRsp still has no runtime-confirmed 7.1 CmdId. Do not emit opcode 0.
    }
}
