package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.OpenStateData;
import org.junit.jupiter.api.Test;

final class PlayerProgressManagerOpenStateTest {
    private static OpenStateData state(boolean defaultState, boolean clientOpen, String conditions) {
        var json = "{\"id\":31,\"defaultState\":" + defaultState
                + ",\"allowClientOpen\":" + clientOpen
                + ",\"cond\":" + conditions + "}";
        return new Gson().fromJson(json, OpenStateData.class);
    }

    @Test
    void serverOwnedNonDefaultWithoutConditionsStaysLockedOnLogin() {
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(false, false, "[]")));
    }

    @Test
    void resourceDefaultWithoutConditionsStillOpens() {
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(true, false, "[]")));
    }

    @Test
    void serverOwnedQuestConditionStillQualifiesForEvaluation() {
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(false, false,
                        "[{\"condType\":\"OPEN_STATE_COND_QUEST\",\"param\":35601}]")));
    }

    @Test
    void parsesActualSevenOneQuestConditionSnakeCase() {
        var state = new Gson().fromJson(
                "{\"id\":31,\"cond\":[{\"cond_type\":\"OPEN_STATE_COND_QUEST\",\"param\":2010001},{}]}",
                OpenStateData.class);
        assertEquals(OpenStateData.OpenStateCondType.OPEN_STATE_COND_QUEST,
                state.getCond().get(0).getCondType());
        assertEquals(2010001, state.getCond().get(0).getParam());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(state));
    }

    @Test
    void acceptsOlderCamelCaseAndRejectsOnlyUnknownConditions() {
        var legacy = new Gson().fromJson(
                "{\"id\":30,\"cond\":[{\"condType\":\"OPEN_STATE_COND_PLAYER_LEVEL\",\"param\":2}]}",
                OpenStateData.class);
        assertEquals(OpenStateData.OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL,
                legacy.getCond().get(0).getCondType());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(legacy));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(false, false, "[{}]")));
    }

    @Test
    void clientOwnedStateIsNotForceOpenedEvenIfDefault() {
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(true, true, "[]")));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(false, true,
                        "[{\"condType\":\"OPEN_STATE_COND_QUEST\",\"param\":35601}]")));
    }
}
