package emu.grasscutter.game.quest.content;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import org.junit.jupiter.api.Test;

/** Quest completion/failure events must match their own scene or monster group. */
final class QuestContentEventSourceTest {
    @Test
    void enteringUnrelatedSceneCannotSatisfySceneThreeQuest() {
        assertTrue(ContentEnterMyWorld.matchesScene(3, 3, 3));
        assertFalse(ContentEnterMyWorld.matchesScene(3, 2001, 2001));
        assertFalse(ContentEnterMyWorld.matchesScene(3, 3, 2001));
        assertFalse(ContentEnterMyWorld.matchesScene(2001, 3, 3));
        assertFalse(ContentEnterMyWorld.matchesScene(0, 0, 0));
    }

    @Test
    void malformedSceneEventDoesNotThrowOrFinish() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {3, 0});
        assertFalse(new ContentEnterMyWorld().execute(null, condition, ""));
    }

    @Test
    void otherGroupKillCannotClearThisQuestGroup() {
        // Act III 39703 and 38802 use two different group clear objectives.
        assertTrue(ContentClearGroupMonster.matchesGroupEvent(133002233, 133002233));
        assertTrue(ContentClearGroupMonster.matchesGroupEvent(133004015, 133004015));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(133002233, 133004015));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(133004015, 133002233));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(133002233));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(0, 0));
    }

    @Test
    void unrelatedGroupEventDoesNotNeedSceneManager() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {133002233, 0});
        assertFalse(new ContentClearGroupMonster().execute(null, condition, "", 133004015));
        assertFalse(new ContentClearGroupMonster().execute(null, condition, ""));
    }

    @Test
    void gadgetStateConditionMatchesGroupConfigAndNewState() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {133707072, 72010});
        condition.setParamStr("201");
        var handler = new ContentGadgetStateChange();

        assertTrue(handler.execute(null, condition, "", 133707072, 72010, 201));
        assertFalse(handler.execute(null, condition, "", 133707002, 72010, 201));
        assertFalse(handler.execute(null, condition, "", 133707072, 72011, 201));
        assertFalse(handler.execute(null, condition, "", 133707072, 72010, 0));
        assertFalse(handler.execute(null, condition, "", 133707072, 72010));
    }

    @Test
    void unrelatedInventoryEventsDoNotEvaluateItemConditions() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {100305, 1});

        assertFalse(new ContentObtainItem().execute(null, condition, "", 100313, 1));
        assertFalse(new ContentItemLessThan().execute(null, condition, "", 100313, 1));
        assertFalse(new ContentObtainItem().execute(null, condition, ""));
        assertFalse(new ContentItemLessThan().execute(null, condition, ""));
    }
}
