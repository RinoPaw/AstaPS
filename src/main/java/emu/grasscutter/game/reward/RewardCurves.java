package emu.grasscutter.game.reward;

import emu.grasscutter.game.drop.LootCount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Exact, discrete reward-count curves. Interpolation and silent fallback are prohibited.
 * A curve maps a level (or inclusive level interval) to a complete LootCount expression.
 */
public final class RewardCurves {
    private record Interval(int min, int max, String value) {
        boolean contains(int level) {
            return min <= level && level <= max;
        }
    }

    public static final class Curve {
        private final String name;
        private final List<Interval> intervals;

        private Curve(String name, List<Interval> intervals) {
            this.name = name;
            this.intervals = List.copyOf(intervals);
        }

        public String lookup(String level) {
            if (level == null || !level.matches("[0-9]+")) {
                throw new IllegalArgumentException("Curve " + name + " requires integer level");
            }
            final int actual;
            try {
                actual = Integer.parseInt(level);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid level for curve " + name + ": " + level, e);
            }
            for (var interval : intervals) {
                if (interval.contains(actual)) return interval.value();
            }
            throw new IllegalArgumentException(
                    "No reward-curve value for " + name + " at level " + actual);
        }
    }

    private final Map<String, Curve> curves;

    private RewardCurves(Map<String, Curve> curves) {
        this.curves = Map.copyOf(curves);
    }

    public static RewardCurves empty() {
        return new RewardCurves(Map.of());
    }

    public Curve require(String name) {
        var result = curves.get(name);
        if (result == null) {
            throw new IllegalArgumentException("Unknown reward curve: " + name);
        }
        return result;
    }

    public static RewardCurves parse(String yaml) {
        var settings = LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setCodePointLimit(1_000_000)
                .build();
        Object root = new Load(settings).loadFromString(yaml);
        if (!(root instanceof Map<?, ?> document)) {
            throw new IllegalArgumentException("Reward curves must be a mapping");
        }
        var parsed = new LinkedHashMap<String, Curve>();
        for (var entry : document.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !name.matches("[a-z][a-z0-9_]*")
                    || !(entry.getValue() instanceof Map<?, ?> rows) || rows.isEmpty()) {
                throw new IllegalArgumentException("Invalid reward-curve definition: " + entry.getKey());
            }
            var intervals = new ArrayList<Interval>();
            for (var row : rows.entrySet()) {
                if (!(row.getKey() instanceof String range)
                        || !range.matches("[0-9]+(?:~[0-9]*)?")
                        || !(row.getValue() instanceof String count)) {
                    throw new IllegalArgumentException("Invalid range or count in curve " + name);
                }
                String[] bounds = range.split("~", -1);
                final int min, max;
                try {
                    min = Integer.parseInt(bounds[0]);
                    max = bounds.length == 1 || bounds[1].isEmpty()
                            ? (bounds.length == 1 ? min : Integer.MAX_VALUE)
                            : Integer.parseInt(bounds[1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid level range: " + range, e);
                }
                if (max < min) throw new IllegalArgumentException("Reversed curve range: " + range);
                LootCount.parse(count); // Reject malformed or out-of-range probabilities at load time.
                for (var previous : intervals) {
                    if (min <= previous.max() && max >= previous.min()) {
                        throw new IllegalArgumentException(
                                "Overlapping level ranges in curve " + name + ": " + range);
                    }
                }
                intervals.add(new Interval(min, max, count));
            }
            if (parsed.putIfAbsent(name, new Curve(name, intervals)) != null) {
                throw new IllegalArgumentException("Duplicate reward curve: " + name);
            }
        }
        return new RewardCurves(parsed);
    }
}
