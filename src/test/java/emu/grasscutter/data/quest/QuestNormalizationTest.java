package emu.grasscutter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.quest.QuestData.QuestField;
import emu.grasscutter.data.excels.quest.QuestData.QuestSource;
import emu.grasscutter.game.quest.enums.QuestExec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QuestNormalizationTest {
    private static final int COMMON_SUB_ID = 990001;
    private static final int BIN_ONLY_SUB_ID = 990002;
    private static final Gson GSON = new Gson();

    @AfterEach
    void cleanUp() {
        GameData.getQuestDataMap().remove(COMMON_SUB_ID);
        GameData.getQuestDataMap().remove(BIN_ONLY_SUB_ID);
        GameData.getBeginCondQuestMap()
                .values()
                .forEach(
                        quests ->
                                quests.removeIf(
                                        quest ->
                                                quest.getSubId() == COMMON_SUB_ID
                                                        || quest.getSubId() == BIN_ONLY_SUB_ID));
        GameData.getBeginCondQuestMap().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Test
    void meaningfulBinExecReplacesEmptyExcelMaterialization() {
        var excel =
                GSON.fromJson(
                        """
                        {
                          "subId": 990001,
                          "mainId": 990,
                          "order": 1,
                          "acceptCond": [],
                          "beginExec": [
                            {"param": [], "param_str": ""}
                          ]
                        }
                        """,
                        QuestData.class);
        excel.onLoad();
        GameData.getQuestDataMap().put(excel.getSubId(), excel);

        var mainQuest =
                GSON.fromJson(
                        """
                        {
                          "id": 990,
                          "subQuests": [
                            {
                              "subId": 990001,
                              "mainId": 990,
                              "order": 1,
                              "beginExec": [
                                {
                                  "param": ["3", "133003002,2"],
                                  "type": "QUEST_EXEC_REFRESH_GROUP_SUITE"
                                }
                              ]
                            }
                          ]
                        }
                        """,
                        MainQuestData.class);
        mainQuest.onLoad();

        assertEquals(QuestSource.BIN_OUTPUT, excel.getFieldSource(QuestField.BEGIN_EXEC));
        assertEquals(1, excel.getBeginExec().size());
        assertEquals(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE, excel.getBeginExec().get(0).getType());
        assertArrayEquals(new String[] {"3", "133003002,2"}, excel.getBeginExec().get(0).getParam());
    }

    @Test
    void binOnlySubQuestDoesNotBecomeRuntimeQuest() {
        var mainQuest =
                GSON.fromJson(
                        """
                        {
                          "id": 990,
                          "subQuests": [
                            {
                              "subId": 990002,
                              "mainId": 990,
                              "order": 2,
                              "beginExec": [
                                {
                                  "param": ["3", "133003002,2"],
                                  "type": "QUEST_EXEC_REFRESH_GROUP_SUITE"
                                }
                              ]
                            }
                          ]
                        }
                        """,
                        MainQuestData.class);
        mainQuest.onLoad();

        assertFalse(GameData.getQuestDataMap().containsKey(BIN_ONLY_SUB_ID));
    }
}
