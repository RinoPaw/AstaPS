package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;
import org.junit.jupiter.api.Test;

/** A queued group spawn must not run after its triggering quest has changed state. */
final class ExecRefreshGroupSuiteDispatchStateTest {
    // GameQuest has Morphia-only transient runtime fields; do not reflect into
    // server/player internals when deserializing a minimal saved quest for a unit test.
    private static final com.google.gson.Gson GSON = new GsonBuilder()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes field) {
                    return field.getAnnotation(dev.morphia.annotations.Transient.class) != null;
                }

                @Override
                public boolean shouldSkipClass(Class<?> type) {
                    return false;
                }
            })
            .create();

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
