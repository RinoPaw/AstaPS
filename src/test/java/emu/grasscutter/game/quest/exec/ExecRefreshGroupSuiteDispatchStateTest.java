package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;
import org.junit.jupiter.api.Test;

/** A queued group spawn must not run after its triggering quest has changed state. */
final class ExecRefreshGroupSuiteDispatchStateTest {
    private static final Gson GSON = new Gson();

    @Test
    void finishedQuestCannotReplayPendingCombatSpawn() {
        var quest = GSON.fromJson(
                "{\"state\":\"QUEST_STATE_FINISHED\",\"subQuestId\":36001}",
                GameQuest.class);
        assertTrue(new ExecRefreshGroupSuite().execute(
                quest, null, QuestState.QUEST_STATE_UNFINISHED));
    }

    @Test
    void startedQuestCannotReplayObsoleteFinishedCleanup() {
        var quest = GSON.fromJson(
                "{\"state\":\"QUEST_STATE_UNFINISHED\",\"subQuestId\":36001}",
                GameQuest.class);
        assertTrue(new ExecRefreshGroupSuite().execute(
                quest, null, QuestState.QUEST_STATE_FINISHED));
    }
}
