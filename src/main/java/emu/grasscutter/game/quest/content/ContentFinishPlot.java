package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_FINISH_PLOT;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_FINISH_PLOT)
public class ContentFinishPlot extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        // A client may omit a content parameter; never abort delivery to
        // other plot conditions when the event has no valid subquest ID.
        if (condition == null || condition.getParam() == null
                || condition.getParam().length == 0 || params.length == 0
                || condition.getParam()[0] != params[0]) return false;
        var talkData = quest.getMainQuest().getTalks().get(params[0]);
        var subQuest = quest.getMainQuest().getChildQuestById(params[0]);
        return (talkData != null && subQuest != null)
                || java.util.Objects.equals(condition.getParamStr(), paramStr);
    }
}
