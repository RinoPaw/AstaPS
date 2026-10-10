package emu.grasscutter.game.quest;

import static emu.grasscutter.game.quest.QuestClientVisibility.includes;
import static emu.grasscutter.game.quest.enums.QuestState.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QuestClientVisibilityTest {
    @Test
    void activeQuestingStillSendsAllRegularQuestUpdates() {
        assertTrue(includes(true, 356, 35603, QUEST_STATE_UNFINISHED, false));
        assertTrue(includes(true, 351, 35101, QUEST_STATE_UNFINISHED, false));
    }

    @Test
    void completedQuestsRemainVisibleWhenQuestingDisabled() {
        assertTrue(includes(false, 356, 35601, QUEST_STATE_FINISHED, false));
        assertTrue(includes(false, 351, 35101, QUEST_STATE_FINISHED, false));
    }

    @Test
    void amberNextStepsStayHiddenUntilTalkCompletes() {
        assertFalse(includes(false, 356, 35602, QUEST_STATE_UNFINISHED, false));
        assertFalse(includes(false, 356, 35603, QUEST_STATE_UNFINISHED, false));
        assertTrue(includes(false, 356, 35602, QUEST_STATE_UNFINISHED, true));
        assertTrue(includes(false, 356, 35603, QUEST_STATE_UNFINISHED, true));
    }

    @Test
    void doNotExposeUnrelatedUnfinishedQuestsOrUnstartedSteps() {
        assertFalse(includes(false, 354, 35404, QUEST_STATE_UNFINISHED, true));
        assertFalse(includes(false, 356, 35604, QUEST_STATE_UNFINISHED, true));
        assertFalse(includes(false, 356, 35603, QUEST_STATE_UNSTARTED, true));
        assertFalse(includes(false, 357, 35603, QUEST_STATE_UNFINISHED, true));
    }

    @Test
    void preserveExplicitFinishAndFailureBehavior() {
        assertTrue(includes(false, 356, 35603, QUEST_STATE_FINISHED, true));
        assertFalse(includes(false, 356, 35603, QUEST_STATE_FAILED, true));
    }
}
