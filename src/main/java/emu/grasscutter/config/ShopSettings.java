package emu.grasscutter.config;

import java.util.*;

/** Dynamic shop gameplay settings stored at the root of game.json. */
public final class ShopSettings {
    public Artifact artifact = new Artifact();

    public void normalize() {
        if (artifact == null) artifact = new Artifact();
        artifact.normalize();
    }

    public static final class Artifact {
        public boolean enabled = true;
        public int buyLimit = 0;
        public Map<Integer, Integer> regionalShops = defaultRegionalShops();
        public Map<Integer, Map<Integer, Integer>> resinCosts = defaultResinCosts();
        public Map<Integer, EnhancementRange> initialEnhancement = defaultInitialEnhancement();

        public void normalize() {
            buyLimit = Math.max(0, buyLimit);

            var routes = new LinkedHashMap<Integer, Integer>();
            if (regionalShops != null) {
                regionalShops.entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getKey() != null
                                                && entry.getValue() != null
                                                && entry.getKey() > 0
                                                && entry.getValue() > 0)
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> routes.put(entry.getKey(), entry.getValue()));
            }
            regionalShops = routes.isEmpty() ? defaultRegionalShops() : routes;

            var costs = new LinkedHashMap<Integer, Map<Integer, Integer>>();
            if (resinCosts != null) {
                resinCosts.entrySet().stream()
                        .filter(entry -> entry.getKey() != null && entry.getKey() >= 0)
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(
                                entry -> {
                                    if (entry.getValue() == null) return;
                                    var prices = new LinkedHashMap<Integer, Integer>();
                                    entry.getValue().entrySet().stream()
                                            .filter(
                                                    price ->
                                                            price.getKey() != null
                                                                    && price.getValue() != null
                                                                    && price.getKey() >= 2
                                                                    && price.getKey() <= 5
                                                                    && price.getValue() > 0)
                                            .sorted(Map.Entry.comparingByKey())
                                            .forEach(
                                                    price ->
                                                            prices.put(
                                                                    price.getKey(), price.getValue()));
                                    if (!prices.isEmpty()) costs.put(entry.getKey(), prices);
                                });
            }
            resinCosts = costs.isEmpty() ? defaultResinCosts() : costs;

            var enhancement = new LinkedHashMap<Integer, EnhancementRange>();
            if (initialEnhancement != null) {
                initialEnhancement.entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getKey() != null
                                                && entry.getKey() >= 0
                                                && entry.getValue() != null)
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(
                                entry -> {
                                    entry.getValue().normalize();
                                    enhancement.put(entry.getKey(), entry.getValue());
                                });
            }
            initialEnhancement =
                    enhancement.isEmpty() ? defaultInitialEnhancement() : enhancement;
        }

        public int resinCost(int clearAdventureRank, int artifactRank) {
            int bestThreshold = Integer.MIN_VALUE;
            Map<Integer, Integer> prices = null;
            for (var entry : resinCosts.entrySet()) {
                Integer threshold = entry.getKey();
                if (threshold != null
                        && threshold <= clearAdventureRank
                        && threshold > bestThreshold) {
                    bestThreshold = threshold;
                    prices = entry.getValue();
                }
            }
            return prices == null ? 0 : Math.max(0, prices.getOrDefault(artifactRank, 0));
        }

        public EnhancementRange initialEnhancementRange(int worldLevel) {
            if (initialEnhancement.isEmpty()) return new EnhancementRange(0, 0);

            int bestBelow = Integer.MIN_VALUE;
            EnhancementRange below = null;
            int bestAbove = Integer.MAX_VALUE;
            EnhancementRange above = null;
            for (var entry : initialEnhancement.entrySet()) {
                int key = entry.getKey();
                if (key <= worldLevel && key > bestBelow) {
                    bestBelow = key;
                    below = entry.getValue();
                }
                if (key >= worldLevel && key < bestAbove) {
                    bestAbove = key;
                    above = entry.getValue();
                }
            }
            return below != null ? below : above != null ? above : new EnhancementRange(0, 0);
        }

        private static Map<Integer, Integer> defaultRegionalShops() {
            var values = new LinkedHashMap<Integer, Integer>();
            values.put(1, 1004);
            values.put(2, 1008);
            values.put(3, 1056);
            values.put(4, 1074);
            values.put(5, 1093);
            return values;
        }

        private static Map<Integer, Map<Integer, Integer>> defaultResinCosts() {
            var values = new LinkedHashMap<Integer, Map<Integer, Integer>>();
            values.put(22, prices(2, 3, 3, 6));
            values.put(25, prices(2, 2, 3, 5));
            values.put(30, prices(2, 2, 3, 3, 4, 20));
            values.put(35, prices(2, 1, 3, 3, 4, 15));
            values.put(40, prices(2, 1, 3, 2, 4, 10, 5, 50));
            values.put(45, prices(2, 1, 3, 2, 4, 8, 5, 20));
            return values;
        }

        private static Map<Integer, EnhancementRange> defaultInitialEnhancement() {
            var values = new LinkedHashMap<Integer, EnhancementRange>();
            values.put(0, new EnhancementRange(0, 0));
            values.put(1, new EnhancementRange(1, 4));
            values.put(2, new EnhancementRange(3, 6));
            values.put(3, new EnhancementRange(5, 8));
            values.put(4, new EnhancementRange(7, 10));
            values.put(5, new EnhancementRange(9, 12));
            values.put(6, new EnhancementRange(11, 14));
            values.put(7, new EnhancementRange(13, 16));
            values.put(8, new EnhancementRange(14, 17));
            values.put(9, new EnhancementRange(15, 18));
            return values;
        }

        private static Map<Integer, Integer> prices(int... values) {
            var out = new LinkedHashMap<Integer, Integer>();
            for (int i = 0; i + 1 < values.length; i += 2) out.put(values[i], values[i + 1]);
            return out;
        }
    }

    public static final class EnhancementRange {
        public int min;
        public int max;

        public EnhancementRange() {}

        public EnhancementRange(int min, int max) {
            this.min = min;
            this.max = max;
            normalize();
        }

        private void normalize() {
            min = Math.max(0, min);
            max = Math.max(min, max);
        }
    }
}
