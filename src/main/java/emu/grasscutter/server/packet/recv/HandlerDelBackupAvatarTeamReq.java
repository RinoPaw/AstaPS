package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelBackupAvatarTeamReqOuterClass.DelBackupAvatarTeamReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DelBackupAvatarTeamReq)
public class HandlerDelBackupAvatarTeamReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int teamId = DelBackupAvatarTeamReq.parseFrom(payload).getBackupAvatarTeamId();
        session.getPlayer().getTeamManager().removeCustomTeam(teamId);
    }
}
