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
    public void ordinaryWinsRetainTheCounterState() {
        var info = new PlayerGachaBannerInfo();
        info.onFiftyFifty(true, false, 3);
        assertEquals(0, info.getCapturingRadianceCounter());
        info.onFiftyFifty(true, false, 3);
        assertEquals(1, info.getCapturingRadianceCounter());
        info.setCapturingRadianceCounter(2);
        info.onFiftyFifty(true, false, 3);
        assertEquals(1, info.getCapturingRadianceCounter());
    }

    @Test
    public void migratesPersistedLossesAndKeepsNewCounterAfterSerialization() {
        var info = JsonUtils.decode("{\"consecutiveFeaturedLosses\":2}", PlayerGachaBannerInfo.class);
        assertEquals(3, info.getCapturingRadianceCounter());
        info.onFiftyFifty(true, true, 3);
        var loaded = JsonUtils.decode(JsonUtils.encode(info), PlayerGachaBannerInfo.class);
        assertEquals(1, loaded.getCapturingRadianceCounter());
    }

    @Test
    public void characterBannerUsesCorrectRadianceRates() {
        var banner = JsonUtils.decode("{\"bannerType\":\"CHARACTER\"}", GachaBanner.class);
        banner.onLoad();
        assertEquals(0, banner.getCapturingRadianceChance(1));
        assertEquals(10, banner.getCapturingRadianceChance(2));
        assertEquals(100, banner.getCapturingRadianceChance(3));
        assertEquals(100, banner.getCapturingRadianceChance(9));
    }
}
