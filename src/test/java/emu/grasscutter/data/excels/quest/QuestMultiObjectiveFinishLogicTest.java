package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.game.quest.enums.LogicType;
import org.junit.jupiter.api.Test;

final class QuestMultiObjectiveFinishLogicTest {
    private static final Gson GSON = new Gson();

    @Test
    void restoresThreeDungeonRequirementFromFullQuestData() {
        var excel = GSON.fromJson("""
                {"subId":30901,"mainId":309,"acceptCond":[],
                 "finishCond":[
                   {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1001,0]},
                   {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1,0]},
                   {"type":"QUEST_CONTENT_FINISH_DUNGEON","param":[1003,0]}
                 ],
                 "beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        var full = GSON.fromJson("""
                {"subId":30901,"finishCondComb":"LOGIC_AND"}
                """, MainQuestData.SubQuestData.class);
        assertEquals(LogicType.LOGIC_AND, full.getFinishCondComb());
        excel.applyFrom(full);
        assertEquals(LogicType.LOGIC_AND, excel.getFinishCondComb());
        assertFalse(LogicType.calculate(excel.getFinishCondComb(), new int[] {1, 0, 0}));
        assertFalse(LogicType.calculate(excel.getFinishCondComb(), new int[] {1, 1, 0}));
        assertTrue(LogicType.calculate(excel.getFinishCondComb(), new int[] {1, 1, 1}));
    }

    @Test
    void explicitExcelLogicAndUnrelatedQuestsStayAuthoritative() {
        assertEquals(LogicType.LOGIC_OR, QuestData.effectiveMondstadtFinishLogic(
                309, LogicType.LOGIC_OR, LogicType.LOGIC_AND, 3));
        assertEquals(LogicType.LOGIC_NONE, QuestData.effectiveMondstadtFinishLogic(
                88888, LogicType.LOGIC_NONE, LogicType.LOGIC_AND, 3));
        assertEquals(LogicType.LOGIC_NONE, QuestData.effectiveMondstadtFinishLogic(
                309, LogicType.LOGIC_NONE, LogicType.LOGIC_AND, 1));
        assertEquals(LogicType.LOGIC_NONE, QuestData.effectiveMondstadtFinishLogic(
                309, LogicType.LOGIC_NONE, null, 3));
    }

    @Test
    void eitherPlotFailureOrTeamDeathCanFailReviewedCombatQuest() {
        var excel = GSON.fromJson("""
                {"subId":37602,"mainId":376,"acceptCond":[],
                 "finishCond":[],"failCond":[
                   {"type":"QUEST_CONTENT_NOT_FINISH_PLOT","param":[37602,0]},
                   {"type":"QUEST_CONTENT_TEAM_DEAD","param":[0,0]}
                 ],
                 "beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        var full = GSON.fromJson("""
                {"subId":37602,"failCondComb":"LOGIC_OR"}
                """, MainQuestData.SubQuestData.class);
        excel.applyFrom(full);
        assertEquals(LogicType.LOGIC_OR, excel.getFailCondComb());
        assertTrue(LogicType.calculate(excel.getFailCondComb(), new int[] {0, 1}));
        assertTrue(LogicType.calculate(excel.getFailCondComb(), new int[] {1, 0}));
        assertFalse(LogicType.calculate(excel.getFailCondComb(), new int[] {0, 0}));
    }

    @Test
    void explicitExcelFailureLogicIsPreserved() {
        assertEquals(LogicType.LOGIC_AND, QuestData.effectiveMondstadtFailLogic(
                376, LogicType.LOGIC_AND, LogicType.LOGIC_OR, 2));
        assertEquals(LogicType.LOGIC_NONE, QuestData.effectiveMondstadtFailLogic(
                88888, LogicType.LOGIC_NONE, LogicType.LOGIC_OR, 2));
    }

    @Test
    void alternativeCompletionRoutesUseReviewedOrLogic() {
        assertEquals(LogicType.LOGIC_OR, QuestData.effectiveMondstadtFinishLogic(
                307, LogicType.LOGIC_NONE, LogicType.LOGIC_OR, 2));
    }
}
