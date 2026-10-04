package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarDieAnimationEndReqOuterClass.AvatarDieAnimationEndReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.AvatarDieAnimationEndReq)
public class HandlerAvatarDieAnimationEndReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AvatarDieAnimationEndReq req = AvatarDieAnimationEndReq.parseFrom(payload);

        var teamManager = session.getPlayer().getTeamManager();
        var currentAvatar = teamManager.getCurrentAvatarEntity();
        long dieGuid = req.getDieGuid();

        // 7.1 sends the persistent avatar GUID here. TeamManager historically compares this
        // value with the transient scene entity id, so the death flow returns before switching
        // to a living teammate or sending WorldPlayerDieNotify. Keep compatibility with clients
        // that still send the scene entity id by only translating a matching avatar GUID.
        if (currentAvatar != null && currentAvatar.getAvatar().getGuid() == dieGuid) {
            dieGuid = currentAvatar.getId();
        }

        teamManager.onAvatarDie(dieGuid);
    }
}
