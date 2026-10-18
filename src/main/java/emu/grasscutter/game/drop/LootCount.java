package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Integer reward counts, ranges, percentage distributions and independent bonus rolls. */
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
        if (text.contains("+")) return bonus(text);
        if (text.contains(":") || text.contains(",")) return distribution(text);
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

    /** CSS roll expressions use count:relative-weight, with no implicit empty result. */
    public static Spec parseRoll(Object raw) {
        if (raw == null) return rng -> 1;
        String text = raw.toString().trim().replaceAll("\\s+", "");
        if (text.contains("+")) {
            throw new IllegalArgumentException("Bonus '+' syntax is not supported in CSS rolls");
        }
        if (!text.contains(":") && !text.contains(",")) return parse(text);
        var amounts = new java.util.ArrayList<Integer>();
        var weights = new java.util.ArrayList<BigDecimal>();
        BigDecimal total = BigDecimal.ZERO;
        var seen = new java.util.HashSet<Integer>();
        for (String part : text.split(",", -1)) {
            String[] pair = part.split(":", -1);
            if (pair.length != 2 || !pair[1].matches("[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException("Expected count:weight in CSS roll: " + part);
            }
            int amount = integer(pair[0]);
            if (!seen.add(amount)) throw new IllegalArgumentException("Duplicate roll count: " + amount);
            BigDecimal weight = new BigDecimal(pair[1]);
            if (weight.signum() == 0) continue;
            amounts.add(amount);
            weights.add(weight);
            total = total.add(weight);
        }
        if (amounts.isEmpty()) throw new IllegalArgumentException("CSS roll has no positive weight");
        if (amounts.size() == 1) {
            int amount = amounts.getFirst();
            return rng -> amount;
        }
        double[] boundaries = new double[weights.size()];
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            cumulative = cumulative.add(weights.get(i));
            boundaries[i] = cumulative.divide(total, java.math.MathContext.DECIMAL128).doubleValue();
        }
        return rng -> {
            double ticket = rng.nextDouble();
            for (int i = 0; i < boundaries.length - 1; i++) {
                if (ticket < boundaries[i]) return amounts.get(i);
            }
            return amounts.getLast();
        };
    }

    /**
     * N+K:P runs N times and independently adds K further executions with P% chance.
     * In nested loot, each execution rolls the nested pool separately.
     */
    private static Spec bonus(String text) {
        if (!text.matches("[0-9]+\\+[0-9]+:[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid bonus count: " + text);
        }
        String[] parts = text.split("[+:]");
        int base = integer(parts[0]);
        int extra = integer(parts[1]);
        if (extra == 0 || (long) base + extra > MAX_COUNT) {
            throw new IllegalArgumentException("Bonus count exceeds supported range: " + text);
        }
        BigDecimal percent = percentage(parts[2]);
        int total = base + extra;
        if (percent.signum() == 0) return rng -> base;
        if (percent.compareTo(HUNDRED) == 0) return rng -> total;
        double chance = percent.movePointLeft(2).doubleValue();
        return rng -> rng.nextDouble() < chance ? total : base;
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
            return base + "+1:" + fraction.movePointRight(2)
                    .stripTrailingZeros().toPlainString();
        }
        // Native integer and inclusive-range encodings remain intact.
        parse(source);
        return source;
    }

    private static BigDecimal percentage(String value) {
        if (!value.matches("[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid percentage: " + value);
        }
        BigDecimal chance = decimal(value);
        if (chance.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Probability above 100%: " + value);
        }
        return chance;
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
                BigDecimal probability = percentage(parts[1].trim());
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
