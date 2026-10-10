package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.ItemData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InventoryRewardAdmissionTest {
    private static final Gson GSON = new Gson();

    private static GameItem item(int id, ItemType type, int count, int stackLimit) {
        ItemData data = GSON.fromJson(
                "{\"id\":" + id + ",\"itemType\":\"" + type.name() +
                "\",\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":" + stackLimit + "}",
                ItemData.class);
        var item = new GameItem();
        item.setItemId(id);
        item.setItemData(data);
        item.setCount(count);
        return item;
    }

    @Test
    void rejectsFullReliquaryInventoryBeforePayment() {
        var relics = new EquipInventoryTab(1);
        relics.onAddItem(item(1001, ItemType.ITEM_RELIQUARY, 1, 1));
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(item(1002, ItemType.ITEM_RELIQUARY, 1, 1)),
                type -> type == ItemType.ITEM_RELIQUARY ? relics : null));
    }

    @Test
    void reservesAllEquipmentSlotsInOneRoll() {
        var relics = new EquipInventoryTab(1);
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(
                        item(1001, ItemType.ITEM_RELIQUARY, 1, 1),
                        item(1002, ItemType.ITEM_RELIQUARY, 1, 1)),
                type -> type == ItemType.ITEM_RELIQUARY ? relics : null));
    }

    @Test
    void detectsCombinedStackOverflow() {
        var materials = new MaterialInventoryTab(2);
        materials.onAddItem(item(2001, ItemType.ITEM_MATERIAL, 7, 10));
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(
                        item(2001, ItemType.ITEM_MATERIAL, 2, 10),
                        item(2001, ItemType.ITEM_MATERIAL, 2, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null));
    }

    @Test
    void rejectsMoreNewMaterialIdsThanAvailableSlots() {
        var materials = new MaterialInventoryTab(1);
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(
                        item(2001, ItemType.ITEM_MATERIAL, 1, 10),
                        item(2002, ItemType.ITEM_MATERIAL, 1, 10)),
                type -> type == ItemType.ITEM_MATERIAL ? materials : null));
    }

    @Test
    void permitsExistingMaterialStackInFullTabAndVirtualReward() {
        var materials = new MaterialInventoryTab(1);
        materials.onAddItem(item(2001, ItemType.ITEM_MATERIAL, 3, 10));
        assertTrue(InventoryRewardAdmission.canAccept(
                List.of(
                        item(2001, ItemType.ITEM_MATERIAL, 4, 10),
                        item(202, ItemType.ITEM_VIRTUAL, 100, 1)),
                Map.of(ItemType.ITEM_MATERIAL, (InventoryTab) materials)::get));
    }

    @Test
    void rejectsUnknownVirtualReward() {
        assertFalse(InventoryRewardAdmission.canAccept(
                List.of(item(99999, ItemType.ITEM_VIRTUAL, 1, 1)),
                type -> null));
    }
}
