package emu.grasscutter.game.quest.handlers;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;

public abstract class QuestExecHandler {

    public abstract boolean execute(
            GameQuest quest, QuestData.QuestExecParam condition, String... paramStr);

    /**
     * The state at the instant a quest action was queued, before execution on the
     * quest worker thread. Ordinary handlers do not need it; Lua quest-start
     * notifications must not reinterpret the action when the live state changes.
     */
    public boolean execute(
            GameQuest quest, QuestData.QuestExecParam condition,
            QuestState stateAtDispatch, String... paramStr) {
        return execute(quest, condition, paramStr);
    }
}
