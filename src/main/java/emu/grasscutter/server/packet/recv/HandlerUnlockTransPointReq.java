package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.TransPointUnlockHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.UnlockTransPointReq)
public class HandlerUnlockTransPointReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UnlockTransPointReq req = UnlockTransPointReq.parseFrom(payload);
        var player = session.getPlayer();
        var entry = GameData.getScenePointEntryById(req.getSceneId(), req.getPointId());
        boolean isStatue =
                emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                        entry != null ? entry.getPointData() : null);

        TransPointUnlockHelper.unlock(player, req.getSceneId(), req.getPointId(), isStatue);

        // The exact 7.1 ScenePointUnlockNotify field mapping is now client-backed and is sufficient
        // to update the live waypoint state. Runtime controls with candidate 36641, candidate 20290,
        // and no UnlockTransPointRsp at all all produced the same successful unlock/map/teleport
        // behavior. Do not hide that evidence behind a full GetScenePointRsp refresh, and do not send
        // an unconfirmed response CmdId until its semantic identity is established independently.
    }
}
