package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.RewardGrantJournal.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.reward.ChestInventoryClaim;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ChestInventoryClaimTest {
    private static final class Store implements RewardGrantJournal.Store {
        final Map<Key, Receipt> receipts = new HashMap<>();
        boolean failCompletion;

        @Override
        public boolean insert(Receipt receipt) {
            return receipts.putIfAbsent(receipt.key(), receipt) == null;
        }

        @Override
        public boolean transition(Key key, Stage from, Stage to) {
            if (to == Stage.COMPLETED && failCompletion) {
                throw new IllegalStateException("receipt completion unavailable");
            }
            Receipt old = receipts.get(key);
            if (old == null || old.stage() != from) return false;
            receipts.put(key, new Receipt(key, to, old.rewards(), old.debits()));
            return true;
        }

        @Override
        public List<Receipt> unresolved() {
            return receipts.values().stream()
                    .filter(it -> it.stage() == Stage.PREPARED || it.stage() == Stage.CONFIRMED)
                    .toList();
        }
    }

    private static final Key KEY = ChestInventoryClaim.key(10001, 3, 131110001, 1001);

    private static List<GameItem> reward() {
        var item = new GameItem();
        item.setItemId(201);
        item.setCount(2);
        return List.of(item);
    }

    private static InventoryAddResult accepted(List<GameItem> batch) {
        return new InventoryAddResult(batch.stream()
                .map(item -> new InventoryAddResult.Entry(
                        item.getItemId(), item.getCount(), item.getCount(),
                        InventoryAddResult.Status.ADDED))
                .toList());
    }

    @Test
    void successfulGrantClosesReceiptAndRejectsTheSameChestTwice() {
        var store = new Store();
        var journal = new RewardGrantJournal(store);
        AtomicInteger actualDeliveries = new AtomicInteger();
        ChestInventoryClaim.Admission grant = (items, authorize, confirmed) -> {
            if (!authorize.getAsBoolean()) {
                return InventoryAddResult.rejected(
                        items, InventoryAddResult.Status.AUTHORIZATION_FAILED);
            }
            confirmed.run();
            actualDeliveries.incrementAndGet();
            return accepted(items);
        };

        assertTrue(ChestInventoryClaim.deliver(journal, KEY, reward(), grant));
        assertEquals(Stage.COMPLETED, store.receipts.get(KEY).stage());
        assertEquals(List.of(new Reward(201, 2, 0)), store.receipts.get(KEY).rewards());
        assertFalse(ChestInventoryClaim.deliver(journal, KEY, reward(), grant));
        assertEquals(1, actualDeliveries.get());
    }

    @Test
    void inventoryPreflightRejectionCreatesNoReceiptAndCanBeRetried() {
        var store = new Store();
        var journal = new RewardGrantJournal(store);
        assertFalse(ChestInventoryClaim.deliver(journal, KEY, reward(), (items, a, c) ->
                InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL)));
        assertTrue(store.receipts.isEmpty());
        assertTrue(ChestInventoryClaim.deliver(journal, KEY, reward(), (items, a, c) -> {
            assertTrue(a.getAsBoolean());
            c.run();
            return accepted(items);
        }));
    }

    @Test
    void acknowledgedPartialDeliveryStaysUnresolvedAndBlocksReplay() {
        var store = new Store();
        var journal = new RewardGrantJournal(store);
        assertFalse(ChestInventoryClaim.deliver(journal, KEY, reward(), (items, a, c) -> {
            assertTrue(a.getAsBoolean());
            c.run();
            return InventoryAddResult.rejected(
                    items, InventoryAddResult.Status.EFFECT_FAILED);
        }));
        assertEquals(Stage.CONFIRMED, store.receipts.get(KEY).stage());
        assertFalse(ChestInventoryClaim.deliver(journal, KEY, reward(), (items, a, c) -> {
            assertFalse(a.getAsBoolean());
            return InventoryAddResult.rejected(
                    items, InventoryAddResult.Status.AUTHORIZATION_FAILED);
        }));
    }

    @Test
    void acknowledgedDeliveryRemainsSuccessWhenReceiptCloseFails() {
        var store = new Store();
        store.failCompletion = true;
        var journal = new RewardGrantJournal(store);
        assertTrue(ChestInventoryClaim.deliver(journal, KEY, reward(), (items, a, c) -> {
            assertTrue(a.getAsBoolean());
            c.run();
            return accepted(items);
        }));
        assertEquals(Stage.CONFIRMED, store.receipts.get(KEY).stage());
        assertFalse(journal.prepare(KEY, List.of(new Reward(201, 2, 0))));
    }

    @Test
    void durableIdentityRejectsInvalidOrUngroupedChests() {
        assertEquals("chest:10001:scene:3:group:131110001:config:1001", KEY.storageId());
        assertThrows(IllegalArgumentException.class, () ->
                ChestInventoryClaim.key(10001, 3, 0, 1001));
        assertThrows(IllegalArgumentException.class, () ->
                ChestInventoryClaim.key(10001, 3, 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                ChestInventoryClaim.key(10001, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                ChestInventoryClaim.deliver(
                        new RewardGrantJournal(new Store()), KEY, List.of(), (i, a, c) -> accepted(i)));
    }

    @Test
    void missingConfirmationCannotBeReportedAsDelivered() {
        var store = new Store();
        var journal = new RewardGrantJournal(store);
        assertThrows(IllegalStateException.class, () ->
                ChestInventoryClaim.deliver(
                        journal, KEY, reward(), (items, a, c) -> {
                            assertTrue(a.getAsBoolean());
                            return accepted(items);
                        }));
        assertEquals(Stage.PREPARED, store.receipts.get(KEY).stage());
    }
}
