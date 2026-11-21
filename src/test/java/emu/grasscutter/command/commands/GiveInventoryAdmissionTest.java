package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.command.CommandOutputCapture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
final class GiveInventoryAdmissionTest {
    @Test
    void bulkMaterialsKeepEligibleGrantsWhenAnotherStackIsFull() {
        var data = GameData.getItemDataMap();
        var previous = new java.util.HashMap<>(data);
        data.clear();
        try {
            for (int id : List.of(100011, 100012)) {
                data.put(id, new Gson().fromJson(
                        """
                        {"id":%d,"itemType":"ITEM_MATERIAL","materialType":"MATERIAL_NONE","stackLimit":99}
                        """.formatted(id),
                        ItemData.class));
            }
            var player = new Player() {
                @Override
                public Inventory getInventory() {
                    return inventory;
                }

                final RecordingInventory inventory = new RecordingInventory(this);
            };
            var command = new GiveCommand().createCommandLine(null, player);
            var feedback = CommandOutputCapture.capture(
                    () -> assertEquals(0, command.execute("mats", "--amount", "3")));

            assertEquals(List.of(100012), player.inventory.delivered);
            assertEquals(1, feedback.size());
            assertTrue(feedback.getFirst().contains("3"));
        } finally {
            data.clear();
            data.putAll(previous);
        }
    }

    private static final class RecordingInventory extends Inventory {
        final List<Integer> delivered = new ArrayList<>();

        RecordingInventory(Player player) {
            super(player);
        }

        @Override
        public InventoryAddResult addItems(
                Collection<GameItem> items, ActionReason reason, InventoryAddPolicy policy) {
            assertSame(InventoryAddPolicy.BEST_EFFORT, policy);
            var entries = new ArrayList<InventoryAddResult.Entry>();
            for (var item : items) {
                assertEquals(3, item.getCount());
                if (item.getItemId() == 100011) {
                    entries.add(new InventoryAddResult.Entry(
                            item.getItemId(), 3, 0, InventoryAddResult.Status.STACK_LIMIT));
                } else {
                    delivered.add(item.getItemId());
                    entries.add(new InventoryAddResult.Entry(
                            item.getItemId(), 3, 3, InventoryAddResult.Status.ADDED));
                }
            }
            return new InventoryAddResult(entries);
        }
    }
}
