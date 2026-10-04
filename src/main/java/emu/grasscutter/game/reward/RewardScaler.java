package emu.grasscutter.game.reward;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.GameItem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Applies source and resource reward-rate layers before rewards enter Inventory. */
public final class RewardScaler {
    public static final int ADVENTURE_EXP_ITEM_ID = 102;
    public static final int MORA_ITEM_ID = 202;

    private RewardScaler() {}

    public static int scaleCount(int itemId, int baseCount, double sourceRate) {
        if (baseCount <= 0) return 0;
        try {
            return scaleCount(baseCount, sourceRate, resourceRate(itemId));
        } catch (RuntimeException exception) {
            logFailure("count calculation", exception);
            return baseCount;
        }
    }

    static int scaleCount(int baseCount, double sourceRate, double resourceRate) {
        if (baseCount <= 0) return 0;
        double source = sanitizeRate(sourceRate);
        double resource = sanitizeRate(resourceRate);
        double scaled = baseCount * source * resource;
        if (!Double.isFinite(scaled) || scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(scaled);
    }

    /**
     * Scales a reward batch in place. The operation is transactional for ordinary Java failures:
     * counts and list contents are restored when the scaled result cannot be applied.
     */
    public static void scaleItems(List<GameItem> items, double sourceRate) {
        if (items == null) return;

        List<GameItem> originalItems;
        IdentityHashMap<GameItem, Integer> originalCounts = new IdentityHashMap<>();
        int[] scaledCounts;
        boolean needsRemoval = false;

        try {
            if (items.isEmpty()) return;

            originalItems = new ArrayList<>(items);
            scaledCounts = new int[originalItems.size()];
            for (int i = 0; i < originalItems.size(); i++) {
                GameItem item = originalItems.get(i);
                if (item == null) {
                    needsRemoval = true;
                    continue;
                }

                int originalCount = item.getCount();
                originalCounts.putIfAbsent(item, originalCount);
                int scaledCount = scaleCount(item.getItemId(), originalCount, sourceRate);
                scaledCounts[i] = scaledCount;
                if (scaledCount <= 0) {
                    needsRemoval = true;
                }
            }
        } catch (RuntimeException exception) {
            logFailure("batch calculation", exception);
            return;
        }

        try {
            for (int i = 0; i < originalItems.size(); i++) {
                GameItem item = originalItems.get(i);
                if (item != null) {
                    item.setCount(scaledCounts[i]);
                }
            }

            if (needsRemoval) {
                items.removeIf(item -> item == null || item.getCount() <= 0);
            }
        } catch (RuntimeException exception) {
            restoreCounts(originalCounts, exception);
            restoreItems(items, originalItems, exception);
            logFailure("batch application", exception);
        }
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

    private static double resourceRate(int itemId) {
        if (GAME == null || GAME.rewards == null) return 1.0;
        return switch (itemId) {
            case ADVENTURE_EXP_ITEM_ID -> GAME.rewards.adventureExp;
            case MORA_ITEM_ID -> GAME.rewards.mora;
            default -> 1.0;
        };
    }

    private static double sanitizeRate(double rate) {
        if (Double.isNaN(rate)) return 0.0;
        if (rate == Double.POSITIVE_INFINITY) return Double.MAX_VALUE;
        if (rate == Double.NEGATIVE_INFINITY) return 0.0;
        return Math.max(0.0, rate);
    }

    private static void logFailure(String stage, RuntimeException exception) {
        try {
            Grasscutter.getLogger()
                    .error(
                            "Reward scaling failed during {}. Using original rewards for this operation.",
                            stage,
                            exception);
        } catch (RuntimeException ignored) {
            // Reward isolation must not depend on logging being available.
        }
    }
}
