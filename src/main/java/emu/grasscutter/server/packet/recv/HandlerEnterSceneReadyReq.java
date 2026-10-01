package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneReadyReq)
public class HandlerEnterSceneReadyReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "[intro-handshake] EnterSceneReadyReq uid={} state={} loadState={} token={} len={}",
                        player.getUid(),
                        session.getState(),
                        player.getSceneLoadState(),
                        player.getEnterSceneToken(),
                        payload == null ? 0 : payload.length);

        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }
}
