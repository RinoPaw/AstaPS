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
    @Test
    void nearbyOriginalScenePointAvoidsExtremelyLongFall() {
        var target = new emu.grasscutter.game.world.Position(1180.8507f, 0, -1005.30237f);
        var waypoint = new emu.grasscutter.game.world.Position(1175.1151f, 406.8991f, -1023.0333f);
        float y = MapMarksManager.estimatedAltitude(target, java.util.List.of(waypoint), 2000f);
        assertTrue(y > waypoint.getY());
        assertTrue(y < 500f);
    }

    @Test
    void distantWaypointsCannotInferDestinationHeight() {
        var target = new emu.grasscutter.game.world.Position(0, 0, 0);
        var distant = new emu.grasscutter.game.world.Position(1000, 250, 1000);
        assertEquals(2000f, MapMarksManager.estimatedAltitude(
                target, java.util.List.of(distant), 2000f));
    }

}
