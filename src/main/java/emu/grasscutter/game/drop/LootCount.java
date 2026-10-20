package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

/** Integer reward counts, ranges and explicit relative-weight distributions. */
public final class LootCount {
    private LootCount() {}

    @FunctionalInterface
    public interface Spec {
        int sample(RandomGenerator random);
    }

    public static Spec parse(Object raw) {
        if (raw == null) return rng -> 1;
        if (!(raw instanceof Number || raw instanceof String)) {
            throw new IllegalArgumentException("Invalid count: " + raw);
        }
        String text = raw.toString().trim();
        if (text.contains(":") || text.contains(",")) return parseRoll(text);
        if (text.contains("~") || text.contains(";")) {
            String[] parts = text.split("[~;]", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid range: " + text);
            int low = integer(parts[0]), high = integer(parts[1]);
            if (high < low) throw new IllegalArgumentException("Reversed range: " + text);
            return rng -> low == high ? low : (int) rng.nextLong(low, (long) high + 1);
        }
        int fixed = integer(text);
        return rng -> fixed;
    }

    /** All count candidates use the same relative-weight syntax as type and rarity. */
    public static Spec parseRoll(Object raw) {
        if (raw == null) return rng -> 1;
        String text = raw.toString().trim();
        if (!text.contains(":") && !text.contains(",")) return parse(text);
        WeightedChoice<Integer> choice = WeightedChoice.parse(text, "count", LootCount::integer);
        return choice::sample;
    }

    private static BigDecimal decimal(String value) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) {
                throw new IllegalArgumentException("Negative count is invalid: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value, e);
        }
    }

    private static int integer(String value) {
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Expected integer: " + value);
        try {
            return decimal(value).intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Count cannot fit an integer: " + value, failure);
        }
    }
}
