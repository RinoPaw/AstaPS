package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class DropRewardAccumulatorTest {
    private static GameItem item(int id, int count) {
        var item = new GameItem();
        item.setItemId(id);
        item.setCount(count);
        return item;
    }

    @Test
    void eachReliquaryKeepsIndependentIdentity() {
        var items = new ArrayList<GameItem>();
        DropRewardAccumulator.add(
                items, 35611, 3, ItemType.ITEM_RELIQUARY, count -> item(35611, count));
        assertEquals(3, items.size());
        assertNotSame(items.get(0), items.get(1));
        assertNotSame(items.get(1), items.get(2));
        for (GameItem relic : items) {
            assertEquals(1, relic.getCount());
        }
    }

    @Test
    void ordinaryStackableItemsStillCombine() {
        var items = new ArrayList<GameItem>();
        DropRewardAccumulator.add(
                items, 202, 100, ItemType.ITEM_VIRTUAL, count -> item(202, count));
        DropRewardAccumulator.add(
                items, 202, 50, ItemType.ITEM_VIRTUAL, count -> item(202, count));
        assertEquals(1, items.size());
        assertEquals(150, items.get(0).getCount());
    }

    @Test
    void rejectsSilentlyTruncatedMaterialRoll() {
        var items = new ArrayList<GameItem>();
        assertThrows(
                IllegalArgumentException.class,
                () -> DropRewardAccumulator.add(
                        items, 104301, 12, ItemType.ITEM_MATERIAL, count -> item(104301, 10)));
        assertEquals(0, items.size());
    }
}
