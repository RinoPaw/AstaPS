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
        GameData.getMainQuestDataMap().remove(991);
        GameData.getMainQuestDataMap().remove(992);
        GameData.getQuestTalkMap().remove(991123);
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
                          "isRewind": true,
                          "finishParent": false,
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
                                    {
                                      "mainId": 992,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "mainId": 992,
                                          "order": 7,
                                          "isRewind": false,
                                          "finishParent": true,
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
        assertEquals(1, report.materializedMainQuests());
        assertEquals(1, report.rows());
        assertEquals(1, report.mergedRows());
        assertEquals(0, report.missingRows());
        assertEquals(0, report.unresolvedLists());

        assertEquals(992, quest.getMainId());
        assertEquals(7, quest.getOrder());
        assertFalse(quest.isRewind());
        assertTrue(quest.isFinishParent());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.MAIN_ID));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.ORDER));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.REWIND));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FINISH_PARENT));

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
    void exactExportedTypeIdentityExpandsBeyondBootstrapAllowlist() {
        var quest =
                GSON.fromJson(
                        """
                        {
                          "subId": 991001,
                          "mainId": 991,
                          "order": 1,
                          "finishCond": [],
                          "finishExec": []
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
                                    {
                                      "mainId": 991,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "finishCond": [
                                            {
                                              "typeId": 2,
                                              "type": "QUEST_CONTENT_COMPLETE_TALK",
                                              "param": [54321, 0]
                                            }
                                          ],
                                          "finishExec": [
                                            {
                                              "typeId": 2,
                                              "type": "QUEST_EXEC_UNLOCK_POINT",
                                              "param": ["3", "6"]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);

        assertEquals(0, report.unresolvedLists());
        assertEquals(QuestContent.QUEST_CONTENT_COMPLETE_TALK, quest.getFinishCond().get(0).getType());
        assertArrayEquals(new int[] {54321, 0}, quest.getFinishCond().get(0).getParam());
        assertEquals(QuestExec.QUEST_EXEC_UNLOCK_POINT, quest.getFinishExec().get(0).getType());
        assertArrayEquals(new String[] {"3", "6"}, quest.getFinishExec().get(0).getParam());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FINISH_COND));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FINISH_EXEC));
    }

    @Test
    void mismatchedNativeTypeNameAndIdStayAuditOnly() {
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
                                    {
                                      "mainId": 991,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "finishCond": [
                                            {
                                              "typeId": 2,
                                              "type": "QUEST_CONTENT_FINISH_PLOT",
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
        assertEquals(QuestContent.QUEST_CONTENT_COMPLETE_TALK, quest.getFinishCond().get(0).getType());
        assertArrayEquals(new int[] {12345, 0}, quest.getFinishCond().get(0).getParam());
        assertEquals(QuestSource.QUEST_EXCEL, quest.getFieldSource(QuestField.FINISH_COND));
    }

    @Test
    void implementedMondstadtNativeTypesOverrideRuntime() {
        var quest =
                GSON.fromJson(
                        """
                        {
                          "subId": 991001,
                          "mainId": 991,
                          "order": 1,
                          "failCond": [],
                          "finishExec": []
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
                                    {
                                      "mainId": 991,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "failCond": [
                                            {
                                              "typeId": 21,
                                              "type": "QUEST_CONTENT_TEAM_DEAD",
                                              "param": [0, 0]
                                            }
                                          ],
                                          "finishExec": [
                                            {
                                              "typeId": 17,
                                              "type": "QUEST_EXEC_LOCK_POINT",
                                              "param": ["3", "1720"]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);

        assertEquals(0, report.unresolvedLists());
        assertEquals(QuestContent.QUEST_CONTENT_TEAM_DEAD, quest.getFailCond().get(0).getType());
        assertEquals(QuestExec.QUEST_EXEC_LOCK_POINT, quest.getFinishExec().get(0).getType());
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FAIL_COND));
        assertEquals(QuestSource.NATIVE_QUEST, quest.getFieldSource(QuestField.FINISH_EXEC));
    }

    @Test
    void exactTypeWithoutImplementedHandlerStaysAuditOnly() {
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
                                    {
                                      "mainId": 991,
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "finishCond": [
                                            {
                                              "typeId": 109,
                                              "type": "QUEST_CONTENT_CITY_LEVEL_UP",
                                              "param": [1, 0]
                                            }
                                          ],
                                          "finishExec": [
                                            {
                                              "typeId": 22,
                                              "type": "QUEST_EXEC_SET_WEATHER_GADGET",
                                              "param": ["3", "0"]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);

        assertEquals(2, report.unresolvedLists());
        assertEquals(QuestContent.QUEST_CONTENT_COMPLETE_TALK, quest.getFinishCond().get(0).getType());
        assertEquals(QuestExec.QUEST_EXEC_UNLOCK_POINT, quest.getFinishExec().get(0).getType());
        assertEquals(QuestSource.QUEST_EXCEL, quest.getFieldSource(QuestField.FINISH_COND));
        assertEquals(QuestSource.QUEST_EXCEL, quest.getFieldSource(QuestField.FINISH_EXEC));
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
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
    void nativeBundleMaterializesParentSkeletonWithoutLegacyBinOutput() {
        var quest =
                GSON.fromJson(
                        """
                        {
                          "subId": 991001,
                          "mainId": 991,
                          "order": 8
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
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
                                    {
                                      "mainId": 991,
                                      "suggestTrackMainQuestList": [992],
                                      "rewardIdList": [10991],
                                      "talks": [
                                        {"id": 991123, "questId": 991}
                                      ],
                                      "quests": [
                                        {
                                          "subId": 991001,
                                          "mainId": 991,
                                          "order": 8,
                                          "isRewind": true,
                                          "finishParent": true
                                        },
                                        {
                                          "subId": 991002,
                                          "mainId": 991,
                                          "order": 9
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """));

        var report = NativeQuestParser.apply(data);
        var parent = GameData.getMainQuestDataMap().get(991);

        assertEquals(1, report.materializedMainQuests());
        assertNotNull(parent);
        assertEquals(991, parent.getId());
        assertEquals(2, parent.getSubQuests().length);
        assertEquals(991001, parent.getSubQuests()[0].getSubId());
        assertEquals(8, parent.getSubQuests()[0].getOrder());
        assertTrue(parent.getSubQuests()[0].getRewind());
        assertTrue(parent.getSubQuests()[0].getFinishParent());
        assertEquals(991002, parent.getSubQuests()[1].getSubId());
        assertEquals(9, parent.getSubQuests()[1].getOrder());
        assertArrayEquals(new int[] {992}, parent.getSuggestTrackMainQuestList());
        assertArrayEquals(new int[] {10991}, parent.getRewardIdList());
        assertEquals(1, parent.getTalks().size());
        assertEquals(991123, parent.getTalks().get(0).getId());
        assertEquals(991, GameData.getQuestTalkMap().get(991123));
    }

    @Test
    void nativeOnlyRowIsNotMaterializedIntoRuntime() {
        var data =
                NativeQuestParser.parse(
                        new StringReader(
                                """
                                {
                                  "schemaVersion": 1,
                                  "gameVersion": "7.1.0-global",
                                  "mainQuests": [
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
    void rejectsCoverageThatDoesNotMatchExportedRows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeQuestParser.parse(
                                new StringReader(
                                        """
                                        {
                                          "schemaVersion": 1,
                                          "gameVersion": "7.1.0-global",
                                          "coverage": {
                                            "total": 2,
                                            "fullConsumed": 2,
                                            "failed": 0
                                          },
                                          "mainQuests": [
                                            {
                                              "mainId": 991,
                                              "quests": []
                                            }
                                          ],
                                          "failedMainQuests": []
                                        }
                                        """)));
    }

    @Test
    void rejectsDuplicateAndCrossParentNativeIds() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeQuestParser.parse(
                                new StringReader(
                                        """
                                        {
                                          "schemaVersion": 1,
                                          "gameVersion": "7.1.0-global",
                                          "mainQuests": [
                                            {
                                              "mainId": 991,
                                              "quests": [
                                                {"subId": 991001, "mainId": 991}
                                              ]
                                            },
                                            {
                                              "mainId": 992,
                                              "quests": [
                                                {"subId": 991001, "mainId": 992}
                                              ]
                                            }
                                          ]
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
                                          "gameVersion": "7.1.0-global",
                                          "mainQuests": [
                                            {
                                              "mainId": 991,
                                              "quests": [
                                                {"subId": 991001, "mainId": 992}
                                              ]
                                            }
                                          ]
                                        }
                                        """)));
    }

    @Test
    void rejectsOverlappingSuccessAndFailureSets() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NativeQuestParser.parse(
                                new StringReader(
                                        """
                                        {
                                          "schemaVersion": 1,
                                          "gameVersion": "7.1.0-global",
                                          "coverage": {
                                            "total": 2,
                                            "fullConsumed": 1,
                                            "failed": 1
                                          },
                                          "mainQuests": [
                                            {
                                              "mainId": 991,
                                              "quests": []
                                            }
                                          ],
                                          "failedMainQuests": [
                                            {
                                              "mainId": 991,
                                              "size": 10
                                            }
                                          ]
                                        }
                                        """)));
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
                                          "gameVersion": "7.1.0-global",
                                          "mainQuests": []
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
                                          "gameVersion": "7.0.0-global",
                                          "mainQuests": []
                                        }
                                        """)));
    }
}
