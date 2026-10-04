package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class ArtifactSettingsTest {
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
