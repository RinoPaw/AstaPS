package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_LOCK_POINT)
public final class ExecLockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (params.length < 2) return false;
        int sceneId = Integer.parseInt(params[0]);
        int pointId = Integer.parseInt(params[1]);
        return quest.getOwner().getProgressManager().lockTransPoint(sceneId, pointId);
    }
}
