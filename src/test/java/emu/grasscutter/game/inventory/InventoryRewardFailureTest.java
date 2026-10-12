package emu.grasscutter.game.inventory;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.ItemData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verify callback ordering; preflight is not a rollback or durable transaction. */
final class InventoryRewardFailureTest {
    private static final Gson GSON = new Gson();

    private static GameItem material(int id) {
        ItemData data = GSON.fromJson(
                "{\"id\":" + id + ",\"itemType\":\"ITEM_MATERIAL\","
                        + "\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":999}",
                ItemData.class);
        GameItem item = new GameItem();
        item.setItemId(id);
        item.setItemData(data);
        item.setCount(1);
        return item;
    }

    @Test void failedAuthorizationRunsNeitherConfirmationNorGrant() {
        var events = new ArrayList<String>();
        var bag = new MaterialInventoryTab(10);
        assertThrows(IllegalStateException.class, () -> InventoryRewardAdmission.grantIfAccepted(
                List.of(material(1001)), type -> bag, id -> 0,
                () -> { events.add("authorize"); throw new IllegalStateException("prepare failed"); },
                () -> events.add("confirm"),
                items -> { events.add("grant"); throw new AssertionError("unexpected grant"); }));
        assertEquals(List.of("authorize"), events);
    }

    @Test void failedConfirmationRunsAfterAuthorizationButBeforeGrant() {
        var events = new ArrayList<String>();
        var bag = new MaterialInventoryTab(10);
        assertThrows(IllegalStateException.class, () -> InventoryRewardAdmission.grantIfAccepted(
                List.of(material(1001)), type -> bag, id -> 0,
                () -> { events.add("authorize"); return true; },
                () -> { events.add("confirm"); throw new IllegalStateException("journal failure"); },
                items -> { events.add("grant"); throw new AssertionError("unexpected grant"); }));
        assertEquals(List.of("authorize", "confirm"), events);
    }

    @Test void insertionFailureAfterOneItemHasNoAutomaticRollback() {
        var events = new ArrayList<String>();
        var delivered = new ArrayList<Integer>();
        var bag = new MaterialInventoryTab(10);
        assertThrows(IllegalStateException.class, () -> InventoryRewardAdmission.grantIfAccepted(
                List.of(material(1001), material(1002)), type -> bag, id -> 0,
                () -> { events.add("authorize"); return true; },
                () -> events.add("confirm"),
                items -> {
                    events.add("grant");
                    delivered.add(items.iterator().next().getItemId());
                    throw new IllegalStateException("second item persist failed");
                }));
        assertEquals(List.of("authorize", "confirm", "grant"), events);
        assertEquals(List.of(1001), delivered);
    }
}
