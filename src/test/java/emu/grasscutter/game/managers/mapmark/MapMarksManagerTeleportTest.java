package emu.grasscutter.game.managers.mapmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapMarksManagerTeleportTest {
    @Test
    void unlabeledFishhookUsesSafeAltitudeInsteadOfLegacyThreeHundred() {
        assertEquals(2000f, MapMarksManager.resolveTeleportHeight("", 2000f));
        assertEquals(2300f, MapMarksManager.resolveTeleportHeight("Fishing", 2300f));
        assertEquals(2000f, MapMarksManager.resolveTeleportHeight("NaN", 2000f));
        assertEquals(2000f, MapMarksManager.resolveTeleportHeight("Infinity", 2000f));
    }

    @Test
    void explicitlyNamedYStillWins() {
        assertEquals(300f, MapMarksManager.resolveTeleportHeight("300", 2000f));
        assertEquals(-100f, MapMarksManager.resolveTeleportHeight(" -100 ", 2000f));
        assertEquals(0f, MapMarksManager.resolveTeleportHeight("0", 2000f));
    }

    @Test
    void defaultAltitudeClearsOriginalThreeHundredMeterFloor() {
        assertTrue(MapMarksManager.safeSceneAltitude(-1, new emu.grasscutter.game.world.Position(0, 0, 0)) >= 2000f);
    }
}