package emu.grasscutter.game.reward;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.GameConfig.ChestRates;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Applies chest-specific multipliers after the authoritative drop table has produced its items. */
public final class ChestRewardScaler {
    private static final int PRIMOGEM_ITEM_ID = 201;
    private static final int MORA_ITEM_ID = 202;
    private static final int WANDERERS_ADVICE_ITEM_ID = 104001;
    private static final int ADVENTURERS_EXPERIENCE_ITEM_ID = 104002;
    private static final int HEROS_WIT_ITEM_ID = 104003;
    private static final int MAX_EQUIPMENT_COPIES_PER_ENTRY = 1024;

    private ChestRewardScaler() {}

    /**
     * Scales one resolved chest reward batch in place. The original DropTable result remains the
     * authority: this method only changes quantities of items that were already produced.
     *
     * <p>Weapons and artifacts are expanded into separate {@link GameItem} instances so each copy
     * remains a real inventory equipment item instead of an invalid stacked equipment entry.
     */
    public static void scaleItems(List<GameItem> items) {
        if (items == null || items.isEmpty()) return;

        List<GameItem> originalItems = new ArrayList<>(items);
        IdentityHashMap<GameItem, Integer> originalCounts = new IdentityHashMap<>();
        List<GameItem> scaledItems = new ArrayList<>();

        try {
            for (GameItem item : originalItems) {
                if (item == null) continue;

                originalCounts.putIfAbsent(item, item.getCount());
                int scaledCount =
                        RewardScaler.scaleCount(
                                item.getItemId(), item.getCount(), chestRate(item));
                if (scaledCount <= 0) continue;

                if (isEquipment(item)) {
                    if (scaledCount > MAX_EQUIPMENT_COPIES_PER_ENTRY) {
                        throw new IllegalArgumentException(
                                "Chest equipment multiplier would create "
                                        + scaledCount
                                        + " copies of item "
                                        + item.getItemId());
                    }

                    item.setCount(1);
                    scaledItems.add(item);
                    for (int i = 1; i < scaledCount; i++) {
                        scaledItems.add(new GameItem(item.getItemData(), 1));
                    }
                } else {
                    item.setCount(scaledCount);
                    scaledItems.add(item);
                }
            }

            items.clear();
            items.addAll(scaledItems);
        } catch (RuntimeException exception) {
            restoreCounts(originalCounts, exception);
            restoreItems(items, originalItems, exception);
            try {
                Grasscutter.getLogger()
                        .error(
                                "Chest reward scaling failed. Using original chest rewards for this operation.",
                                exception);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static double chestRate(GameItem item) {
        ChestRates rates =
                GAME != null && GAME.rewards != null ? GAME.rewards.chests : null;
        if (rates == null || item == null) return 1.0;

        return switch (item.getItemId()) {
            case PRIMOGEM_ITEM_ID -> rates.primogems;
            case MORA_ITEM_ID -> rates.mora;
            case WANDERERS_ADVICE_ITEM_ID,
                    ADVENTURERS_EXPERIENCE_ITEM_ID,
                    HEROS_WIT_ITEM_ID -> rates.expBooks;
            default -> {
                if (item.getItemData() == null) yield 1.0;
                yield switch (item.getItemData().getItemType()) {
                    case ITEM_RELIQUARY -> rates.artifacts;
                    case ITEM_WEAPON -> rates.weapons;
                    default -> 1.0;
                };
            }
        };
    }

    private static boolean isEquipment(GameItem item) {
        if (item == null || item.getItemData() == null) return false;
        ItemType type = item.getItemData().getItemType();
        return type == ItemType.ITEM_RELIQUARY || type == ItemType.ITEM_WEAPON;
    }

    private static void restoreCounts(
            IdentityHashMap<GameItem, Integer> originalCounts, RuntimeException failure) {
        originalCounts.forEach(
                (item, count) -> {
                    try {
                        item.setCount(count);
                    } catch (RuntimeException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                });
    }

    private static void restoreItems(
            List<GameItem> items, List<GameItem> originalItems, RuntimeException failure) {
        try {
            items.clear();
            items.addAll(originalItems);
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
