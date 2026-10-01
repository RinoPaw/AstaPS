package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetOnlinePlayerListRsp;

@Opcodes(MultiplayerProtocol71.GET_ONLINE_PLAYER_LIST_REQ)
public class HandlerGetOnlinePlayerListReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send(new PacketGetOnlinePlayerListRsp(session.getPlayer()));
    }
}
