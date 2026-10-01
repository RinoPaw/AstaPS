package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketWorldPlayerReviveRsp;

@Opcodes(PacketOpcodes.WorldPlayerReviveReq)
public class HandlerWorldPlayerReviveReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var teamManager = session.getPlayer().getTeamManager();
        // respawnTeam historically restored HP directly, which leaves GameEntity.isDead set after
        // drowning/team death. The next combat event then re-enters killEntity even at positive HP.
        // Run the existing avatar revive path first so the server life state is actually reset.
        for (var entity : teamManager.getActiveTeam()) {
            if (entity.isDead()) {
                entity.reviveToRatio(0.4f);
            }
        }
        teamManager.respawnTeam();
        session.send(new PacketWorldPlayerReviveRsp());
    }
}
