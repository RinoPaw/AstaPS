package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.FontaineDomainRetryGuard;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DungeonRestartReq)
public class HandlerDungeonRestartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null || !FontaineDomainRetryGuard.mayRestart(player, payload)) {
            Grasscutter.getLogger().warn(
                    "DungeonRestartReq ignored: uid={} scene={} (not a claimed Fontaine weapon domain)",
                    player == null ? 0 : player.getUid(),
                    player == null ? 0 : player.getSceneId());
            return;
        }

        Grasscutter.getLogger().info("DungeonRestartReq uid={} scene={} opcode={}",
                player.getUid(), player.getSceneId(), PacketOpcodes.DungeonRestartReq);
        player.getServer().getDungeonSystem().restartDungeon(player);
        player.sendPacket(new BasePacket(PacketOpcodes.DungeonRestartRsp));
    }
}
