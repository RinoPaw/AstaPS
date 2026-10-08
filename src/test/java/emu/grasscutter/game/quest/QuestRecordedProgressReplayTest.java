package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.PlayerProgress;
import emu.grasscutter.game.quest.enums.QuestContent;
import org.junit.jupiter.api.Test;

/** Quest Lua progress can be recorded before a dependent subquest starts. */
final class QuestRecordedProgressReplayTest {
    private static QuestData.QuestContentCondition condition(
            QuestContent type, String text, int id, int required) {
        var c = new QuestData.QuestContentCondition();
        c.setType(type);
        c.setParamStr(text);
        c.setParam(new int[] {id, 0});
        c.setCount(required);
        return c;
    }

    @Test
    void recordedSlimeKillCanFinishTheNextTutorialObjective() {
        var progress = new PlayerProgress();
        var condition = condition(QuestContent.QUEST_CONTENT_LUA_NOTIFY,
                "1330030022", 0, 1); // quest 35309
        assertFalse(QuestManager.hasRecordedQuestProgress(condition, progress));
        progress.addToCurrentProgress("1330030022", 1);
        assertTrue(QuestManager.hasRecordedQuestProgress(condition, progress));
        assertFalse(QuestManager.hasRecordedQuestProgress(
                condition(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "1330030023", 0, 1),
                progress)); // next wave remains independent
    }

    @Test
    void recordedProgressHonorsRequiredCountAndDefaultsZeroToOne() {
        var progress = new PlayerProgress();
        var numeric = condition(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS,
                "", 359011, 2);
        progress.addToCurrentProgress("359011", 1);
        assertFalse(QuestManager.hasRecordedQuestProgress(numeric, progress));
        progress.addToCurrentProgress("359011", 1);
        assertTrue(QuestManager.hasRecordedQuestProgress(numeric, progress));
        numeric.setCount(0);
        assertTrue(QuestManager.hasRecordedQuestProgress(numeric, progress));
    }

    @Test
    void completedDungeonsBeforeQuest30901StartsAreRecognized() {
        var progress = new PlayerProgress();
        var first = condition(QuestContent.QUEST_CONTENT_FINISH_DUNGEON, "", 1001, 0);
        var second = condition(QuestContent.QUEST_CONTENT_FINISH_DUNGEON, "", 1, 0);
        var third = condition(QuestContent.QUEST_CONTENT_FINISH_DUNGEON, "", 1003, 0);
        assertFalse(QuestManager.hasCompletedQuestDungeon(first, progress));

        progress.getCompletedDungeons().add(1001);
        progress.getCompletedDungeons().add(1);
        assertTrue(QuestManager.hasCompletedQuestDungeon(first, progress));
        assertTrue(QuestManager.hasCompletedQuestDungeon(second, progress));
        assertFalse(QuestManager.hasCompletedQuestDungeon(third, progress));

        progress.getCompletedDungeons().add(1003);
        assertTrue(QuestManager.hasCompletedQuestDungeon(third, progress));
        assertFalse(QuestManager.hasCompletedQuestDungeon(
                condition(QuestContent.QUEST_CONTENT_FINISH_DUNGEON, "", 9999, 0), progress));
    }

    @Test
    void invalidAndUnrelatedConditionsAreNotReplayed() {
        var progress = new PlayerProgress();
        progress.addToCurrentProgress("123", 1);
        assertFalse(QuestManager.hasRecordedQuestProgress(
                condition(QuestContent.QUEST_CONTENT_LUA_NOTIFY, "", 0, 1), progress));
        assertFalse(QuestManager.hasRecordedQuestProgress(
                condition(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, "", 0, 1), progress));
        assertFalse(QuestManager.hasRecordedQuestProgress(
                condition(QuestContent.QUEST_CONTENT_COMPLETE_TALK, "123", 123, 1), progress));
    }
}
