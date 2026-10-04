package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public final class ArtifactSettingsTest {
    @Test
    public void defaultResinCurveMatchesDesign() {
        var settings = new ArtifactSettings();
        settings.normalize();

        assertEquals(0, settings.shop.resinCost(21, 2));
        assertEquals(3, settings.shop.resinCost(22, 2));
        assertEquals(6, settings.shop.resinCost(22, 3));
        assertEquals(20, settings.shop.resinCost(30, 4));
        assertEquals(50, settings.shop.resinCost(40, 5));
        assertEquals(20, settings.shop.resinCost(45, 5));
        assertEquals(0, settings.shop.resinCost(45, 6));
    }

    @Test
    public void defaultWorldLevelEnhancementRangesMatchDesign() {
        var settings = new ArtifactSettings();
        settings.normalize();
        int[][] expected = {
            {0, 0},
            {1, 4},
            {3, 6},
            {5, 8},
            {7, 10},
            {9, 12},
            {11, 14},
            {13, 16},
            {14, 17},
            {15, 18}
        };

        for (int worldLevel = 0; worldLevel < expected.length; worldLevel++) {
            var range = settings.shop.initialEnhancementRange(worldLevel);
            assertArrayEquals(expected[worldLevel], new int[] {range.min, range.max});
        }

        var below = settings.shop.initialEnhancementRange(-1);
        var above = settings.shop.initialEnhancementRange(10);
        assertArrayEquals(new int[] {0, 0}, new int[] {below.min, below.max});
        assertArrayEquals(new int[] {15, 18}, new int[] {above.min, above.max});
    }

    @Test
    public void defaultArtifactRollWeightsMatchDesign() {
        var settings = new ArtifactSettings();
        settings.normalize();

        assertEquals(1.60, settings.rolls.critical, 1e-9);
        assertEquals(1.60, settings.rolls.criticalDamage, 1e-9);
        assertEquals(1.20, settings.rolls.energyRecharge, 1e-9);
        assertEquals(1.20, settings.rolls.elementalMastery, 1e-9);
        assertEquals(1.00, settings.rolls.percentStat, 1e-9);
        assertEquals(0.60, settings.rolls.flatStat, 1e-9);
        assertArrayEquals(
                new double[] {0.40, 0.80, 1.20, 1.60}, settings.rolls.valueTiers, 1e-9);
    }
}
