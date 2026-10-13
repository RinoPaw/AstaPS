package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.DungeonEntryUnlock;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetDungeonEntryExploreConditionReqOuterClass.GetDungeonEntryExploreConditionReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketDungeonEntryToBeExploreNotify;
import emu.grasscutter.server.packet.send.PacketGetDungeonEntryExploreConditionRsp;

@Opcodes(PacketOpcodes.GetDungeonEntryExploreConditionReq)
public class HandlerGetDungeonEntryExploreConditionReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetDungeonEntryExploreConditionReq.parseFrom(payload);
        var data = GameData.getDungeonEntryDataMap().get(req.getDungeonEntryConfigId());
        var player = session.getPlayer();
        var point = GameData.getScenePointEntryById(
                req.getSceneId(), req.getDungeonEntryScenePointId());

        if (data == null
                || data.getSceneId() != req.getSceneId()
                || data.getDungeonEntryId() != req.getDungeonEntryScenePointId()
                || point == null
                || point.getPointData() == null
                || point.getPointData().isForbidSimpleUnlock()
                || player.isScenePointForceLocked(req.getSceneId(), req.getDungeonEntryScenePointId())
                || !DungeonEntryUnlock.canExplore(player, data)) {
            session.send(new PacketGetDungeonEntryExploreConditionRsp(req.getDungeonEntryConfigId()));
            return;
        }

        // Eligible for the native exploration interaction. Do not unlock here: the actual
        // first-unlock transition goes through UnlockTransPointReq / QUEST_EXEC_UNLOCK_POINT.
        session.send(new PacketDungeonEntryToBeExploreNotify(
                req.getDungeonEntryScenePointId(), req.getSceneId(), req.getDungeonEntryConfigId()));
    }
}
