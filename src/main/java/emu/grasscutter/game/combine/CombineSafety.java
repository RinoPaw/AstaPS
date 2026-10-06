package emu.grasscutter.game.combine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CombineSafety {
    static final int MAX_QUANTITY = 10_000;

    record Cost(int itemId, int count) {}
    record Plan(List<Cost> costs, int resultCount) {}

    private CombineSafety() {}

    static Plan plan(int quantity, int resultPerCombine, List<Cost> baseCosts) {
        if (quantity <= 0 || quantity > MAX_QUANTITY || resultPerCombine <= 0 || baseCosts == null) {
            return null;
        }

        long result = (long) resultPerCombine * quantity;
        if (result <= 0 || result > Integer.MAX_VALUE) {
            return null;
        }

        Map<Integer, Long> totals = new LinkedHashMap<>();
        for (Cost cost : baseCosts) {
            if (cost == null || cost.itemId() <= 0 || cost.count() <= 0) {
                return null;
            }
            long scaled = (long) cost.count() * quantity;
            if (scaled <= 0 || scaled > Integer.MAX_VALUE) {
                return null;
            }
            long aggregated;
            try {
                aggregated = Math.addExact(totals.getOrDefault(cost.itemId(), 0L), scaled);
            } catch (ArithmeticException overflow) {
                return null;
            }
            if (aggregated > Integer.MAX_VALUE) {
                return null;
            }
            totals.put(cost.itemId(), aggregated);
        }

        List<Cost> costs =
                totals.entrySet().stream()
                        .map(entry -> new Cost(entry.getKey(), entry.getValue().intValue()))
                        .toList();
        return new Plan(costs, (int) result);
    }
}
