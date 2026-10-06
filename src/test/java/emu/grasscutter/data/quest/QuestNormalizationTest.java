package emu.grasscutter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.quest.QuestData.QuestField;
import emu.grasscutter.data.excels.quest.QuestData.QuestSource;
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
        QuestData.clearNormalizationAudit();
    }

    @Test
    void unprovenBinExecIsAuditOnly() {
        var excel =
                GSON.fromJson(
                        """
                        {
                          "subId": 990001,
                          "mainId": 990,
                          "order": 1,
                          "acceptCond": [],
                          "beginExec": []
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

        assertEquals(QuestSource.QUEST_EXCEL, excel.getFieldSource(QuestField.BEGIN_EXEC));
        assertTrue(excel.getBeginExec().isEmpty());
        assertTrue(QuestData.getNormalizationAuditSummary().contains("conflict="));
    }

    @Test
    void establishedBinFieldsStillOverrideRuntime() {
        var excel =
                GSON.fromJson(
                        """
                        {
                          "subId": 990001,
                          "mainId": 990,
                          "order": 1,
                          "isRewind": false,
                          "finishParent": false,
                          "acceptCond": [],
                          "beginExec": []
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
                              "isRewind": true,
                              "finishParent": true
                            }
                          ]
                        }
                        """,
                        MainQuestData.class);
        mainQuest.onLoad();

        assertTrue(excel.isRewind());
        assertTrue(excel.isFinishParent());
        assertEquals(QuestSource.BIN_OUTPUT, excel.getFieldSource(QuestField.REWIND));
        assertEquals(QuestSource.BIN_OUTPUT, excel.getFieldSource(QuestField.FINISH_PARENT));
    }

    @Test
    void binOnlySubQuestStaysOutOfRuntimeAndIsAudited() {
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
        assertTrue(QuestData.getNormalizationAuditSummary().contains("binOnlyRows=1"));
    }
}
