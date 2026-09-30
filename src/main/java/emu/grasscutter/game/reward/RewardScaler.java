package emu.grasscutter.game.reward;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.game.inventory.GameItem;
import java.util.List;

/**
 * Applies server reward-rate layers before rewards enter Inventory.
 *
 * <p>Inventory deliberately stays rate-agnostic: callers pass the source-specific multiplier here,
 * then this class applies the resource multiplier (currently Adventure EXP / Mora) and rounds once.
 */
public final class RewardScaler {
    public static final int ADVENTURE_EXP_ITEM_ID = 102;
    public static final int MORA_ITEM_ID = 202;

    private RewardScaler() {}

    /** Final count = base count × source rate × resource rate, rounded exactly once. */
    public static int scaleCount(int itemId, int baseCount, double sourceRate) {
        return scaleCount(baseCount, sourceRate, resourceRate(itemId));
    }

    static int scaleCount(int baseCount, double sourceRate, double resourceRate) {
        if (baseCount <= 0) return 0;

        double source = sanitizeRate(sourceRate);
        double resource = sanitizeRate(resourceRate);
        double scaled = baseCount * source * resource;
        if (!Double.isFinite(scaled)) return Integer.MAX_VALUE;
        if (scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(scaled);
    }

    /** Mutates reward items in place after their source has finished determining base counts. */
    public static void scaleItems(List<GameItem> items, double sourceRate) {
        if (items == null || items.isEmpty()) return;

        var iterator = items.iterator();
        while (iterator.hasNext()) {
            GameItem item = iterator.next();
            if (item == null) {
                iterator.remove();
                continue;
            }

            int count = scaleCount(item.getItemId(), item.getCount(), sourceRate);
            if (count <= 0) {
                iterator.remove();
            } else {
                item.setCount(count);
            }
        }
    }

    private static double resourceRate(int itemId) {
        if (GAME_OPTIONS == null || GAME_OPTIONS.rates == null) return 1.0;
        return switch (itemId) {
            case ADVENTURE_EXP_ITEM_ID -> GAME_OPTIONS.rates.adventureExp;
            case MORA_ITEM_ID -> GAME_OPTIONS.rates.mora;
            default -> 1.0;
        };
    }

    private static double sanitizeRate(double rate) {
        if (Double.isNaN(rate)) return 0.0;
        if (rate == Double.POSITIVE_INFINITY) return Double.MAX_VALUE;
        if (rate == Double.NEGATIVE_INFINITY) return 0.0;
        return Math.max(0.0, rate);
    }
}
