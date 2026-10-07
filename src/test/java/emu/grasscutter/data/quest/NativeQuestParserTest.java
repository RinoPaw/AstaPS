package emu.grasscutter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.quest.QuestData.QuestField;
import emu.grasscutter.data.excels.quest.QuestData.QuestSource;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestExec;
import java.io.StringReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NativeQuestParserTest {
    private static final Gson GSON = new Gson();
    private static final int SUB_ID = 991001;
    private static final int NATIVE_ONLY_SUB_ID = 991002;

    @AfterEach
    void cleanUp() {
        GameData.getQuestDataMap().remove(SUB_ID);
        GameData.getQuestDataMap().remove(NATIVE_ONLY_SUB_ID);
        GameData.getBeginCondQuestMap()
                .values()
                .forEach(
                        quests ->
                                quests.removeIf(
                                        quest ->
                                                quest.getSubId() == SUB_ID
                                                        || quest.getSubId() == NATIVE_ONLY_SUB_ID));
        GameData.getBeginCondQuestMap().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Test
    void confirmedNativeFinishAndFailFieldsOverrideRuntime() {
        var quest =
                GSON.fromJson(
                        """
                        {
                          "subId": 991001,
                          "mainId": 991,
                          "order": 99,
                          "acceptCond": [
                            {
                              "type": "QUEST_COND_STATE_EQUAL",
                              "param": [991000, 3]
                            }
                          ],
                          "finishCond": [
                            {
                              "type": "QUEST_CONTENT_COMPLETE_TALK",
                              "param": [1, 0]
                            }
                          ],
                          "failCond": [
                            {
                              "type": "QUEST_CONTENT_MONSTER_DIE",
                              "param": [2, 0]
                            }
                          ],
                          "finishExec": [
                            {
                              "type": "QUEST_EXEC_UNLOCK_POINT",
                              "param": ["3", "6"]
                            }
                          ]
                        }
                        """,
                        QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getSubId(), quest);

        var data =
                NativeQuestParser.parse(
                        new StringReader(
                                """
                                {
                                  "schemaVersion": 1,
                                  "version": "7.1.0-global",
                                  "quests": [
                                    {
                                      "mainId": 992,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "mainId": 992,
                                          "order": 7,
                                          "finishCond": [
                                            {
                                              "typeId": 4,
                                              "type": "QUEST_CONTENT_FINISH_PLOT",
                                              "param": [991001, 0]
                                            }
                                          ],
                                          "finishExec": [
                                            {
                                              "typeId": 19,
                                              "type": "QUEST_EXEC_REFRESH_GROUP_SUITE",
                                              "param": ["3", "133003429,1"]
                                            }
                                          ],
                                          "failExec": [
                                            {
                                              "typeId": 14,
                                              "type": "QUEST_EXEC_ROLLBACK_QUEST",
                                              "param": ["991000"]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);

        assertEquals(1, report.mainQuests());
        assertEquals(1, report.rows());
        assertEquals(1, report.mergedRows());
        assertEquals(0, report.missingRows());
        assertEquals(0, report.unresolvedLists());

        assertEquals(992, quest.getMainId());
        assertEquals(7, quest.getOrder());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.MAIN_ID));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.ORDER));

        assertEquals(1, quest.getFinishCond().size());
        assertEquals(QuestContent.QUEST_CONTENT_FINISH_PLOT, quest.getFinishCond().get(0).getType());
        assertArrayEquals(new int[] {991001, 0}, quest.getFinishCond().get(0).getParam());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FINISH_COND));

        assertTrue(quest.getFailCond().isEmpty());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FAIL_COND));

        assertEquals(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE, quest.getFinishExec().get(0).getType());
        assertEquals(QuestExec.QUEST_EXEC_ROLLBACK_QUEST, quest.getFailExec().get(0).getType());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FINISH_EXEC));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FAIL_EXEC));

        assertEquals(1, quest.getAcceptCond().size());
        assertEquals(QuestSource.QUEST_EXCEL, quest.getFieldSource(QuestField.ACCEPT_COND));
    }

    @Test
    void unresolvedNativeTypeLeavesLegacyRuntimeListUntouched() {
        var quest =
                GSON.fromJson(
                        """
                        {
                          "subId": 991001,
                          "mainId": 991,
                          "order": 1,
                          "finishCond": [
                            {
                              "type": "QUEST_CONTENT_COMPLETE_TALK",
                              "param": [12345, 0]
                            }
                          ]
                        }
                        """,
                        QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getSubId(), quest);

        var data =
                NativeQuestParser.parse(
                        new StringReader(
                                """
                                {
                                  "schemaVersion": 1,
                                  "version": "7.1.0-global",
                                  "quests": [
                                    {
                                      "mainId": 991,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "finishCond": [
                                            {
                                              "typeId": 2,
                                              "param": [54321, 0]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);

        assertEquals(1, report.unresolvedLists());
        assertEquals(1, quest.getFinishCond().size());
        assertEquals(QuestContent.QUEST_CONTENT_COMPLETE_TALK, quest.getFinishCond().get(0).getType());
        assertArrayEquals(new int[] {12345, 0}, quest.getFinishCond().get(0).getParam());
        assertEquals(QuestSource.QUEST_EXCEL, quest.getFieldSource(QuestField.FINISH_COND));
    }

    @Test
    void nativeOnlyRowIsNotMaterializedIntoRuntime() {
        var data =
                NativeQuestParser.parse(
                        new StringReader(
                                """
                                {
                                  "schemaVersion": 1,
                                  "version": "7.1.0-global",
                                  "quests": [
                                    {
                                      "mainId": 991,
                                      "quests": [
                                        {
                                          "subId": 991002,
                                          "order": 2,
                                          "finishCond": [
                                            {
                                              "typeId": 6,
                                              "type": "QUEST_CONTENT_TRIGGER_FIRE",
                                              "param": [1100, 0]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);

        assertEquals(1, report.rows());
        assertEquals(0, report.mergedRows());
        assertEquals(1, report.missingRows());
        assertFalse(GameData.getQuestDataMap().containsKey(NATIVE_ONLY_SUB_ID));
    }

    @Test
    void rejectsWrongVersionAndSchema() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeQuestParser.parse(
                                new StringReader(
                                        """
                                        {
                                          "schemaVersion": 2,
                                          "version": "7.1.0-global",
                                          "quests": []
                                        }
                                        """)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeQuestParser.parse(
                                new StringReader(
                                        """
                                        {
                                          "schemaVersion": 1,
                                          "version": "7.0.0-global",
                                          "quests": []
                                        }
                                        """)));
    }
}
