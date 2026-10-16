package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Supports integral counts, 1~3 ranges and percentages; unallocated probability means zero. */
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
        if (text.contains(":") || text.contains(",")) return distribution(text);
        if (text.contains(".")) {
            // Native DropTable countRange decimals are expected values:
            // floor(value) plus one with probability equal to the fractional part.
            BigDecimal amount = decimal(text);
            int floor = amount.intValue();
            double fraction = amount.remainder(BigDecimal.ONE).doubleValue();
            return fraction == 0 ? rng -> floor :
                    rng -> floor + (rng.nextDouble() < fraction ? 1 : 0);
        }
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

    private static Spec distribution(String text) {
        Map<Integer, BigDecimal> probabilities = new LinkedHashMap<>();
        Integer missing = null;
        BigDecimal total = BigDecimal.ZERO;
        for (String part : text.split(",", -1)) {
            String[] parts = part.trim().split(":", -1);
            if (parts.length < 1 || parts.length > 2) throw new IllegalArgumentException("Invalid outcome: " + part);
            int amount = integer(parts[0].trim());
            if (probabilities.containsKey(amount)) throw new IllegalArgumentException("Duplicate count: " + amount);
            if (parts.length == 1) {
                if (missing != null) throw new IllegalArgumentException("Multiple missing probabilities: " + text);
                missing = amount;
                probabilities.put(amount, null);
            } else {
                BigDecimal probability = BigDecimal.valueOf(integer(parts[1].trim()));
                if (probability.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("Probability above 100%");
                total = total.add(probability);
                probabilities.put(amount, probability);
            }
        }
        if (total.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("Probability total above 100%");
        BigDecimal rest = HUNDRED.subtract(total);
        if (missing != null) probabilities.put(missing, rest);
        else if (rest.signum() > 0) probabilities.merge(0, rest, BigDecimal::add);

        var amounts = new java.util.ArrayList<Integer>();
        var boundaries = new java.util.ArrayList<Double>();
        double cumulative = 0;
        for (var entry : probabilities.entrySet()) {
            if (entry.getValue().signum() <= 0) continue;
            cumulative += entry.getValue().divide(HUNDRED).doubleValue();
            amounts.add(entry.getKey());
            boundaries.add(cumulative);
        }
        if (amounts.isEmpty()) throw new IllegalArgumentException("No possible count: " + text);
        return rng -> {
            double roll = rng.nextDouble();
            for (int i = 0; i < amounts.size() - 1; i++) {
                if (roll < boundaries.get(i)) return amounts.get(i);
            }
            return amounts.getLast();
        };
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
