package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class ScriptLoaderTest {
    @Test
    @DisplayName("surrounding whitespace and CRLF do not affect require parsing")
    public void whitespaceIsIgnored() {
        assertEquals(
                "CommonConfig",
                ScriptLoader.extractRequiredScriptName("   require \"CommonConfig\"   \r"));
    }

    @Test
    @DisplayName("ordinary Lua lines are not parsed as requires")
    public void nonRequireLineIsIgnored() {
        assertNull(
                ScriptLoader.extractRequiredScriptName(
                        "local value = \"CommonConfig\""));
    }

    @Test
    @DisplayName("malformed require lines are left alone")
    public void malformedRequireIsIgnored() {
        assertNull(ScriptLoader.extractRequiredScriptName("require \"CommonConfig"));
        assertNull(ScriptLoader.extractRequiredScriptName("require CommonConfig"));
        assertNull(ScriptLoader.extractRequiredScriptName(null));
    }
}
