package emu.grasscutter.server.packet.send;

import static emu.grasscutter.game.quest.enums.QuestState.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AmberQuestListVisibilityTest {
    @Test
    void sendsUnfinishedQuestWhenEffectiveQuestingIsActive() {
        assertTrue(PacketQuestListUpdateNotify.includesQuestState(true, QUEST_STATE_UNFINISHED));
        assertTrue(PacketQuestListUpdateNotify.includesQuestState(true, QUEST_STATE_UNSTARTED));
    }

    @Test
    void unfinishedQuestMayBeFilteredWhenWorldScriptsAreDisabled() {
        // Effective questing requires both questing.enabled and enableScriptInBigWorld.
        assertFalse(PacketQuestListUpdateNotify.includesQuestState(false, QUEST_STATE_UNFINISHED));
        assertTrue(PacketQuestListUpdateNotify.includesQuestState(false, QUEST_STATE_FINISHED));
    }
}
