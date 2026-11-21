package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_GADGET_STATE_CHANGE;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueContent;

@QuestValueContent(QUEST_CONTENT_GADGET_STATE_CHANGE)
public final class ContentGadgetStateChange extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        int[] target = condition.getParam();
        return params.length >= 3
                && target != null
                && target.length >= 2
                && target[0] == params[0]
                && target[1] == params[1]
                && Integer.toString(params[2]).equals(condition.getParamStr());
    }
}
