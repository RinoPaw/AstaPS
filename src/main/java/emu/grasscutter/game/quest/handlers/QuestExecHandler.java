package emu.grasscutter.game.quest.handlers;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;

public abstract class QuestExecHandler {

    public abstract boolean execute(
            GameQuest quest, QuestData.QuestExecParam condition, String... paramStr);

    public boolean execute(
            GameQuest quest,
            QuestData.QuestExecParam condition,
            QuestState stateAtDispatch,
            String... paramStr) {
        return execute(quest, condition, paramStr);
    }
}
