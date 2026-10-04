package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

public final class GameConfigRewardSchemaTest {
    @Test
    public void chestTierPresenceControlsReplacement() {
        var rewards = new GameConfig.Rewards();

        assertNull(rewards.chests.common);
        assertNull(rewards.chests.exquisite);
        assertNull(rewards.chests.precious);
        assertNull(rewards.chests.luxurious);
        assertNull(rewards.chest("COMMON"));

        var replacement = new GameConfig.ChestReward();
        rewards.chests.common = replacement;
        assertSame(replacement, rewards.chest("COMMON"));
    }

    @Test
    public void rewardJsonUsesOnlyCanonicalChestAndLeyLineFields() {
        String chest = JsonUtils.encode(new GameConfig.ChestReward());
        String leyLines = JsonUtils.encode(new GameConfig.LeyLineRates());

        assertFalse(chest.contains("enabled"));
        assertTrue(leyLines.contains("wealth"));
        assertTrue(leyLines.contains("revelation"));
        assertFalse(leyLines.contains("global"));
        assertFalse(leyLines.contains("experienceBooks"));
        assertFalse(leyLines.contains("\"exp\""));
        assertFalse(leyLines.contains("\"mora\""));
    }

    @Test
    public void leyLineDefaultsPreserveThePreviousEffectiveSourceRate() {
        var rates = new GameConfig.LeyLineRates();
        assertEquals(2.0f, rates.wealth);
        assertEquals(2.0f, rates.revelation);
    }
}
