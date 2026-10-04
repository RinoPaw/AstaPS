package emu.grasscutter.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class PluginApiVersionTest {
    @Test
    public void currentPluginApiIsV5() {
        assertEquals(5, PluginManager.API_VERSION);
    }
}
