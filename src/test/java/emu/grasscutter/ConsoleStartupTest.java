package emu.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.config.ConfigContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ConsoleStartupTest {
    private ConfigContainer originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = Grasscutter.config;
        Grasscutter.config = new ConfigContainer();
        Grasscutter.setRunModeOverride(null);
        Grasscutter.setNoConsole(false);
    }

    @AfterEach
    void restore() {
        Grasscutter.config = originalConfig;
        Grasscutter.setRunModeOverride(null);
        Grasscutter.setNoConsole(false);
    }

    @Test
    void defaultsDependOnServerMode() {
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        assertTrue(Grasscutter.shouldStartConsole());
        Grasscutter.setRunModeOverride(ServerRunMode.GAME_ONLY);
        assertTrue(Grasscutter.shouldStartConsole());
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        assertFalse(Grasscutter.shouldStartConsole());
    }

    @Test
    void explicitSettingOverridesServerMode() {
        for (var mode : ServerRunMode.values()) {
            Grasscutter.setRunModeOverride(mode);
            Grasscutter.config.server.console.enabled = true;
            assertTrue(Grasscutter.shouldStartConsole());
            Grasscutter.config.server.console.enabled = false;
            assertFalse(Grasscutter.shouldStartConsole());
        }
    }

    @Test
    void startupOptionAlwaysDisablesConsole() {
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        Grasscutter.config.server.console.enabled = true;
        Grasscutter.setNoConsole(true);
        assertFalse(Grasscutter.shouldStartConsole());
    }
}
