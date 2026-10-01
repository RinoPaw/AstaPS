package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class RewardScalerTest {
    @Test
    public void composesSourceAndResourceRatesBeforeRounding() {
        assertEquals(15, RewardScaler.scaleCount(4, 1.5, 2.5));
        assertEquals(2, RewardScaler.scaleCount(1, 1.0, 1.5));
    }

    @Test
    public void clampsInvalidAndNegativeInputs() {
        assertEquals(0, RewardScaler.scaleCount(-5, 2.0, 2.0));
        assertEquals(0, RewardScaler.scaleCount(5, -1.0, 2.0));
        assertEquals(0, RewardScaler.scaleCount(5, 2.0, Double.NaN));
        assertEquals(Integer.MAX_VALUE, RewardScaler.scaleCount(Integer.MAX_VALUE, 2.0, 2.0));
    }
}
