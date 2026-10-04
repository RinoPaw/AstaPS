package emu.grasscutter.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class PluginApiVersionTest {
    @Test
    public void commandApiBreakUsesPluginApiV4() {
        assertEquals(4, PluginManager.API_VERSION);
    }
}
