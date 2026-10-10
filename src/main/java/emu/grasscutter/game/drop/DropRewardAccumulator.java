package emu.grasscutter.game.drop;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import java.util.List;
import java.util.function.IntFunction;

/** Keeps independently rolled equipment separate while combining stackable drop items. */
final class DropRewardAccumulator {
    private DropRewardAccumulator() {}

    static void add(
            List<GameItem> items,
            int itemId,
            int amount,
            ItemType type,
            IntFunction<GameItem> newItem) {
        if (amount <= 0 || type == null) {
            throw new IllegalArgumentException("Invalid terminal drop " + itemId + ": " + amount);
        }
        if (type == ItemType.ITEM_WEAPON || type == ItemType.ITEM_RELIQUARY) {
            for (int i = 0; i < amount; i++) {
                GameItem copy = newItem.apply(1);
                if (copy == null || copy.getItemId() != itemId || copy.getCount() != 1) {
                    throw new IllegalArgumentException("Invalid equipment drop " + itemId);
                }
                items.add(copy);
            }
            return;
        }
        for (GameItem existing : items) {
            if (existing.getItemId() == itemId) {
                existing.setCount(Math.addExact(existing.getCount(), amount));
                return;
            }
        }
        GameItem item = newItem.apply(amount);
        // GameItem's constructor clips material counts to stackLimit. Reject truncation,
        // because a charged domain claim must never quietly lose part of a reward.
        if (item == null || item.getItemId() != itemId || item.getCount() != amount) {
            throw new IllegalArgumentException("Truncated terminal drop " + itemId);
        }
        items.add(item);
    }
}
