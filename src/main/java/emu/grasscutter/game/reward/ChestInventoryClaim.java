package emu.grasscutter.game.reward;

import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryAddResult;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Durable duplicate-key boundary for a standard chest that delivers every reward directly
 * to inventory. Ground drop entities are deliberately outside this executor.
 *
 * <p>It prevents blind replay after a partially acknowledged grant, but a COMPLETED receipt
 * does not guarantee asynchronous player/item database writes have reached durable storage.
 */
public final class ChestInventoryClaim {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(ChestInventoryClaim.class);

    private ChestInventoryClaim() {}

    @FunctionalInterface
    public interface Admission {
        InventoryAddResult grant(
                List<GameItem> items, BooleanSupplier authorize, Runnable confirmed);
    }

    /**
     * A chest is identified by an authored scene/group/config, scoped to the host player.
     * Never use a random entity runtime ID for a durable claim.
     */
    public static RewardGrantJournal.Key key(int uid, int scene, int group, int config) {
        if (scene <= 0 || group <= 0 || config <= 0) {
            throw new IllegalArgumentException("Chest claim requires positive source IDs");
        }
        return new RewardGrantJournal.Key(
                RewardGrantJournal.Source.CHEST, uid,
                "scene:" + scene + ":group:" + group + ":config:" + config);
    }

    /**
     * Refusal before authorization leaves no receipt and may be retried. After receipt
     * confirmation any partial/ambiguous failure remains unresolved for manual inspection:
     * do not regenerate or replay the random roll on another interaction.
     *
     * @return true only when the inventory explicitly reports full delivery. Receipt
     *         completion failure after that point does not undo delivered items.
     */
    public static boolean deliver(
            RewardGrantJournal journal,
            RewardGrantJournal.Key key,
            List<GameItem> batch,
            Admission admission) {
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(admission, "admission");
        if (key.source() != RewardGrantJournal.Source.CHEST
                || batch == null || batch.isEmpty()) {
            throw new IllegalArgumentException("Invalid chest inventory grant");
        }
        var intended = batch.stream().map(item -> {
            if (item == null || item.getItemId() <= 0 || item.getCount() <= 0) {
                throw new IllegalArgumentException("Invalid chest inventory item");
            }
            return new RewardGrantJournal.Reward(
                    item.getItemId(), item.getCount(), Math.max(0, item.getLevel()));
        }).toList();

        AtomicBoolean confirmed = new AtomicBoolean(false);
        InventoryAddResult outcome = admission.grant(
                List.copyOf(batch),
                () -> journal.prepare(key, intended),
                () -> {
                    journal.confirm(key);
                    confirmed.set(true);
                });
        if (outcome == null || !outcome.allAccepted()) {
            return false;
        }
        if (!confirmed.get()) {
            throw new IllegalStateException("Inventory grant did not confirm chest receipt");
        }
        try {
            journal.complete(key);
        } catch (RuntimeException writeFailure) {
            // Delivery is already acknowledged: do not create a second grant attempt.
            // The durable CONFIRMED receipt is left for source-specific reconciliation.
            LOGGER.warn(
                    "Chest rewards delivered but completion receipt unresolved key={}",
                    key.storageId(), writeFailure);
        }
        return true;
    }
}
