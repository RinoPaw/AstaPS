package emu.grasscutter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.game.city.CityInfoData;
import org.junit.jupiter.api.Test;

class RuntimeCompatibilityTest {
    @Test
    void kcpHashingDependencyIsAvailableAtRuntime() {
        assertDoesNotThrow(() -> Class.forName("net.openhft.hashing.LongHashFunction"));
    }

    @Test
    void cityInfoHasMorphiaCompatibleDefaultConstruction() {
        var cityInfo = new CityInfoData();
        assertEquals(0, cityInfo.getCityId());
        assertEquals(1, cityInfo.getLevel());
        assertEquals(0, cityInfo.getNumCrystal());
    }
}
