package emu.grasscutter.game.quest.conditions;

import static emu.grasscutter.game.quest.enums.QuestCond.QUEST_COND_STATE_EQUAL;

import emu.grasscutter.data.excels.ChapterData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.QuestValueCond;
import emu.grasscutter.game.quest.enums.QuestState;

@QuestValueCond(QUEST_COND_STATE_EQUAL)
public class ConditionStateEqual extends BaseCondition {

    @Override
    public boolean execute(
            Player owner,
            QuestData questData,
            QuestData.QuestAcceptCondition condition,
            String paramStr,
            int... params) {
        var questId = condition.getParam()[0];
        var questStateValue = condition.getParam()[1];
        var checkQuest = owner.getQuestManager().getQuestById(questId);

        if (checkQuest == null) {
            // Chapter controller quests use [0, FINISHED] as the root sentinel. The 7.1
            // resources also contain unrelated broken handoffs with the same shape, so only
            // treat it as satisfied when ChapterExcel explicitly names this subquest as a
            // chapter begin quest.
            if (questId == 0
                    && questStateValue == QuestState.QUEST_STATE_FINISHED.getValue()
                    && ChapterData.getBeginQuestChapterMap().containsKey(questData.getId())) {
                return true;
            }
            return questStateValue == QuestState.QUEST_STATE_UNSTARTED.getValue();
        }
        return checkQuest.getState().getValue() == questStateValue;
    }
}
