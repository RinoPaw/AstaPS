package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.config.GameConfig;
import emu.grasscutter.data.excels.OpenStateData;
import org.junit.jupiter.api.Test;

final class PlayerProgressManagerOpenStateTest {
    private static final Gson GSON = new Gson();

    private static OpenStateData state(String json) {
        return GSON.fromJson(json, OpenStateData.class);
    }

    @Test
    void massUnlockRequiresExplicitOptIn() {
        var options = new GameConfig.OpenStateOptions();
        assertFalse(PlayerProgressManager.shouldUnlockAllOnLogin(options));
        assertFalse(PlayerProgressManager.shouldUnlockAllOnLogin(null));
        options.unlockAllOnLogin = true;
        assertTrue(PlayerProgressManager.shouldUnlockAllOnLogin(options));
    }

    @Test
    void nativeSevenOneSnakeCaseQuestConditionIsParsed() {
        var row = state(
                "{\"id\":31,\"cond\":[{\"cond_type\":\"OPEN_STATE_COND_QUEST\",\"param\":2010001},{}]}");
        assertEquals(OpenStateData.OpenStateCondType.OPEN_STATE_COND_QUEST,
                row.getCond().get(0).getCondType());
        assertEquals(2010001, row.getCond().get(0).getParam());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(row));
    }

    @Test
    void legacyCamelCaseFieldRemainsCompatible() {
        var row = state(
                "{\"id\":30,\"cond\":[{\"condType\":\"OPEN_STATE_COND_PLAYER_LEVEL\",\"param\":2}]}");
        assertEquals(OpenStateData.OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL,
                row.getCond().get(0).getCondType());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(row));
    }

    @Test
    void emptyNonDefaultConditionsDoNotOpenButDefaultsRemain() {
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state("{\"id\":31,\"cond\":[{},{}]}")));
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state("{\"id\":3,\"defaultState\":true,\"cond\":[{},{}]}")));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(
                state("{\"id\":7,\"defaultState\":true,\"allowClientOpen\":true,\"cond\":[]}")));
    }
}
