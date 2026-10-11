package emu.grasscutter.game.player;

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
    void clientOwnedStateIsNotForceOpenedEvenIfDefault() {
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(true, true, "[]")));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state(false, true,
                        "[{\"condType\":\"OPEN_STATE_COND_QUEST\",\"param\":35601}]")));
    }
}
