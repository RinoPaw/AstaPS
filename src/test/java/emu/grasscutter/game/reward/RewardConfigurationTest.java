package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardConfigurationTest {
    @Test
    void historicGeneratedTemplatesCannotSilentlyKeepInflatedRewards() {
        var oldYaml = """
                fallback_common:
                  - {reward: genshin.item.primogem, count: 66}
                fallback_luxurious:
                  - {reward: genshin.item.primogem, count: 566}
                """;
        var oldCss = """
                chest[kind="common"] { --loot: fallback_common; }
                chest[kind="luxurious"] { --loot: fallback_luxurious; }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfiguration.rejectLegacyChestTemplates(oldYaml, ""));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfiguration.rejectLegacyChestTemplates("", oldCss));
        assertDoesNotThrow(() -> RewardConfiguration.rejectLegacyChestTemplates(
                "fallback_common: []", """
                chest[kind="common"] {
                    --loot: fallback_common;
                    --genshin-item-primogem: 2;
                }
                """));
    }
}
