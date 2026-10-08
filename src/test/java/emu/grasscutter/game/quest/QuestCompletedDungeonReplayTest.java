package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.PlayerProgress;
import emu.grasscutter.game.quest.enums.QuestContent;
import org.junit.jupiter.api.Test;

final class QuestCompletedDungeonReplayTest {
    private static QuestData.QuestContentCondition dungeon(int id) {
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QuestContent.QUEST_CONTENT_FINISH_DUNGEON);
        condition.setParam(new int[] {id, 0});
        return condition;
    }

    @Test
    void persistedClearHistoryCanBeReplayedWhen30901BeginsLate() {
        var progress = new PlayerProgress();
        var first = dungeon(1001);
        var second = dungeon(1);
        var third = dungeon(1003);
        assertFalse(QuestManager.hasCompletedQuestDungeon(first, progress));

        progress.getCompletedDungeons().add(1001);
        progress.getCompletedDungeons().add(1);
        assertTrue(QuestManager.hasCompletedQuestDungeon(first, progress));
        assertTrue(QuestManager.hasCompletedQuestDungeon(second, progress));
        assertFalse(QuestManager.hasCompletedQuestDungeon(third, progress));
    }

    @Test
    void unrelatedProgressAndMalformedConditionsDoNotReplay() {
        var progress = new PlayerProgress();
        progress.getCompletedDungeons().add(1001);
        var wrongType = dungeon(1001);
        wrongType.setType(QuestContent.QUEST_CONTENT_ENTER_DUNGEON);
        assertFalse(QuestManager.hasCompletedQuestDungeon(wrongType, progress));
        var noId = dungeon(1001);
        noId.setParam(new int[0]);
        assertFalse(QuestManager.hasCompletedQuestDungeon(noId, progress));
        assertFalse(QuestManager.hasCompletedQuestDungeon(null, progress));
    }
}
