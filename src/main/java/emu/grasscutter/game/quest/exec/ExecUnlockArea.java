package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_AREA)
public class ExecUnlockArea extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        int sceneId = Integer.parseInt(paramStr[0]);
        int areaId = Integer.parseInt(paramStr[1]);
        quest.getOwner().getProgressManager().unlockSceneArea(sceneId, areaId);

        if (quest.getMainQuestId() == 303 || quest.getMainQuestId() == 352) {
            Grasscutter.getLogger()
                    .info(
                            "[statue-probe] QUEST_EXEC_UNLOCK_AREA uid={} quest={}/{} scene={} area={}",
                            quest.getOwner().getUid(),
                            quest.getMainQuestId(),
                            quest.getSubQuestId(),
                            sceneId,
                            areaId);
        }
        return true;
    }
}
