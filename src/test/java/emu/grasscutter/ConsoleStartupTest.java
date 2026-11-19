package emu.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.utils.Utils;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
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
    void gameOnlyNeverStartsHttpEvenWhenConfiguredEarly() {
        Grasscutter.config.server.http.startImmediately = true;
        assertTrue(Grasscutter.shouldStartHttpImmediately(ServerRunMode.HYBRID));
        assertTrue(Grasscutter.shouldStartHttpImmediately(ServerRunMode.DISPATCH_ONLY));
        assertFalse(Grasscutter.shouldStartHttpImmediately(ServerRunMode.GAME_ONLY));
        Grasscutter.config.server.http.startImmediately = false;
        assertFalse(Grasscutter.shouldStartHttpImmediately(ServerRunMode.HYBRID));
    }

    @Test
    void dispatchOnlyStartupDoesNotRequireGameResources(@TempDir Path directory) {
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        Grasscutter.config.folderStructure.data = directory.resolve("data").toString();
        assertDoesNotThrow(Utils::startupCheck);
        assertTrue(Files.isDirectory(directory.resolve("data")));
    }

    @Test
    void startupOptionAlwaysDisablesConsole() {
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);
        Grasscutter.config.server.console.enabled = true;
        Grasscutter.setNoConsole(true);
        assertFalse(Grasscutter.shouldStartConsole());
    }
}
