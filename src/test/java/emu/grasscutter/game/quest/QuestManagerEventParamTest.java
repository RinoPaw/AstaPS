package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QuestManagerEventParamTest {
    @Test
    void luaConditionEventWithoutIntegerArgumentsUsesZero() {
        assertEquals(0, QuestManager.firstQuestConditionParam());
    }

    @Test
    void explicitIntegerConditionEventKeepsItsLookupKey() {
        assertEquals(35601, QuestManager.firstQuestConditionParam(35601, 3));
        assertEquals(0, QuestManager.firstQuestConditionParam(0, 1));
    }
}
