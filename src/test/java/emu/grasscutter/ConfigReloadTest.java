package emu.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.utils.JsonUtils;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ConfigReloadTest {
    private ConfigContainer original;
    private JsonElement originalSnapshot;

    @BeforeEach
    void setUp() {
        original = Grasscutter.config;
        originalSnapshot = Grasscutter.loadedConfigSnapshot;
        Grasscutter.config = cloneOf(original);
    }

    @AfterEach
    void restore() {
        Grasscutter.config = original;
        Grasscutter.loadedConfigSnapshot = originalSnapshot;
    }

    private static ConfigContainer cloneOf(ConfigContainer config) {
        return JsonUtils.decode(JsonUtils.toJson(config), ConfigContainer.class);
    }

    @Test
    void appliesOnlyLiveLoggingSettingsWithoutReplacingConfig() {
        var current = Grasscutter.config;
        var next = cloneOf(current);
        next.server.logCommands = !current.server.logCommands;
        next.server.game.isShowPacketPayload = !current.server.game.isShowPacketPayload;
        next.server.debugWhitelist = Set.of(1234, 5678);

        Grasscutter.applyConfigReload(next);

        assertSame(current, Grasscutter.getConfig());
        assertEquals(next.server.logCommands, current.server.logCommands);
        assertEquals(next.server.game.isShowPacketPayload, current.server.game.isShowPacketPayload);
        assertEquals(next.server.debugWhitelist, current.server.debugWhitelist);
    }

    @Test
    void ignoresRuntimeOnlyOverridesWhenComparingDiskChanges() {
        var current = Grasscutter.config;
        var unchangedFile = cloneOf(current);
        current.server.http.encryption.useEncryption =
                !current.server.http.encryption.useEncryption;
        boolean override = current.server.http.encryption.useEncryption;

        assertDoesNotThrow(() -> Grasscutter.applyConfigReload(unchangedFile));
        assertEquals(override, current.server.http.encryption.useEncryption);
    }

    @Test
    void refusesInfrastructureChangeAndAppliesNothing() {
        var current = Grasscutter.config;
        var next = cloneOf(current);
        next.server.http.bindPort++;
        next.server.logCommands = !current.server.logCommands;
        boolean before = current.server.logCommands;

        var error = assertThrows(IllegalArgumentException.class,
                () -> Grasscutter.applyConfigReload(next));

        assertTrue(error.getMessage().contains("server.http.bindPort"));
        assertSame(current, Grasscutter.getConfig());
        assertEquals(before, current.server.logCommands);
    }

    @Test
    void refusesMalformedConfigWithoutChangingActiveState() {
        var current = Grasscutter.config;
        var invalid = cloneOf(current);
        invalid.server = null;

        assertThrows(IllegalArgumentException.class,
                () -> Grasscutter.applyConfigReload(invalid));
        assertSame(current, Grasscutter.getConfig());
    }
}
