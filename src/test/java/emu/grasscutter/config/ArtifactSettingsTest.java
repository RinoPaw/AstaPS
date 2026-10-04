package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.utils.JsonUtils;
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

    @Test
    public void artifactSettingsContainNoShopConfiguration() {
        String json = JsonUtils.encode(new ArtifactSettings());
        assertTrue(json.contains("\"rolls\""));
        assertFalse(json.contains("\"shop\""));
        assertFalse(json.contains("\"shops\""));
    }
}
