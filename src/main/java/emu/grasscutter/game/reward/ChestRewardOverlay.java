package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Read-only partition of a rolled native chest reward and operator-owned fixed amounts.
 *
 * <p>Ownership is established by every field named in the resolved TOML rules, including
 * explicit zero clears. The native roll is still needed for unowned, random or special items.
 * This class does not mutate native items, grant loot, or mark a chest as opened.
 */
public final class ChestRewardOverlay {
    private ChestRewardOverlay() {}

    public record Plan(
            List<GameItem> nativeItems, List<ItemParamData> configuredItems) {
        public Plan {
            nativeItems = List.copyOf(nativeItems);
            configuredItems = List.copyOf(configuredItems);
        }

        /**
         * Prepare a single inventory batch, preserving the native item's rolled stats
         * and expanding configured equipment and direct-use cards correctly.
         *
         * <p>This only prepares an in-memory batch. The caller must still decide whether
         * the original ground-drop behavior can safely be replaced, then perform full
         * inventory admission and durable claim coordination. No state is changed here.
         */
        public List<GameItem> inventoryBatch(IntFunction<ItemData> definitions) {
            Objects.requireNonNull(definitions, "definitions");
            var batch = new ArrayList<GameItem>();
            for (GameItem item : nativeItems) {
                if (item.getItemData() == null
                        || item.getItemData().getId() != item.getItemId()) {
                    throw new IllegalArgumentException(
                            "Missing native chest item definition: " + item.getItemId());
                }
                batch.add(item.copyForGrant());
            }
            for (ItemParamData direct : configuredItems) {
                ItemData data = definitions.apply(direct.getId());
                if (data == null || data.getId() != direct.getId()) {
                    throw new IllegalArgumentException(
                            "Missing configured chest item definition: " + direct.getId());
                }
                batch.addAll(InventoryGrantBuilder.create(data, direct.getCount(), 1));
            }
            return List.copyOf(batch);
        }
    }

    /**
     * Keep each unowned native item with its original rolled stats, identity-independent.
     * Native entries whose item IDs are owned by any matching config rule are removed,
     * even when the final configured amount is zero.
     */
    public static Plan plan(ChestRewardConfig.Match match, List<GameItem> nativeRoll) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(nativeRoll, "nativeRoll");

        var nativeItems = new ArrayList<GameItem>();
        for (GameItem item : nativeRoll) {
            if (item == null || item.getItemId() <= 0 || item.getCount() <= 0) {
                throw new IllegalArgumentException("Native chest roll contains an invalid item");
            }
            if (!match.ownedItemIds().contains(item.getItemId())) {
                // A future grant must not reuse an original owner's GUID or mutate a roll
                // belonging to a separate delivery attempt.
                nativeItems.add(item.copyForGrant());
            }
        }
        return new Plan(nativeItems, match.rewards());
    }
}
