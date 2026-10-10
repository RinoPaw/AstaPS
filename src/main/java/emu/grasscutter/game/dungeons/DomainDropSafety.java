package emu.grasscutter.game.dungeons;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Validation and one-claim commit guard for configured dungeon rewards. */
final class DomainDropSafety {
    private static final int MAX_COUNT_CHOICES = 10_000;

    private DomainDropSafety() {}

    static void validatePool(int dungeonId, List<DungeonDropEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw invalid(dungeonId, "no reward entries");
        }
        for (int index = 0; index < entries.size(); index++) {
            DungeonDropEntry entry = entries.get(index);
            if (entry == null || entry.getCounts() == null || entry.getCounts().isEmpty()) {
                throw invalid(dungeonId, "entry " + index + " has no counts");
            }
            List<Integer> counts = entry.getCounts();
            Integer start = counts.get(0);
            Integer end = counts.get(counts.size() - 1);
            if (start == null || end == null || start < 0 || end < start) {
                throw invalid(dungeonId, "entry " + index + " has invalid count bounds");
            }
            long choices = (long) end - start + 1;
            if (choices > MAX_COUNT_CHOICES) {
                throw invalid(dungeonId, "entry " + index + " has an excessive count range");
            }
            validateWeights(dungeonId, index, "count", entry.getProbabilities(), (int) choices);

            List<Integer> items = entry.getItems();
            if (items == null || items.isEmpty() || items.stream().anyMatch(id -> id == null || id <= 0)) {
                throw invalid(dungeonId, "entry " + index + " has no valid item pool");
            }
            validateWeights(dungeonId, index, "item", entry.getItemProbabilities(), items.size());
        }
    }

    private static void validateWeights(
            int dungeonId, int index, String kind, List<Integer> weights, int choices) {
        if (weights == null || weights.isEmpty()) {
            return; // No explicit weights means uniform selection.
        }
        if (weights.size() != choices) {
            throw invalid(dungeonId, "entry " + index + " has mismatched " + kind + " weights");
        }
        long total = 0;
        for (Integer weight : weights) {
            if (weight == null || weight < 0) {
                throw invalid(dungeonId, "entry " + index + " has negative/null " + kind + " weight");
            }
            total += weight;
        }
        if (total <= 0 || total >= Integer.MAX_VALUE) {
            throw invalid(dungeonId, "entry " + index + " has invalid " + kind + " weight sum");
        }
    }

    private static IllegalArgumentException invalid(int dungeonId, String explanation) {
        return new IllegalArgumentException(
                "DungeonDrop.json dungeon " + dungeonId + ": " + explanation);
    }

    /**
     * Serialize the final reward check, payment, inventory grant and claim marker. Callers must
     * prepare and decorate rewards first, before a consumable resource can be charged.
     */
    static <T> boolean commitOnce(
            Object lock,
            Set<Integer> rewarded,
            int uid,
            List<T> rewards,
            Predicate<T> usable,
            BooleanSupplier pay,
            Consumer<List<T>> grant) {
        synchronized (lock) {
            if (rewarded == null || rewarded.contains(uid) || rewards == null) {
                return false;
            }
            rewards.removeIf(item -> !usable.test(item));
            if (rewards.isEmpty() || !pay.getAsBoolean()) {
                return false;
            }
            grant.accept(rewards);
            rewarded.add(uid);
            return true;
        }
    }
}
