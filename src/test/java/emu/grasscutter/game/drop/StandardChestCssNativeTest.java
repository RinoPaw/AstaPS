package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.reward.ChestRewardConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StandardChestCssNativeTest {
    private static GameItem item(int id) {
        var item = new GameItem();
        item.setItemId(id);
        item.setCount(1);
        return item;
    }

    @Test
    void nativeSigilIdentityOverridesWrongRegionHeuristic() {
        int actual = DropSystem.nativeChestSigilId(
                List.of(item(102), item(305), item(104001)), 306);
        assertEquals(305, actual);
        assertEquals(Map.of(102, 10, 305, 2),
                ChestRewardConfig.resolveRegionalSigils(Map.of(102, 10, -1, 2), actual));
    }

    @Test
    void missingNativeSigilUsesFallbackOnlyForIdentity() {
        assertEquals(304, DropSystem.nativeChestSigilId(
                List.of(item(102), item(104002)), 304));
        assertEquals(0, DropSystem.nativeChestSigilId(List.of(), 0));
    }

    @Test
    void explicitZeroItemSuppressesGenericSigilCount() {
        var resolved = ChestRewardConfig.resolveRegionalSigils(
                Map.of(-1, 8, 305, 0, 201, 0), 305);
        assertEquals(0, resolved.get(305));
        assertEquals(0, resolved.get(201));
        assertFalse(resolved.containsKey(-1));
    }
}
