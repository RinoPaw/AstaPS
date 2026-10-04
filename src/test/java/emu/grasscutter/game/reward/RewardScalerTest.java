package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.game.inventory.GameItem;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    public void removesZeroScaledRewardsFromMutableBatch() {
        var item = reward(1, 5);
        var items = new ArrayList<>(List.of(item));

        RewardScaler.scaleItems(items, Double.NaN);

        assertTrue(items.isEmpty());
    }

    @Test
    public void restoresOriginalRewardWhenBatchCannotBeModified() {
        var item = reward(1, 5);
        var items = List.of(item);

        assertDoesNotThrow(() -> RewardScaler.scaleItems(items, Double.NaN));

        assertEquals(5, item.getCount());
        assertEquals(1, items.size());
    }

    private static GameItem reward(int itemId, int count) {
        var item = new GameItem();
        item.setItemId(itemId);
        item.setCount(count);
        return item;
    }
}
