package emu.grasscutter.config;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

public final class ShopSettingsTest {
    @Test
    public void defaultArtifactResinCurveMatchesDesign() {
        var settings = new ShopSettings();
        settings.normalize();
        var artifact = settings.artifact;

        assertEquals(0, artifact.resinCost(21, 2));
        assertEquals(3, artifact.resinCost(22, 2));
        assertEquals(6, artifact.resinCost(22, 3));
        assertEquals(20, artifact.resinCost(30, 4));
        assertEquals(50, artifact.resinCost(40, 5));
        assertEquals(20, artifact.resinCost(45, 5));
        assertEquals(0, artifact.resinCost(45, 6));
    }

    @Test
    public void defaultArtifactWorldLevelEnhancementRangesMatchDesign() {
        var settings = new ShopSettings();
        settings.normalize();
        var artifact = settings.artifact;
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
            var range = artifact.initialEnhancementRange(worldLevel);
            assertArrayEquals(expected[worldLevel], new int[] {range.min, range.max});
        }

        var below = artifact.initialEnhancementRange(-1);
        var above = artifact.initialEnhancementRange(10);
        assertArrayEquals(new int[] {0, 0}, new int[] {below.min, below.max});
        assertArrayEquals(new int[] {15, 18}, new int[] {above.min, above.max});
    }

    @Test
    public void rootShopSettingsAreReadyForMultipleProviders() {
        String json = JsonUtils.encode(new ShopSettings());
        assertTrue(json.contains("\"artifact\""));
        assertTrue(json.contains("\"regionalShops\""));
        assertFalse(json.contains("\"rolls\""));
    }
}
