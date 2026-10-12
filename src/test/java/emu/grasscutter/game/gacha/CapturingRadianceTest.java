package emu.grasscutter.game.gacha;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

public class CapturingRadianceTest {
    @Test
    public void lossesAdvanceToGuaranteedRadiance() {
        var info = new PlayerGachaBannerInfo();
        assertEquals(1, info.getCapturingRadianceCounter());
        info.onFiftyFifty(false, false, 3);
        assertEquals(2, info.getCapturingRadianceCounter());
        info.onFiftyFifty(false, false, 3);
        assertEquals(3, info.getCapturingRadianceCounter());
        info.onFiftyFifty(true, true, 3);
        assertEquals(1, info.getCapturingRadianceCounter());
    }

    @Test
    public void migratesPersistedLosses() {
        var info = JsonUtils.decode("{\"consecutiveFeaturedLosses\":2}", PlayerGachaBannerInfo.class);
        assertNotNull(info);
        assertEquals(3, info.getCapturingRadianceCounter());
    }
}
