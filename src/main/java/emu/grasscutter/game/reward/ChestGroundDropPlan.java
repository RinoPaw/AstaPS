package emu.grasscutter.game.reward;

import emu.grasscutter.data.excels.DropMaterialData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import emu.grasscutter.game.inventory.ItemType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Pure preflight for a standard chest's native pickup semantics.
 *
 * <p>A native fall-to-ground root still auto-picks DropMaterial.autoPick items and
 * non-gadget virtual currency into inventory. All other native materials stay
 * on the ground. Operator-owned fixed rewards always go directly to inventory.
 *
 * <p>No entity ID allocation, scene insertion, inventory write or claim occurs here.
 */
public final class ChestGroundDropPlan {
    private ChestGroundDropPlan() {}

    public record Prepared(List<GameItem> directItems, List<GameItem> groundItems) {
        public Prepared {
            directItems = List.copyOf(directItems);
            groundItems = List.copyOf(groundItems);
        }

        public boolean hasGroundItems() {
            return !groundItems.isEmpty();
        }
    }

    /**
     * Build every item before the caller starts irreversible delivery.
     * Unknown definitions, invalid amounts and items with no usable pickup gadget
     * fail closed instead of generating only the prefix of a loot batch.
     */
    public static Prepared prepare(
            ChestRewardOverlay.Plan overlay,
            boolean nativeFallsToGround,
            IntFunction<ItemData> definitions,
            IntFunction<DropMaterialData> dropMaterials) {
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(dropMaterials, "dropMaterials");

        var direct = new ArrayList<GameItem>();
        var ground = new ArrayList<GameItem>();
        for (GameItem nativeItem : overlay.nativeItems()) {
            if (nativeItem == null || nativeItem.getCount() <= 0 || nativeItem.getItemId() <= 0) {
                throw new IllegalArgumentException("Invalid rolled chest item");
            }
            ItemData data = nativeItem.getItemData();
            if (data == null || data.getId() != nativeItem.getItemId()) {
                throw new IllegalArgumentException(
                        "Missing rolled chest item definition: " + nativeItem.getItemId());
            }
            DropMaterialData drop = dropMaterials.apply(nativeItem.getItemId());
            boolean autoPick = drop != null && drop.isAutoPick();
            boolean virtualAutoPick =
                    data.getItemType() == ItemType.ITEM_VIRTUAL && data.getGadgetId() == 0;
            if (!nativeFallsToGround || autoPick || virtualAutoPick) {
                direct.add(nativeItem.copyForGrant());
            } else {
                if (data.getGadgetId() <= 0) {
                    throw new IllegalArgumentException(
                            "Ground chest item has no pickup gadget: " + nativeItem.getItemId());
                }
                ground.add(nativeItem.copyForGrant());
            }
        }
        for (var configured : overlay.configuredItems()) {
            ItemData data = definitions.apply(configured.getId());
            if (data == null || data.getId() != configured.getId()) {
                throw new IllegalArgumentException(
                        "Missing configured chest item definition: " + configured.getId());
            }
            direct.addAll(InventoryGrantBuilder.create(data, configured.getCount(), 1));
        }
        return new Prepared(direct, ground);
    }
}
