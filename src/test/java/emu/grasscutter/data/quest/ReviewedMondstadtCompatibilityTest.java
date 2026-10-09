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

final class ReviewedMondstadtCompatibilityTest {
    private static final Gson GSON = new Gson();

    @AfterEach
    void cleanUp() {
        for (int sub : new int[] {35302, 30901, 37602, 35100, 35203, 38201, 39807}) {
            GameData.getQuestDataMap().remove(sub);
            GameData.getBeginCondQuestMap().values()
                    .forEach(rows -> rows.removeIf(q -> q.getSubId() == sub));
        }
        GameData.getBeginCondQuestMap().entrySet().removeIf(e -> e.getValue().isEmpty());
        QuestData.clearNormalizationAudit();
    }

    @Test
    void reviewedTutorialBeginsWithRestoredSuiteAndPrerequisite() {
        var excel = GSON.fromJson("""
                {"subId":35302,"mainId":353,"order":2,
                 "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[0,3,0]}],
                 "beginExec":[]}
                """, QuestData.class);
        excel.onLoad();
        var bin = GSON.fromJson("""
                {"subId":35302,"mainId":353,
                 "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[35301,3,0]}],
                 "beginExec":[{"type":"QUEST_EXEC_REFRESH_GROUP_SUITE",
                               "param":["3","133003002,2"]}]}
                """, MainQuestData.SubQuestData.class);
        excel.mergeFromBinOutput(bin, 353);
        assertEquals(35301, excel.getAcceptCond().get(0).getParam()[0]);
        assertEquals(QuestSource.BIN_OUTPUT, excel.getFieldSource(QuestField.ACCEPT_COND));
        assertEquals(1, excel.getBeginExec().size());
        assertEquals(QuestSource.BIN_OUTPUT, excel.getFieldSource(QuestField.BEGIN_EXEC));
    }

    @Test
    void multiDungeonAndBattleFailureCombinatorsRetainHistoricalOperators() {
        var dungeon = GSON.fromJson("""
                {"subId":30901,"mainId":309,"order":1,
                 "acceptCond":[],"finishCond":[
                   {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1001,0]},
                   {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1,0]},
                   {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1003,0]}]}
                """, QuestData.class);
        dungeon.onLoad();
        dungeon.mergeFromBinOutput(GSON.fromJson(
                "{\"subId\":30901,\"finishCondComb\":\"LOGIC_AND\"}",
                MainQuestData.SubQuestData.class), 309);
        assertEquals(LogicType.LOGIC_AND, dungeon.getFinishCondComb());
        assertEquals(QuestSource.BIN_OUTPUT,
                dungeon.getFieldSource(QuestField.FINISH_COND_COMB));

        var battle = GSON.fromJson("""
                {"subId":37602,"mainId":376,"order":2,
                 "acceptCond":[],"failCond":[
                   {"type":"QUEST_CONTENT_NOT_FINISH_PLOT","param":[37602,0]},
                   {"type":"QUEST_CONTENT_TEAM_DEAD","param":[0,0]}]}
                """, QuestData.class);
        battle.onLoad();
        battle.mergeFromBinOutput(GSON.fromJson(
                "{\"subId\":37602,\"failCondComb\":\"LOGIC_OR\"}",
                MainQuestData.SubQuestData.class), 376);
        assertEquals(LogicType.LOGIC_OR, battle.getFailCondComb());
        assertEquals(QuestSource.BIN_OUTPUT,
                battle.getFieldSource(QuestField.FAIL_COND_COMB));
    }

    @Test
    void explicitExcelBeginActionsStayAuthoritative() {
        var excel = GSON.fromJson("""
                {"subId":35302,"mainId":353,"order":2,"acceptCond":[],
                 "beginExec":[{"type":"QUEST_EXEC_NOTIFY_GROUP_LUA",
                               "param":["3","133003448"]}]}
                """, QuestData.class);
        excel.onLoad();
        var bin = GSON.fromJson("""
                {"subId":35302,"mainId":353,
                 "beginExec":[{"type":"QUEST_EXEC_REFRESH_GROUP_SUITE",
                               "param":["3","133003002,2"]}]}
                """, MainQuestData.SubQuestData.class);
        excel.mergeFromBinOutput(bin, 353);
        assertEquals(1, excel.getBeginExec().size());
        assertEquals(QuestSource.QUEST_EXCEL, excel.getFieldSource(QuestField.BEGIN_EXEC));
    }

    @Test
    void amberOpeningSupportsEitherOfTwoVerifiedTriggerFires() {
        // Historical 3.7/4.0 and 7.1 BinOutput agree: 35201 is OR.
        var excel = GSON.fromJson("""
                {"subId":35201,"mainId":352,"order":1,"acceptCond":[],
                 "finishCond":[
                    {"type":"QUEST_CONTENT_TRIGGER_FIRE","param":[1001,0]},
                    {"type":"QUEST_CONTENT_TRIGGER_FIRE","param":[1095,0]}
                 ],"finishCondComb":"LOGIC_NONE"}
                """, QuestData.class);
        excel.onLoad();
        excel.mergeFromBinOutput(GSON.fromJson("""
                {"subId":35201,"mainId":352,"finishCondComb":"LOGIC_OR"}
                """, MainQuestData.SubQuestData.class), 352);
        assertEquals(LogicType.LOGIC_OR, excel.getFinishCondComb());
        assertEquals(QuestSource.BIN_OUTPUT,
                excel.getFieldSource(QuestField.FINISH_COND_COMB));
    }

    @Test
    void stormCleanupReturnsFromReviewedBinWhenExcelOmitsFinishActions() {
        var excel = GSON.fromJson("""
                {"subId":35901,"mainId":359,"order":1,"acceptCond":[],
                 "finishExec":[]}
                """, QuestData.class);
        excel.onLoad();
        var bin = GSON.fromJson("""
                {"subId":35901,"mainId":359,
                 "finishExec":[
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["3","0"]},
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["1","0"]}
                 ]}
                """, MainQuestData.SubQuestData.class);
        excel.mergeFromBinOutput(bin, 359);
        assertEquals(2, excel.getFinishExec().size());
        assertArrayEquals(new String[]{"3", "0"}, excel.getFinishExec().get(0).getParam());
        assertArrayEquals(new String[]{"1", "0"}, excel.getFinishExec().get(1).getParam());
        assertEquals(QuestSource.BIN_OUTPUT,
                excel.getFieldSource(QuestField.FINISH_EXEC));
    }

    @Test
    void thirdActCleanupRetainsVerifiedAvatarWeatherAndLuaSequence() {
        var excel = GSON.fromJson("""
                {"subId":39403,"mainId":394,"order":3,"acceptCond":[],
                 "finishExec":[]}
                """, QuestData.class);
        excel.onLoad();
        var bin = GSON.fromJson("""
                {"subId":39403,"mainId":394,
                 "finishExec":[
                     {"type":"QUEST_EXEC_REMOVE_TRIAL_AVATAR","param":["5"]},
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["2","0"]},
                     {"type":"QUEST_EXEC_NOTIFY_GROUP_LUA","param":["3","133007183"]}
                 ]}
                """, MainQuestData.SubQuestData.class);
        excel.mergeFromBinOutput(bin, 394);
        assertEquals(3, excel.getFinishExec().size());
        assertArrayEquals(new String[]{"5"}, excel.getFinishExec().get(0).getParam());
        assertArrayEquals(new String[]{"2", "0"}, excel.getFinishExec().get(1).getParam());
        assertArrayEquals(new String[]{"3", "133007183"},
                excel.getFinishExec().get(2).getParam());
        assertEquals(QuestSource.BIN_OUTPUT,
                excel.getFieldSource(QuestField.FINISH_EXEC));
    }

    @Test
    void reviewedFinishCleanupCannotOverrideExcelOrAcceptChangedActions() {
        var excel = GSON.fromJson("""
                {"subId":35901,"mainId":359,"order":1,"acceptCond":[],
                 "finishExec":[{"type":"QUEST_EXEC_NOTIFY_GROUP_LUA",
                                "param":["3","123"]}]}
                """, QuestData.class);
        excel.onLoad();
        var valid = GSON.fromJson("""
                {"subId":35901,"mainId":359,
                 "finishExec":[
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["3","0"]},
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["1","0"]}]}
                """, MainQuestData.SubQuestData.class);
        excel.mergeFromBinOutput(valid, 359);
        assertEquals(1, excel.getFinishExec().size());
        assertEquals(QuestSource.QUEST_EXCEL,
                excel.getFieldSource(QuestField.FINISH_EXEC));

        var empty = GSON.fromJson("""
                {"subId":35901,"mainId":359,"order":1,"acceptCond":[],
                 "finishExec":[]}
                """, QuestData.class);
        empty.onLoad();
        var changed = GSON.fromJson("""
                {"subId":35901,"mainId":359,
                 "finishExec":[
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["3","1"]},
                     {"type":"QUEST_EXEC_SET_WEATHER_GADGET","param":["1","0"]}]}
                """, MainQuestData.SubQuestData.class);
        empty.mergeFromBinOutput(changed, 359);
        assertTrue(empty.getFinishExec().isEmpty());
        assertEquals(QuestSource.QUEST_EXCEL,
                empty.getFieldSource(QuestField.FINISH_EXEC));
    }

    @Test
    void allFourOtherReviewedMultiObjectiveFinishCombinatorsAreRestored() {
        // Resource-side 7.1 audit contains these, but the server's reviewed
        // whitelist previously omitted all four. Two are OR alternatives,
        // and 35203 requires both plot completion and its fire trigger.
        record Case(int id, int main, String logic, String firstType,
                    int firstId, String secondType, int secondId) {}
        var cases = java.util.List.of(
                new Case(35100, 351, "LOGIC_OR",
                        "QUEST_CONTENT_FINISH_PLOT", 35100,
                        "QUEST_CONTENT_TRIGGER_FIRE", 1053),
                new Case(35203, 352, "LOGIC_AND",
                        "QUEST_CONTENT_FINISH_PLOT", 35203,
                        "QUEST_CONTENT_TRIGGER_FIRE", 1172),
                new Case(38201, 382, "LOGIC_OR",
                        "QUEST_CONTENT_TRIGGER_FIRE", 1065,
                        "QUEST_CONTENT_TRIGGER_FIRE", 1066),
                new Case(39807, 398, "LOGIC_OR",
                        "QUEST_CONTENT_COMPLETE_TALK", 39807,
                        "QUEST_CONTENT_COMPLETE_TALK", 39806));
        for (var sample : cases) {
            var excel = GSON.fromJson("""
                    {"subId":%d,"mainId":%d,"order":1,"acceptCond":[],
                     "finishCondComb":"LOGIC_NONE","finishCond":[
                       {"type":"%s","param":[%d,0]},
                       {"type":"%s","param":[%d,0]}
                     ]}
                    """.formatted(sample.id(), sample.main(),
                        sample.firstType(), sample.firstId(),
                        sample.secondType(), sample.secondId()), QuestData.class);
            excel.onLoad();
            excel.mergeFromBinOutput(GSON.fromJson(
                    "{\"subId\":" + sample.id() + ",\"mainId\":" + sample.main()
                            + ",\"finishCondComb\":\"" + sample.logic() + "\"}",
                    MainQuestData.SubQuestData.class), sample.main());
            assertEquals(LogicType.valueOf(sample.logic()), excel.getFinishCondComb(),
                    "reviewed logical operator for " + sample.id());
            assertEquals(QuestSource.BIN_OUTPUT,
                    excel.getFieldSource(QuestField.FINISH_COND_COMB),
                    "provenance for " + sample.id());
        }
    }

    @Test
    void explicitExcelMultiObjectiveLogicCannotBeReplacedByCompatibility() {
        var excel = GSON.fromJson("""
                {"subId":35203,"mainId":352,"order":3,"acceptCond":[],
                 "finishCondComb":"LOGIC_OR",
                 "finishCond":[
                   {"type":"QUEST_CONTENT_FINISH_PLOT","param":[35203,0]},
                   {"type":"QUEST_CONTENT_TRIGGER_FIRE","param":[1172,0]}]}
                """, QuestData.class);
        excel.onLoad();
        excel.mergeFromBinOutput(GSON.fromJson("""
                {"subId":35203,"mainId":352,"finishCondComb":"LOGIC_AND"}
                """, MainQuestData.SubQuestData.class), 352);
        assertEquals(LogicType.LOGIC_OR, excel.getFinishCondComb());
        assertEquals(QuestSource.QUEST_EXCEL,
                excel.getFieldSource(QuestField.FINISH_COND_COMB));
    }

    @Test
    void confirmedNativeQuestFinishConditionsRemainAuthoritative() {
        var excel = GSON.fromJson("""
                {"subId":30901,"mainId":309,"order":1,"acceptCond":[],
                 "finishCond":[{"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1001,0]}]}
                """, QuestData.class);
        excel.onLoad();
        excel.mergeFromNative(NativeQuestOverlay.builder()
                .subId(30901)
                .finishCond(java.util.List.of())
                .build());
        assertTrue(excel.getFinishCond().isEmpty());
        assertEquals(QuestSource.NATIVE_QUEST,
                excel.getFieldSource(QuestField.FINISH_COND));
    }
}
