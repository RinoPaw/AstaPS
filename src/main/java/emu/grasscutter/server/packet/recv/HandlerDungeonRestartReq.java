package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DungeonRestartReq)
public class HandlerDungeonRestartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        Grasscutter.getLogger().info("DungeonRestartReq uid={} scene={}",
                player.getUid(), player.getSceneId());
        player.getServer().getDungeonSystem().restartDungeon(player);
        session.getPlayer().sendPacket(new BasePacket(PacketOpcodes.DungeonRestartRsp));
    }
}
