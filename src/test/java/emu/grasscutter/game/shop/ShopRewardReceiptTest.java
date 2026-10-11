package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.inventory.GameItem;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ShopRewardReceiptTest {
    private static final Gson GSON = new Gson();

    @Test
    void multipliesPaymentAndKeepsRepeatedCostsForInspection() {
        var price = ShopRewardReceipt.snapshotDebits(
                List.of(new ItemParamData(202, 3), new ItemParamData(202, 5),
                        new ItemParamData(201, 0)), 2);
        assertEquals(List.of(new RewardGrantJournal.Debit(202, 6),
                new RewardGrantJournal.Debit(202, 10)), price);
        assertThrows(UnsupportedOperationException.class, () -> price.clear());
    }

    @Test
    void rejectsNegativePricesAndMultiplicationOverflowBeforePayment() {
        assertThrows(IllegalArgumentException.class, () ->
                ShopRewardReceipt.snapshotDebits(List.of(new ItemParamData(202, -1)), 1));
        assertThrows(IllegalArgumentException.class, () ->
                ShopRewardReceipt.snapshotDebits(List.of(new ItemParamData(0, 1)), 1));
        assertThrows(IllegalArgumentException.class, () ->
                ShopRewardReceipt.snapshotDebits(List.of(new ItemParamData(202, 1)), 0));
        assertThrows(ArithmeticException.class, () ->
                ShopRewardReceipt.snapshotDebits(
                        List.of(new ItemParamData(202, Integer.MAX_VALUE)), 2));
    }

    @Test
    void eachPurchaseAttemptGetsAnIndependentAuditIdentity() {
        ItemData data = GSON.fromJson(
                "{\"id\":2001,\"itemType\":\"ITEM_MATERIAL\","
                        + "\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":100}",
                ItemData.class);
        var item = new GameItem(data, 1);
        // A journal does not hit MongoDB until prepare() is called.
        var journal = new RewardGrantJournal();
        var a = new ShopRewardReceipt(journal, 10001, 1, 1001,
                List.of(item), List.of(new ItemParamData(202, 100)), 1);
        var b = new ShopRewardReceipt(journal, 10001, 1, 1001,
                List.of(item), List.of(new ItemParamData(202, 100)), 1);
        assertNotEquals(a.id(), b.id());
        assertTrue(a.id().startsWith("shop:10001:1.1001."));
    }
}
