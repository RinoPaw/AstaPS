package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardConfigurationTest {
    @Test
    void rewardConfigurationReadsOnlyBundledResources() throws Exception {
        assertTrue(RewardConfiguration.readBundled("loot.yaml").contains("minimal:"));
        assertTrue(RewardConfiguration.readBundled("rewards.css").contains("--loot: minimal;"));
    }

    @Test
    void missingBundledResourcesAreReported() {
        assertThrows(java.io.IOException.class, () ->
                RewardConfiguration.readBundled("missing.yaml"));
    }
}
