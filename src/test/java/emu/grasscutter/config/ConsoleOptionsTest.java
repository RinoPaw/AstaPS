package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter.ServerRunMode;
import org.junit.jupiter.api.Test;

public final class ConsoleOptionsTest {
    @Test
    void defaultsDependOnServerMode() {
        var options = new ConfigContainer.ConsoleOptions();
        assertTrue(options.isEnabled(ServerRunMode.HYBRID));
        assertTrue(options.isEnabled(ServerRunMode.GAME_ONLY));
        assertFalse(options.isEnabled(ServerRunMode.DISPATCH_ONLY));
    }

    @Test
    void explicitConfigurationOverridesEveryMode() {
        var options = new ConfigContainer.ConsoleOptions();
        options.enabled = true;
        for (var mode : ServerRunMode.values()) assertTrue(options.isEnabled(mode));

        options.enabled = false;
        for (var mode : ServerRunMode.values()) assertFalse(options.isEnabled(mode));
    }
}
