package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

public final class GameConfigRewardSchemaTest {
    @Test
    public void chestRatesDefaultToVanillaAndDoNotExposeTierReplacements() {
        var rates = new GameConfig.ChestRates();
        String json = JsonUtils.encode(rates);

        assertEquals(1.0f, rates.primogems);
        assertEquals(1.0f, rates.mora);
        assertEquals(1.0f, rates.artifacts);
        assertEquals(1.0f, rates.weapons);
        assertEquals(1.0f, rates.expBooks);
        assertTrue(json.contains("primogems"));
        assertTrue(json.contains("expBooks"));
        assertFalse(json.contains("common"));
        assertFalse(json.contains("exquisite"));
        assertFalse(json.contains("precious"));
        assertFalse(json.contains("luxurious"));
        assertFalse(json.contains("enabled"));
    }

    @Test
    public void rewardJsonUsesOnlyCanonicalLeyLineFields() {
        String leyLines = JsonUtils.encode(new GameConfig.LeyLineRates());

        assertTrue(leyLines.contains("wealth"));
        assertTrue(leyLines.contains("revelation"));
        assertFalse(leyLines.contains("global"));
        assertFalse(leyLines.contains("experienceBooks"));
        assertFalse(leyLines.contains("\"exp\""));
        assertFalse(leyLines.contains("\"mora\""));
    }

    @Test
    public void rewardDefaultsMatchVanillaRates() {
        var rewards = new GameConfig.Rewards();

        assertEquals(1.0f, rewards.adventureExp);
        assertEquals(1.0f, rewards.mora);
        assertEquals(1.0f, rewards.leyLines.wealth);
        assertEquals(1.0f, rewards.leyLines.revelation);
        assertEquals(5, rewards.waypoint.primogems);
        assertEquals(50, rewards.waypoint.adventureExp);
        assertEquals(5, rewards.statue.primogems);
        assertEquals(50, rewards.statue.adventureExp);
    }
}
