package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * A single weighted-expression evaluator for entry fields and reward pools.
 * Every candidate has an explicit relative weight, with no implicit outcome.
 * A scalar compiles to a constant expression.
 */
final class WeightedChoice<T> {
    private final List<T> candidates;
    private final double[] boundaries;
    private final boolean constant;

    private WeightedChoice(List<T> candidates, double[] boundaries, boolean constant) {
        this.candidates = List.copyOf(candidates);
        this.boundaries = boundaries;
        this.constant = constant;
    }

    static <T> WeightedChoice<T> compile(
            Object raw, String location, Function<Object, T> resolve) {
        Objects.requireNonNull(resolve, "resolve");
        if (raw instanceof String text && (text.contains(":") || text.contains(","))) {
            return parse(text, location, name -> resolve.apply(name));
        }
        return constant(Objects.requireNonNull(resolve.apply(raw),
                location + ": invalid value " + raw));
    }

    private static <T> WeightedChoice<T> constant(T candidate) {
        return new WeightedChoice<>(List.of(candidate), new double[] {1.0}, true);
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
            candidates.add(Objects.requireNonNull(resolve.apply(name),
                    location + ": invalid candidate " + name));
            weights.add(weight);
        }
        return of(candidates, weights, location);
    }

    /** A weighted pool is the same expression, with entries as its candidates. */
    static <T> WeightedChoice<T> of(
            List<T> candidates, List<BigDecimal> weights, String location) {
        if (candidates.size() != weights.size()) {
            throw new IllegalArgumentException(location + ": mismatched weighted candidates");
        }
        var selected = new ArrayList<T>();
        var selectedWeights = new ArrayList<BigDecimal>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < candidates.size(); i++) {
            BigDecimal weight = weights.get(i);
            if (weight.signum() < 0) {
                throw new IllegalArgumentException(location + ": negative weight");
            }
            if (weight.signum() == 0) continue;
            selected.add(Objects.requireNonNull(candidates.get(i)));
            selectedWeights.add(weight);
            total = total.add(weight);
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException(location + ": no positive weights");
        }
        double[] cumulative = new double[selectedWeights.size()];
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < selectedWeights.size(); i++) {
            sum = sum.add(selectedWeights.get(i));
            cumulative[i] = sum.divide(total, MathContext.DECIMAL128).doubleValue();
        }
        cumulative[cumulative.length - 1] = 1.0;
        return new WeightedChoice<>(selected, cumulative, false);
    }

    T sample(RandomGenerator random) {
        if (constant) return candidates.getFirst();
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
