package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardConfigurationTest {
    @Test
    void previousChestPresetsCannotBeMistakenForTheNewSchema() {
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfiguration.rejectLegacyChestTemplates(
                        "fallback_common: []", ""));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfiguration.rejectLegacyChestTemplates(
                        "", "chest { --loot: native_51010000; }"));
        assertDoesNotThrow(() -> RewardConfiguration.rejectLegacyChestTemplates(
                "common_chest: []",
                "chest[kind=\"common\"] { --loot: common_chest; }"));
    }
}
