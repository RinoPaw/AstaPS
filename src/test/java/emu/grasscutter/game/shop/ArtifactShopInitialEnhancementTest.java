package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public final class ArtifactShopInitialEnhancementTest {
    @Test
    public void worldLevelRangesMatchDesign() {
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
            assertArrayEquals(
                    expected[worldLevel], ArtifactShop.initialEnhancementRange(worldLevel));
        }
    }

    @Test
    public void worldLevelRangeClampsOutsideSupportedLevels() {
        assertArrayEquals(new int[] {0, 0}, ArtifactShop.initialEnhancementRange(-1));
        assertArrayEquals(new int[] {15, 18}, ArtifactShop.initialEnhancementRange(10));
    }
}
