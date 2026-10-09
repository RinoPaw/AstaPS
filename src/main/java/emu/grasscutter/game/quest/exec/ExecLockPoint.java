package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;

@QuestValueExec(QuestExec.QUEST_EXEC_LOCK_POINT)
public class ExecLockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (params.length < 2) return false;

        int sceneId;
        int pointId;
        try {
            sceneId = Integer.parseInt(params[0]);
            pointId = Integer.parseInt(params[1]);
        } catch (NumberFormatException ignored) {
            return false;
        }

        if (GameData.getScenePointEntryById(sceneId, pointId) == null) return false;

        var player = quest.getOwner();
        boolean changed = player.getForceLockedScenePoints(sceneId).add(pointId);
        if (changed) {
            player.save();
        }
        player.sendPacket(PacketScenePointUnlockNotify.lock(sceneId, pointId));
        return true;
    }
}
