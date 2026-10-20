package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Shared relative-weight choice for type, count and item properties.
 * Every outcome requires an explicit weight; no weight total or implicit
 * empty outcome is assumed.
 */
final class WeightedChoice<T> {
    private final List<T> candidates;
    private final double[] boundaries;

    private WeightedChoice(List<T> candidates, double[] boundaries) {
        this.candidates = List.copyOf(candidates);
        this.boundaries = boundaries;
    }

    static <T> WeightedChoice<T> parse(
            String expression, String location, Function<String, T> resolve) {
        Objects.requireNonNull(resolve, "resolve");
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(location + ": empty weighted choice");
        }
        var candidates = new ArrayList<T>();
        var weights = new ArrayList<BigDecimal>();
        var names = new HashSet<String>();
        BigDecimal total = BigDecimal.ZERO;
        for (String segment : expression.split(",", -1)) {
            String[] pair = segment.trim().split(":", -1);
            if (pair.length != 2 || pair[0].isBlank()
                    || !pair[1].trim().matches("[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException(
                        location + ": expected candidate:weight: " + segment);
            }
            String name = pair[0].trim();
            if (!names.add(name)) {
                throw new IllegalArgumentException(location + ": duplicate candidate: " + name);
            }
            var weight = new BigDecimal(pair[1].trim());
            if (weight.signum() == 0) continue;
            T candidate = Objects.requireNonNull(
                    resolve.apply(name), location + ": invalid candidate " + name);
            candidates.add(candidate);
            weights.add(weight);
            total = total.add(weight);
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException(location + ": no positive weights");
        }
        double[] cumulative = new double[weights.size()];
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            sum = sum.add(weights.get(i));
            cumulative[i] = sum.divide(total, MathContext.DECIMAL128).doubleValue();
        }
        cumulative[cumulative.length - 1] = 1.0;
        return new WeightedChoice<>(candidates, cumulative);
    }

    T sample(RandomGenerator random) {
        double ticket = random.nextDouble();
        for (int i = 0; i < boundaries.length - 1; i++) {
            if (ticket < boundaries[i]) return candidates.get(i);
        }
        return candidates.getLast();
    }

    List<T> candidates() {
        return candidates;
    }
}
