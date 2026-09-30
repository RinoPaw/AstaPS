package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.tower.TowerAbyssFix;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerEnterLevelReqOuterClass.TowerEnterLevelReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.TowerEnterLevelReq)
public class HandlerTowerEnterLevelReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session.getPlayer();
        TowerEnterLevelReq req = TowerEnterLevelReq.parseFrom(payload == null ? new byte[0] : payload);
        int enterPointId = req.getEnterPointId() > 0 ? req.getEnterPointId() : 45;
        TeamManager teamManager = player.getTeamManager();

        // Team selection and start are separate client packets. Wait briefly for the selected
        // temporary team so the abyss never silently falls back to the overworld party.
        for (int attempt = 0; attempt < 40 && !teamManager.hasTemporaryTeam(); attempt++) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!teamManager.hasTemporaryTeam()) {
            Grasscutter.getLogger().warn(
                    "TowerEnterLevel aborted uid={}: temporaryTeam not ready", player.getUid());
            return;
        }

        TowerAbyssFix.rememberEnterPoint(player, enterPointId);
        // 4.7+ clients mark entering after a floor restart / party reconfiguration explicitly.
        boolean restartFloor = req.getIsRestartFloor();
        if (restartFloor) {
            player.getTowerManager().restartFloorFromChamberOne();
        }
        boolean firstChamber = TowerAbyssFix.isFirstChamber(player);
        player.getTowerManager().enterLevel(enterPointId);
        try {
            teamManager.useTemporaryTeam(0);
            if (firstChamber) {
                TowerAbyssFix.prepareFirstChamber(player);
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn(
                    "TowerEnterLevel post-handoff failed uid={}: {}",
                    player.getUid(),
                    throwable.toString());
        }
        // Lua already installs option 175/176. Do not reinject option 177 after a delay.
    }
}
