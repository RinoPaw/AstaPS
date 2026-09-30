package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.TransPointUnlockHelper;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_POINT)
public class ExecUnlockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        int sceneId = Integer.parseInt(paramStr[0]);
        int pointId = Integer.parseInt(paramStr[1]);

        // TODO: Determine if the point is a statue from point data consistently.
        boolean isStatue = quest.getMainQuestId() == 303 || quest.getMainQuestId() == 352;

        return TransPointUnlockHelper.unlock(quest.getOwner(), sceneId, pointId, isStatue);
    }
}
