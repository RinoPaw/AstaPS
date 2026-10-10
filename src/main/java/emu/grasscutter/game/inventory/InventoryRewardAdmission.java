package emu.grasscutter.game.inventory;

import emu.grasscutter.data.excels.ItemData;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Projects a whole reward batch against the current inventory without modifying it.
 *
 * <p>Inventory still does the insertion. This guards common no-space and stack-overflow cases
 * before payment; it cannot roll back a database or item-use failure after payment.
 */
final class InventoryRewardAdmission {
    private static final Set<Integer> VIRTUAL_REWARDS =
            Set.of(101, 102, 105, 106, 107, 121, 201, 202, 203, 204);

    private InventoryRewardAdmission() {}

    static boolean canAccept(
            Collection<GameItem> rewards,
            java.util.function.Function<ItemType, InventoryTab> getTab) {
        if (rewards == null || rewards.isEmpty()) {
            return false;
        }
        Map<InventoryTab, Integer> reservedSlots = new IdentityHashMap<>();
        Map<InventoryTab, Map<Integer, Long>> projectedStacks = new IdentityHashMap<>();

        for (GameItem item : rewards) {
            if (item == null || item.getCount() <= 0 || item.getItemId() <= 0) {
                return false;
            }
            ItemData data = item.getItemData();
            if (data == null || data.getId() != item.getItemId()
                    || data.getItemType() == null || data.isUseOnGain()) {
                // useOnGain is a non-inventory side effect whose success cannot be guaranteed.
                return false;
            }
            ItemType type = data.getItemType();
            if (type == ItemType.ITEM_VIRTUAL) {
                if (!VIRTUAL_REWARDS.contains(item.getItemId())) {
                    return false;
                }
                continue;
            }
            // TPS weapons have unique-owned semantics. They are not valid domain prizes.
            if (type == ItemType.ITEM_TPS_WEAPON) {
                return false;
            }
            InventoryTab tab = getTab.apply(type);
            if (tab == null) {
                return false;
            }

            if (type == ItemType.ITEM_WEAPON || type == ItemType.ITEM_RELIQUARY) {
                if (!reserveSlot(tab, reservedSlots)) {
                    return false;
                }
                continue;
            }
            MaterialType materialType = data.getMaterialType();
            if (materialType == null) {
                return false;
            }
            switch (materialType) {
                case MATERIAL_AVATAR, MATERIAL_FLYCLOAK, MATERIAL_COSTUME, MATERIAL_NAMECARD -> {
                    return false;
                }
                default -> {
                    // Material and furniture items share one slot for each distinct item ID.
                }
            }
            long limit = data.getStackLimit();
            if (limit <= 0) {
                return false;
            }
            Map<Integer, Long> stacks =
                    projectedStacks.computeIfAbsent(tab, unused -> new HashMap<>());
            long current = stacks.computeIfAbsent(
                    item.getItemId(), id -> (long) tab.getItemCountById(id));
            long next = current + item.getCount();
            if (next > limit) {
                return false;
            }
            if (current == 0 && tab.getItemById(item.getItemId()) == null
                    && !reserveSlot(tab, reservedSlots)) {
                return false;
            }
            stacks.put(item.getItemId(), next);
        }
        return true;
    }

    private static boolean reserveSlot(
            InventoryTab tab, Map<InventoryTab, Integer> reservedSlots) {
        int next = reservedSlots.getOrDefault(tab, 0) + 1;
        if ((long) tab.getSize() + next > tab.getMaxCapacity()) {
            return false;
        }
        reservedSlots.put(tab, next);
        return true;
    }
}
