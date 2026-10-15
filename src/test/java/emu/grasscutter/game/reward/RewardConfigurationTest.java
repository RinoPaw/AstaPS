package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardConfigurationTest {
    @Test
    void rewardConfigurationReadsOnlyBundledResources() throws Exception {
        assertTrue(RewardConfiguration.readBundled("loot.yaml").contains("common_chest:"));
        assertTrue(RewardConfiguration.readBundled("rewards.css").contains("--loot: common_chest;"));
    }

    @Test
    void missingBundledResourcesAreReported() {
        assertThrows(java.io.IOException.class, () ->
                RewardConfiguration.readBundled("missing.yaml"));
    }
}
