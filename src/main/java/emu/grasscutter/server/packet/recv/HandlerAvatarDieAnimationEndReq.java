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

        // 7.1 sends the persistent avatar GUID here. TeamManager compares the death key with the
        // transient scene entity id, so translate only when the request matches the active avatar.
        if (currentAvatar != null) {
            dieGuid =
                    resolveDeathEntityId(
                            dieGuid, currentAvatar.getAvatar().getGuid(), currentAvatar.getId());
        }

        teamManager.onAvatarDie(dieGuid);
    }

    static long resolveDeathEntityId(long dieGuid, long avatarGuid, int entityId) {
        return dieGuid == avatarGuid ? entityId : dieGuid;
    }
}
