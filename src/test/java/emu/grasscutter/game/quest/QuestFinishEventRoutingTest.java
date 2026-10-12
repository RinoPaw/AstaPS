package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class QuestFinishEventRoutingTest {
    @Test
    void active351OpeningWithNoAcceptConditionsStillReceivesPlotFinish() {
        var config = JsonUtils.decode("""
                {"subId":35104,"mainId":351,
                 "acceptCond":[],"finishCond":[
                   {"type":"QUEST_CONTENT_FINISH_PLOT","param":[35104,0]}],
                 "failCond":[],"beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        config.onLoad();
        var quest = new GameQuest(null, config);
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);

        assertTrue(GameMainQuest.acceptsFinishEvent(quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));
        assertFalse(GameMainQuest.acceptsFinishEvent(quest, QuestContent.QUEST_CONTENT_COMPLETE_TALK));
        quest.setState(QuestState.QUEST_STATE_FINISHED);
        assertFalse(GameMainQuest.acceptsFinishEvent(quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));
        quest.setState(QuestState.QUEST_STATE_UNSTARTED);
        assertFalse(GameMainQuest.acceptsFinishEvent(quest, QuestContent.QUEST_CONTENT_FINISH_PLOT));
    }
}
