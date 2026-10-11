package emu.grasscutter.game.shop;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.inventory.GameItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One server-side shop purchase attempt, with an immutable reward and debit snapshot.
 * The UUID identifies an attempt for operator inspection. BuyGoodsReq has no verified
 * durable idempotency key, so a new client retry is NOT automatically the same attempt.
 */
public final class ShopRewardReceipt {
    private final RewardGrantJournal journal;
    private final RewardGrantJournal.Key key;
    private final List<RewardGrantJournal.Reward> rewards;
    private final List<RewardGrantJournal.Debit> debits;

    public ShopRewardReceipt(
            RewardGrantJournal journal,
            int uid,
            int shopType,
            int goodsId,
            List<GameItem> items,
            List<ItemParamData> costs,
            int quantity) {
        this.journal = Objects.requireNonNull(journal);
        if (shopType <= 0 || goodsId <= 0 || quantity <= 0
                || items == null || items.isEmpty() || costs == null) {
            throw new IllegalArgumentException("Invalid shop receipt input");
        }
        this.key = new RewardGrantJournal.Key(
                RewardGrantJournal.Source.SHOP, uid,
                shopType + "." + goodsId + "." + UUID.randomUUID());
        var rewardLines = new ArrayList<RewardGrantJournal.Reward>();
        for (GameItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Null purchased item");
            }
            rewardLines.add(new RewardGrantJournal.Reward(
                    item.getItemId(), item.getCount(), item.getLevel()));
        }
        this.rewards = List.copyOf(rewardLines);
        this.debits = snapshotDebits(costs, quantity);
    }

    /** Snapshot repeated price rows exactly as charged; zero-cost entries are omitted. */
    public static List<RewardGrantJournal.Debit> snapshotDebits(
            List<ItemParamData> costs, int quantity) {
        if (costs == null || quantity <= 0) {
            throw new IllegalArgumentException("Invalid shop price");
        }
        var lines = new ArrayList<RewardGrantJournal.Debit>();
        for (ItemParamData cost : costs) {
            if (cost == null || cost.getId() <= 0 || cost.getCount() < 0) {
                throw new IllegalArgumentException("Invalid shop price entry");
            }
            if (cost.getCount() > 0) {
                lines.add(new RewardGrantJournal.Debit(
                        cost.getId(), Math.multiplyExact(cost.getCount(), quantity)));
            }
        }
        return List.copyOf(lines);
    }

    public boolean prepare() {
        return journal.prepare(key, rewards, debits);
    }

    public void confirm() {
        journal.confirm(key);
    }

    /** Only for a clean payment refusal that did not deduct any funds. */
    public void abortUnpaid() {
        journal.abortUnpaid(key);
    }

    public void complete() {
        journal.complete(key);
    }

    public String id() {
        return key.storageId();
    }
}
