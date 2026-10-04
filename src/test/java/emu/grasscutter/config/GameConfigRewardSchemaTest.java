package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class GameConfigRewardSchemaTest {
    @Test
    public void chestRatesDefaultToVanilla() {
        var rates = new GameConfig.ChestRates();

        assertEquals(1.0f, rates.primogems);
        assertEquals(1.0f, rates.mora);
        assertEquals(1.0f, rates.artifacts);
        assertEquals(1.0f, rates.weapons);
        assertEquals(1.0f, rates.expBooks);
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
