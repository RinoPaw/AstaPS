package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_COMPLETE_TALK;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_COMPLETE_TALK)
public class ContentCompleteTalk extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        if (params.length == 0 || condition.getParam() == null || condition.getParam().length == 0) {
            return false;
        }

        // TalkManager validates the talk and emits COMPLETE_TALK with the talk that just completed.
        // Match that event directly. Looking at the saved-talk map here made an unrelated later talk
        // satisfy any active quest whose required talk had happened at some point in the past.
        return condition.getParam()[0] == params[0];
    }
}
