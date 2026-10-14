package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * YAML loot lists: an all-null-weight list executes all entries; an all-numeric-weight
 * list chooses one. Mixed weights are rejected, even if the numeric weight is zero.
 * item may be an item name, numeric ID, nested YAML list, or null (empty reward).
 */
public final class YamlLootTables {
    @FunctionalInterface
    public interface ItemResolver {
        int resolve(String itemName);
    }

    private record Entry(Integer item, Pool nested, LootCount.Spec count, BigDecimal weight) {}
    private record Pool(boolean weighted, List<Entry> entries, double totalWeight) {}

    private static final int MAX_DEPTH = 64;
    private static final int MAX_TABLES = 4096;
    private static final int MAX_ENTRIES = 20_000;

    private static final class CompileBudget {
        int tables;
        int entries;
    }

    private final Map<String, Pool> pools;
    private YamlLootTables(Map<String, Pool> pools) {
        this.pools = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pools));
    }

    public static YamlLootTables parse(String yaml, ItemResolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("Missing item resolver");
        var settings = LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(2048)
                .setCodePointLimit(1_000_000)
                .build();
        Object document = new Load(settings).loadFromString(yaml);
        if (!(document instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Loot YAML root must be a mapping of named lists");
        }
        if (raw.size() > MAX_TABLES) throw new IllegalArgumentException("Too many loot tables");
        var budget = new CompileBudget();
        var parsed = new LinkedHashMap<String, Pool>();
        var compiled = new IdentityHashMap<Object, Pool>();
        var visiting = new IdentityHashMap<Object, Boolean>();
        for (var e : raw.entrySet()) {
            if (!(e.getKey() instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Loot table name must be a nonempty string");
            }
            parsed.put(name, parseList(e.getValue(), resolver, compiled, visiting, budget, name, 0));
        }
        return new YamlLootTables(parsed);
    }

    private static Pool parseList(Object raw, ItemResolver resolver,
            IdentityHashMap<Object, Pool> compiled,
            IdentityHashMap<Object, Boolean> visiting, CompileBudget budget,
            String location, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException(location + ": loot nesting too deep");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(location + ": expected a YAML list");
        }
        if (visiting.containsKey(raw)) {
            throw new IllegalArgumentException(location + ": cyclic YAML loot alias");
        }
        Pool cached = compiled.get(raw);
        if (cached != null) return cached;
        if (++budget.tables > MAX_TABLES) throw new IllegalArgumentException("Too many unique nested loot lists");
        if (list.size() > MAX_ENTRIES - budget.entries) {
            throw new IllegalArgumentException(location + ": too many loot entries");
        }
        budget.entries += list.size();
        visiting.put(raw, true);
        try {
            var entries = new ArrayList<Entry>();
            boolean hasWeight = false, hasNull = false;
            for (int i = 0; i < list.size(); i++) {
                String at = location + "[" + i + "]";
                if (!(list.get(i) instanceof Map<?, ?> fields)) {
                    throw new IllegalArgumentException(at + ": expected {item, count?, weight?}");
                }
                for (Object key : fields.keySet()) {
                    if (!"item".equals(key) && !"count".equals(key) && !"weight".equals(key)) {
                        throw new IllegalArgumentException(at + ": unknown field: " + key);
                    }
                }
                if (!fields.containsKey("item")) {
                    throw new IllegalArgumentException(at + ": missing item field");
                }
                Object target = fields.get("item");
                Integer item = null;
                Pool nested = null;
                if (target instanceof List<?>) {
                    nested = parseList(target, resolver, compiled, visiting, budget, at + ".item", depth + 1);
                } else if (target != null) {
                    if (!(target instanceof String || target instanceof Number)) {
                        throw new IllegalArgumentException(at + ": invalid item: " + target);
                    }
                    int id = resolver.resolve(target.toString());
                    if (id <= 0) throw new IllegalArgumentException(at + ": unknown item: " + target);
                    item = id;
                }
                LootCount.Spec count = LootCount.parse(fields.get("count"));
                BigDecimal weight = null;
                Object value = fields.get("weight");
                if (value != null) {
                    if (!(value instanceof Number)) throw new IllegalArgumentException(at + ": weight must be numeric or null");
                    try {
                        weight = new BigDecimal(value.toString());
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(at + ": invalid weight", ex);
                    }
                    if (weight.signum() < 0 || weight.compareTo(new BigDecimal("1000000000")) > 0) {
                        throw new IllegalArgumentException(at + ": weight outside 0..1000000000");
                    }
                    hasWeight = true;
                } else hasNull = true;
                entries.add(new Entry(item, nested, count, weight));
            }
            if (hasWeight && hasNull) {
                throw new IllegalArgumentException(location + ": cannot mix null and numeric weights");
            }
            double total = 0;
            if (hasWeight) {
                for (Entry e : entries) total += e.weight.doubleValue();
                if (total <= 0) throw new IllegalArgumentException(location + ": all weights are zero");
            }
            Pool pool = new Pool(hasWeight, List.copyOf(entries), total);
            compiled.put(raw, pool);
            return pool;
        } finally {
            visiting.remove(raw);
        }
    }

    /** Returns an immutable item-ID-to-count map. Each invocation rolls afresh. */
    public Map<Integer, Integer> roll(String name, RandomGenerator random) {
        Pool root = pools.get(name);
        if (root == null) throw new IllegalArgumentException("Unknown loot table: " + name);
        if (random == null) throw new IllegalArgumentException("Missing random generator");
        var reward = new TreeMap<Integer, Integer>();
        int[] remaining = {100_000};
        rollPool(root, 1, random, reward, remaining, 0);
        return Map.copyOf(reward);
    }

    private static void rollPool(Pool pool, int times, RandomGenerator random,
            Map<Integer, Integer> reward, int[] remaining, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalStateException("Loot nesting exceeds " + MAX_DEPTH + " levels");
        for (int n = 0; n < times; n++) {
            if (--remaining[0] < 0) throw new IllegalStateException("Loot evaluation budget exceeded");
            if (pool.weighted) {
                double ticket = random.nextDouble(pool.totalWeight);
                Entry selected = null;
                for (Entry e : pool.entries) {
                    ticket -= e.weight.doubleValue();
                    if (ticket < 0) { selected = e; break; }
                }
                if (selected != null) rollEntry(selected, random, reward, remaining, depth);
            } else {
                for (Entry e : pool.entries) rollEntry(e, random, reward, remaining, depth);
            }
        }
    }

    private static void rollEntry(Entry entry, RandomGenerator random,
            Map<Integer, Integer> reward, int[] remaining, int depth) {
        if (--remaining[0] < 0) throw new IllegalStateException("Loot evaluation budget exceeded");
        int count = entry.count.sample(random);
        if (count == 0) return;
        if (entry.nested != null) {
            rollPool(entry.nested, count, random, reward, remaining, depth + 1);
        } else if (entry.item != null) {
            reward.merge(entry.item, count, Math::addExact);
        }
    }

    public List<String> names() { return List.copyOf(pools.keySet()); }
}
