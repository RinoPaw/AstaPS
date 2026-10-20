package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

/** Integer reward counts, ranges and explicit relative-weight distributions. */
public final class LootCount {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final int MAX_COUNT = 100_000;
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
            return rng -> low == high ? low : rng.nextInt(low, high + 1);
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

    /** Convert native fractional expectations at the import boundary, never in user YAML. */
    static String fromNativeCount(String value) {
        if (value == null) throw new IllegalArgumentException("Missing native count");
        String source = value.trim();
        if (source.matches("[0-9]+\\.[0-9]+")) {
            BigDecimal amount = decimal(source);
            int base = amount.intValue();
            BigDecimal fraction = amount.remainder(BigDecimal.ONE);
            if (fraction.signum() == 0) return Integer.toString(base);
            if (base >= MAX_COUNT) {
                throw new IllegalArgumentException("Native bonus count exceeds limit: " + value);
            }
            BigDecimal upperWeight = fraction.movePointRight(2);
            BigDecimal lowerWeight = HUNDRED.subtract(upperWeight);
            return base + ":" + lowerWeight.stripTrailingZeros().toPlainString()
                    + ", " + (base + 1) + ":"
                    + upperWeight.stripTrailingZeros().toPlainString();
        }
        // Native integer and inclusive-range encodings remain intact.
        parse(source);
        return source;
    }

    private static BigDecimal decimal(String value) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0 || parsed.compareTo(BigDecimal.valueOf(MAX_COUNT)) > 0) {
                throw new IllegalArgumentException("Count/probability outside supported range: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value, e);
        }
    }

    private static int integer(String value) {
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Expected integer: " + value);
        return decimal(value).intValueExact();
    }
}
