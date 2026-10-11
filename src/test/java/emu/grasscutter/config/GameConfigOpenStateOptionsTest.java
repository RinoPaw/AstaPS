package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

final class GameConfigOpenStateOptionsTest {
    @Test
    void freshAndExistingConfigsDefaultToNoMassUnlock() {
        var fresh = new Gson().fromJson("{}", GameConfig.class);
        var existing = new Gson().fromJson("{\"quests\":{\"enabled\":false}}", GameConfig.class);
        assertNotNull(fresh.openStates);
        assertFalse(fresh.openStates.unlockAllOnLogin);
        assertFalse(existing.openStates.unlockAllOnLogin);
        assertFalse(new GameConfig.OpenStateOptions().unlockAllOnLogin);
    }

    @Test
    void explicitOptInRoundTripsInGameJson() {
        var enabled = new Gson().fromJson(
                "{\"quests\":{\"enabled\":true},\"openStates\":{\"unlockAllOnLogin\":true}}",
                GameConfig.class);
        assertTrue(enabled.openStates.unlockAllOnLogin);
        assertTrue(enabled.quests.enabled);
        assertTrue(new Gson().toJson(enabled).contains("\"unlockAllOnLogin\":true"));
    }
}
