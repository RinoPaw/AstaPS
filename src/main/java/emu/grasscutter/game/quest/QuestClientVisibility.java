package emu.grasscutter.game.quest;

import emu.grasscutter.game.quest.enums.QuestState;

/**
 * Defines which persisted quests the client may see when quest simulation is switched off.
 * Usually only completed quests are sent. The native City of Freedom quest-tracking tutorial
 * immediately after talking to Amber needs the newly active task to exist on the client.
 */
public final class QuestClientVisibility {
    private QuestClientVisibility() {}

    static boolean includes(
            boolean questingActive,
            int parentQuestId,
            int questId,
            QuestState state,
            boolean amber35601Finished) {
        if (questingActive || state == QuestState.QUEST_STATE_FINISHED) return true;
        // Keep all other unfinished prologue quests suppressed as before. In particular,
        // do not replay earlier intro quests/cutscenes for questing-disabled accounts.
        return parentQuestId == 356
                && (questId == 35602 || questId == 35603)
                && state == QuestState.QUEST_STATE_UNFINISHED
                && amber35601Finished;
    }

    public static boolean includes(GameQuest quest) {
        var manager = quest.getOwner().getQuestManager();
        var amber = manager.getQuestById(35601);
        return includes(
                QuestManager.isQuestingActive(),
                quest.getMainQuestId(),
                quest.getSubQuestId(),
                quest.getState(),
                amber != null && amber.getState() == QuestState.QUEST_STATE_FINISHED);
    }
}
