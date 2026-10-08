package emu.grasscutter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.quest.QuestData.QuestField;
import emu.grasscutter.data.excels.quest.QuestData.QuestSource;
import emu.grasscutter.game.quest.enums.LogicType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class QuestReviewedMondstadtCompatibilityTest {
    private static final Gson GSON = new Gson();

    private QuestData merge(String source, String bin) {
        var quest = GSON.fromJson(source, QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getId(), quest);
        GSON.fromJson(bin, MainQuestData.class).onLoad();
        return quest;
    }

    @AfterEach
    void cleanup() {
        for (int id : new int[] {35302, 30901, 37602}) {
            GameData.getQuestDataMap().remove(id);
            GameData.getBeginCondQuestMap().values().forEach(
                    rows -> rows.removeIf(q -> q.getSubId() == id));
        }
        GameData.getBeginCondQuestMap().entrySet().removeIf(e -> e.getValue().isEmpty());
        QuestData.clearNormalizationAudit();
    }

    @Test
    void tutorialActionAndConditionUseReviewedCompatibility() {
        var quest = merge(
                "{\"subId\":35302,\"mainId\":353,\"acceptCond\":[],\"beginExec\":[],\"finishCond\":[],\"failCond\":[]}",
                "{\"id\":353,\"subQuests\":[{\"subId\":35302,\"acceptCond\":[{\"type\":\"QUEST_COND_STATE_EQUAL\",\"param\":[35301,3]}],\"beginExec\":[{\"type\":\"QUEST_EXEC_REFRESH_GROUP_SUITE\",\"param\":[\"3\",\"133003002,2\"]}]}]}");
        assertEquals(35301, quest.getAcceptCond().get(0).getParam()[0]);
        assertEquals("133003002,2", quest.getBeginExec().get(0).getParam()[1]);
        assertEquals(QuestSource.REVIEWED_COMPAT, quest.getFieldSource(QuestField.BEGIN_EXEC));
    }

    @Test
    void threeDungeonsRequireAllAndCombatFailsOnEitherTrigger() {
        var dungeon = merge(
                "{\"subId\":30901,\"mainId\":309,\"acceptCond\":[],\"finishCondComb\":\"LOGIC_NONE\",\"finishCond\":[{\"type\":\"QUEST_CONTENT_FINISH_DUNGEON\",\"param\":[1001]},{\"type\":\"QUEST_CONTENT_FINISH_DUNGEON\",\"param\":[1]},{\"type\":\"QUEST_CONTENT_FINISH_DUNGEON\",\"param\":[1003]}],\"failCond\":[]}",
                "{\"id\":309,\"subQuests\":[{\"subId\":30901,\"finishCondComb\":\"LOGIC_AND\"}]}");
        assertEquals(LogicType.LOGIC_AND, dungeon.getFinishCondComb());
        assertFalse(LogicType.calculate(dungeon.getFinishCondComb(), new int[] {1,1,0}));
        assertTrue(LogicType.calculate(dungeon.getFinishCondComb(), new int[] {1,1,1}));

        var fail = merge(
                "{\"subId\":37602,\"mainId\":376,\"acceptCond\":[],\"failCondComb\":\"LOGIC_NONE\",\"finishCond\":[],\"failCond\":[{\"type\":\"QUEST_CONTENT_NOT_FINISH_PLOT\",\"param\":[37602]},{\"type\":\"QUEST_CONTENT_TEAM_DEAD\",\"param\":[0]}]}",
                "{\"id\":376,\"subQuests\":[{\"subId\":37602,\"failCondComb\":\"LOGIC_OR\"}]}");
        assertEquals(LogicType.LOGIC_OR, fail.getFailCondComb());
        assertTrue(LogicType.calculate(fail.getFailCondComb(), new int[] {0,1}));
    }
}
