package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.DropMaterialData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ChestGroundDropPlanTest {
    private static final Gson JSON = new Gson();

    private static ItemData item(int id, String type, int gadgetId) {
        return JSON.fromJson(
                "{\"id\":" + id + ",\"itemType\":\"" + type
                        + "\",\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":99999"
                        + ",\"gadgetId\":" + gadgetId + "}",
                ItemData.class);
    }

    private static GameItem nativeItem(int id, String type, int gadgetId, int count) {
        return new GameItem(item(id, type, gadgetId), count);
    }

    private static DropMaterialData autoPick(int id) {
        return JSON.fromJson("{\"id\":" + id + ",\"autoPick\":true}", DropMaterialData.class);
    }

    private static ChestRewardOverlay.Plan overlay() {
        var ground = nativeItem(104001, "ITEM_MATERIAL", 70310011, 3);
        ground.setLevel(20);
        return new ChestRewardOverlay.Plan(
                List.of(ground,
                        nativeItem(202, "ITEM_VIRTUAL", 0, 800),
                        nativeItem(104002, "ITEM_MATERIAL", 70310012, 2)),
                List.of(new ItemParamData(201, 2), new ItemParamData(102, 20)));
    }

    @Test
    void nativeGroundKeepsPhysicalPickupsAndAutopicksVirtualAndExplicitDrops() {
        var staged = ChestGroundDropPlan.prepare(
                overlay(), true,
                id -> item(id, "ITEM_VIRTUAL", 0),
                id -> id == 104002 ? autoPick(id) : null);

        assertEquals(List.of(202, 104002, 201, 102),
                staged.directItems().stream().map(GameItem::getItemId).toList());
        assertEquals(List.of(104001),
                staged.groundItems().stream().map(GameItem::getItemId).toList());
        assertEquals(20, staged.groundItems().getFirst().getLevel());
        assertEquals(3, staged.groundItems().getFirst().getCount());
        assertTrue(staged.hasGroundItems());
    }

    @Test
    void nativeNonGroundRootNeverSpawnsPickupEntities() {
        var staged = ChestGroundDropPlan.prepare(
                overlay(), false,
                id -> item(id, "ITEM_VIRTUAL", 0),
                id -> null);
        assertTrue(staged.groundItems().isEmpty());
        assertFalse(staged.hasGroundItems());
        assertEquals(List.of(104001, 202, 104002, 201, 102),
                staged.directItems().stream().map(GameItem::getItemId).toList());
    }

    @Test
    void unknownGroundPickupGadgetFailsBeforeAnyDelivery() {
        var proposal = new ChestRewardOverlay.Plan(
                List.of(nativeItem(104001, "ITEM_MATERIAL", 70310011, 2),
                        nativeItem(104003, "ITEM_MATERIAL", 0, 1)),
                List.of(new ItemParamData(201, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> ChestGroundDropPlan.prepare(
                        proposal, true, id -> item(id, "ITEM_VIRTUAL", 0), id -> null));
        assertEquals(2, proposal.nativeItems().size());
    }

    @Test
    void invalidConfiguredDefinitionFailsRatherThanGrantingNativePrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> ChestGroundDropPlan.prepare(overlay(), true, id -> null, id -> null));
        assertThrows(IllegalArgumentException.class,
                () -> ChestGroundDropPlan.prepare(
                        overlay(), true, id -> item(id + 1, "ITEM_VIRTUAL", 0), id -> null));
        assertThrows(NullPointerException.class,
                () -> ChestGroundDropPlan.prepare(null, true, id -> null, id -> null));
    }

    @Test
    void nativeLootIsClonedWithoutMutatingTheRandomRoll() {
        var original = nativeItem(104001, "ITEM_MATERIAL", 70310011, 3);
        original.setLevel(20);
        var plan = new ChestRewardOverlay.Plan(List.of(original), List.of());
        var staged = ChestGroundDropPlan.prepare(plan, true, id -> null, id -> null);
        assertNotSame(original, staged.groundItems().getFirst());
        assertEquals(20, staged.groundItems().getFirst().getLevel());
        assertEquals(3, original.getCount());
    }
}
