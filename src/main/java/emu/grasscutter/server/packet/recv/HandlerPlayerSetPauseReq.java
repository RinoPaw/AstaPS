package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerSetPauseReqOuterClass.PlayerSetPauseReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerSetPauseRsp;

@Opcodes(PacketOpcodes.PlayerSetPauseReq)
public class HandlerPlayerSetPauseReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = PlayerSetPauseReq.parseFrom(payload);
        var player = session.getPlayer();
        var world = player.getWorld();

        // Native fresh-player intro legitimately toggles pause before the first World exists. Ack
        // the client state first, then let the born gate observe the false->true cycle boundary.
        if (world == null) {
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_SUCC));
            BornIntroGate.notePause(session, req.getIsPaused());
            return;
        }

        // Check if the player is in a multiplayer world.
        if (player.isInMultiplayer()) {
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_FAIL));
        } else {
            world.setPaused(req.getIsPaused());
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_SUCC));
        }
    }
}
