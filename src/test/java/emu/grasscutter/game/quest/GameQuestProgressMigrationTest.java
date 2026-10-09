package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.quest.QuestData;
import org.junit.jupiter.api.Test;

final class GameQuestProgressMigrationTest {
    private static final Gson GSON = new Gson();

    private static QuestData definition(int subId, int finishCount, int failCount) {
        StringBuilder json = new StringBuilder(
                "{\"subId\":" + subId + ",\"mainId\":359,\"finishCond\":[");
        for (int i = 0; i < finishCount; i++) {
            if (i != 0) json.append(',');
            json.append("{\"type\":\"QUEST_CONTENT_FINISH_PLOT\",\"param\":[")
                    .append(subId + i).append(",0]}");
        }
        json.append("],\"failCond\":[");
        for (int i = 0; i < failCount; i++) {
            if (i != 0) json.append(',');
            json.append("{\"type\":\"QUEST_CONTENT_NOT_FINISH_PLOT\",\"param\":[")
                    .append(subId + i).append(",0]}");
        }
        return GSON.fromJson(json.append("]}").toString(), QuestData.class);
    }

    @Test
    void oldSaveWithDifferentConditionCountsResetsBothProgressArrays() {
        var quest = GSON.fromJson("""
                {"subQuestId":35901,"finishProgressList":[1],
                 "failProgressList":[1,0,1]}
                """, GameQuest.class);
        quest.setConfig(definition(35901, 2, 1));

        assertArrayEquals(new int[]{0, 0}, quest.getFinishProgressList());
        assertArrayEquals(new int[]{0}, quest.getFailProgressList());
    }

    @Test
    void unchangedDefinitionsKeepRecordedProgress() {
        var quest = GSON.fromJson("""
                {"subQuestId":35901,"finishProgressList":[1,0],
                 "failProgressList":[1]}
                """, GameQuest.class);
        var originalFinish = quest.getFinishProgressList();
        var originalFail = quest.getFailProgressList();
        quest.setConfig(definition(35901, 2, 1));

        assertSame(originalFinish, quest.getFinishProgressList());
        assertSame(originalFail, quest.getFailProgressList());
        assertArrayEquals(new int[]{1, 0}, quest.getFinishProgressList());
        assertArrayEquals(new int[]{1}, quest.getFailProgressList());
    }

    @Test
    void missingLegacyArraysAreInitializedForCurrentDefinition() {
        var quest = GSON.fromJson("{\"subQuestId\":35901}", GameQuest.class);
        quest.setConfig(definition(35901, 1, 2));

        assertArrayEquals(new int[]{0}, quest.getFinishProgressList());
        assertArrayEquals(new int[]{0, 0}, quest.getFailProgressList());
    }

    @Test
    void unrelatedDefinitionMustNotChangeTheSavedQuest() {
        var quest = GSON.fromJson("""
                {"subQuestId":35901,"finishProgressList":[1],"failProgressList":[1]}
                """, GameQuest.class);
        quest.setConfig(definition(39403, 2, 2));

        assertArrayEquals(new int[]{1}, quest.getFinishProgressList());
        assertArrayEquals(new int[]{1}, quest.getFailProgressList());
        assertNull(quest.getQuestData());
    }
}
