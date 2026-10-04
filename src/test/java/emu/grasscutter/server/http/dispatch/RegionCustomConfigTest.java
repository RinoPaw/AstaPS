package emu.grasscutter.server.http.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import emu.grasscutter.GameConstants;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guards the semantic contents of cur_region's regionCustomConfig. */
public final class RegionCustomConfigTest {
    @Test
    @DisplayName("region custom config keeps the client-facing fields")
    public void keepsClientFacingFields() throws Exception {
        Method method = RegionHandler.class.getDeclaredMethod("buildRegionCustomConfig");
        method.setAccessible(true);
        JsonObject config = (JsonObject) method.invoke(null);

        assertEquals("2", config.get("sdkenv").getAsString());
        assertEquals("false", config.get("checkdevice").getAsString());
        assertEquals("false", config.get("loadPatch").getAsString());
        assertEquals(String.valueOf(GameConstants.DEBUG), config.get("showexception").getAsString());
        assertEquals("pm", config.get("regionConfig").getAsString());
        assertEquals("0", config.get("downloadMode").getAsString());
        assertEquals(4334, config.getAsJsonArray("codeSwitch").get(0).getAsInt());
        assertEquals(3, config.getAsJsonArray("coverSwitch").size());
        assertEquals(40, config.getAsJsonArray("coverSwitch").get(0).getAsInt());
        assertEquals(41, config.getAsJsonArray("coverSwitch").get(1).getAsInt());
        assertEquals(42, config.getAsJsonArray("coverSwitch").get(2).getAsInt());
    }
}
