package emu.grasscutter.game.drop;

import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.common.ItemParamData;
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
 * reward may be a named item, nested YAML list, nested entry mapping,
 * or null (empty reward).
 */
public final class YamlLootTables {
    @FunctionalInterface
    public interface ItemResolver {
        int resolve(String itemName);
    }

    private record Entry(Integer rewardId, ArtifactResolver.Selector artifact,
                         WeightedItemSelector grade, WeightedItemSelector element,
                         Pool nested, LootCount.Spec count, BigDecimal weight) {}

    /**
     * An advancement family resolves one native item per rarity. Rarity probabilities
     * are percentages summing to 100; weights on outer entries are independent.
     * A chosen grade applies to the entire count of that entry execution.
     */
    private record WeightedItemSelector(int[] itemIds, double[] cumulative) {
        int select(RandomGenerator random) {
            double ticket = random.nextDouble();
            for (int i = 0; i < itemIds.length; i++) {
                if (ticket < cumulative[i]) return itemIds[i];
            }
            // The last boundary is exactly 1.0 for a valid distribution.
            return itemIds[itemIds.length - 1];
        }
    }

    private static WeightedItemSelector compileGrade(
            String family, Object rawProperties, ItemResolver resolver, String location) {
        var path = ResourceId.parse(family);
        if (path.size() != 3 || !"genshin".equals(path.segment(0))
                || (!"avatar_ascension".equals(path.segment(1))
                    && !"talent_up".equals(path.segment(1)))) {
            throw new IllegalArgumentException(location
                    + ": rarity properties require an ascension gem or talent-book family");
        }
        int maxGrade = "talent_up".equals(path.segment(1)) ? 4 : 5;
        if (!(rawProperties instanceof Map<?, ?> props)
                || props.size() != 1 || !props.containsKey("rarity")) {
            throw new IllegalArgumentException(location
                    + ": advancement properties require a rarity or a rarity distribution");
        }
        Object rarity = props.get("rarity");
        if (rarity instanceof Integer fixedGrade) {
            if (fixedGrade < 2 || fixedGrade > maxGrade) {
                throw new IllegalArgumentException(location
                        + ": invalid fixed advancement rarity: " + fixedGrade);
            }
            int itemId = resolver.resolve(family + "." + fixedGrade);
            if (itemId <= 0) {
                throw new IllegalArgumentException(location
                        + ": unknown advancement family or rarity: " + family + "." + fixedGrade);
            }
            return new WeightedItemSelector(new int[] {itemId}, new double[] {1.0});
        }
        if (!(rarity instanceof String probabilities)) {
            throw new IllegalArgumentException(location
                    + ": advancement rarity must be an integer or quoted percentage distribution");
        }
        var ids = new ArrayList<Integer>();
        var boundaries = new ArrayList<Double>();
        var seen = new java.util.HashSet<Integer>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal hundred = new BigDecimal("100");
        for (String part : probabilities.split(",", -1)) {
            String[] pair = part.trim().split(":", -1);
            if (pair.length != 2 || !pair[0].trim().matches("[2-" + maxGrade + "]")
                    || !pair[1].trim().matches("[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException(location
                        + ": invalid advancement rarity percentage: " + part);
            }
            int grade = Integer.parseInt(pair[0].trim());
            if (!seen.add(grade)) {
                throw new IllegalArgumentException(location + ": duplicate advancement rarity: " + grade);
            }
            BigDecimal percentage = new BigDecimal(pair[1].trim());
            if (percentage.compareTo(hundred) > 0) {
                throw new IllegalArgumentException(location
                        + ": advancement rarity probability above 100%: " + part);
            }
            total = total.add(percentage);
            if (total.compareTo(hundred) > 0) {
                throw new IllegalArgumentException(location
                        + ": advancement rarity percentages exceed 100%");
            }
            if (percentage.signum() == 0) continue;
            int itemId = resolver.resolve(family + "." + grade);
            if (itemId <= 0) {
                throw new IllegalArgumentException(location
                        + ": unknown advancement family or rarity: " + family + "." + grade);
            }
            ids.add(itemId);
            boundaries.add(total.movePointLeft(2).doubleValue());
        }
        if (total.compareTo(hundred) != 0) {
            throw new IllegalArgumentException(location
                    + ": advancement rarity percentages must sum to 100: " + total);
        }
        var nativeIds = new int[ids.size()];
        var cumulative = new double[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            nativeIds[i] = ids.get(i);
            cumulative[i] = boundaries.get(i);
        }
        return new WeightedItemSelector(nativeIds, cumulative);
    }

    /**
     * An elemental gem roll resolves one native item per emitted copy.
     * Each copy rerolls its element, like the original nested weighted pool.
     */
    private static WeightedItemSelector compileElements(
            Object rawProperties, ItemResolver resolver, String location) {
        if (!(rawProperties instanceof Map<?, ?> props)
                || props.size() != 2
                || !(props.get("element") instanceof String probabilities)
                || !(props.get("rarity") instanceof Integer rarity)
                || rarity < 2 || rarity > 5) {
            throw new IllegalArgumentException(location
                    + ": elemental ascension gems require quoted element odds and integer rarity 2..5");
        }
        var ids = new ArrayList<Integer>();
        var boundaries = new ArrayList<Double>();
        var seen = new java.util.HashSet<String>();
        var total = BigDecimal.ZERO;
        var hundred = new BigDecimal("100");
        for (String part : probabilities.split(",", -1)) {
            String[] pair = part.trim().split(":", -1);
            String element = pair[0].trim();
            if (pair.length != 2
                    || !List.of("anemo", "geo", "electro", "dendro",
                                    "hydro", "pyro", "cryo").contains(element)
                    || !pair[1].trim().matches("[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException(location
                        + ": invalid ascension gem element percentage: " + part);
            }
            if (!seen.add(element)) {
                throw new IllegalArgumentException(location
                        + ": duplicate ascension gem element: " + element);
            }
            var percentage = new BigDecimal(pair[1].trim());
            total = total.add(percentage);
            if (total.compareTo(hundred) > 0) {
                throw new IllegalArgumentException(location
                        + ": ascension gem element percentages exceed 100%");
            }
            if (percentage.signum() == 0) continue;
            String reference = "genshin.avatar_ascension." + element + "." + rarity;
            int itemId = resolver.resolve(reference);
            if (itemId <= 0) {
                throw new IllegalArgumentException(location
                        + ": unknown ascension gem: " + reference);
            }
            ids.add(itemId);
            boundaries.add(total.movePointLeft(2).doubleValue());
        }
        if (total.compareTo(hundred) != 0) {
            throw new IllegalArgumentException(location
                    + ": ascension gem element percentages must sum to 100: " + total);
        }
        var itemIds = new int[ids.size()];
        var cumulative = new double[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            itemIds[i] = ids.get(i);
            cumulative[i] = boundaries.get(i);
        }
        return new WeightedItemSelector(itemIds, cumulative);
    }

    private record Pool(boolean weighted, List<Entry> entries, double totalWeight) {}

    private static final int MAX_DEPTH = 64;
    private static final int MAX_TABLES = 12_000;
    private static final int MAX_ENTRIES = 40_000;

    private static final class CompileBudget {
        int tables;
        int entries;
    }

    private final String namespace;
    private final Map<String, Pool> pools;
    private final Map<String, List<LevelledLootTemplates.Variant>> levelBindings;
    private YamlLootTables(String namespace, Map<String, Pool> pools,
            Map<String, List<LevelledLootTemplates.Variant>> levelBindings) {
        this.namespace = namespace;
        this.pools = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pools));
        this.levelBindings = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(levelBindings));
    }

    /** Short keys in loot.yaml are registered under astaps.loot by default. */
    public static YamlLootTables parse(String yaml, ItemResolver resolver) {
        return parse(yaml, "astaps", resolver);
    }

    /** The owning namespace is a property of the loader, never of an individual YAML table. */
    public static YamlLootTables parse(String yaml, String namespace, ItemResolver resolver) {
        return parse(yaml, namespace, resolver, null);
    }

    public static YamlLootTables parse(String yaml, ItemResolver resolver,
                                        ArtifactResolver artifacts) {
        return parse(yaml, "astaps", resolver, artifacts);
    }

    public static YamlLootTables parse(String yaml, String namespace, ItemResolver resolver,
                                        ArtifactResolver artifacts) {
        if (resolver == null) throw new IllegalArgumentException("Missing item resolver");
        var settings = LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(100_000)
                .setCodePointLimit(3_000_000)
                .build();
        Object document = new Load(settings).loadFromString(yaml);
        if (!(document instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Loot YAML root must be a mapping of named lists");
        }
        // Expand parameterized templates before compiling ordinary loot pools.
        // Template references become nested lists, retaining independent rolls.
        var levelled = LevelledLootTemplates.expand(raw, namespace);
        raw = LootTemplates.expand(levelled.tables(), namespace);
        if (raw.size() > MAX_TABLES) throw new IllegalArgumentException("Too many loot tables");
        var budget = new CompileBudget();
        var parsed = new LinkedHashMap<String, Pool>();
        var compiled = new IdentityHashMap<Object, Pool>();
        var visiting = new IdentityHashMap<Object, Boolean>();
        for (var e : raw.entrySet()) {
            if (!(e.getKey() instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Loot table name must be a nonempty string");
            }
            String id = ResourceId.parse(namespace + ".loot." + name).toString();
            parsed.put(id, parseList(e.getValue(), resolver, artifacts, compiled, visiting, budget, name, 0));
        }
        return new YamlLootTables(namespace, parsed, levelled.bindings());
    }

    private static Pool parseList(Object raw, ItemResolver resolver, ArtifactResolver artifacts,
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
                    throw new IllegalArgumentException(at + ": expected {reward, count?, weight?}");
                }
                for (Object key : fields.keySet()) {
                    if (!"reward".equals(key) && !"count".equals(key) && !"weight".equals(key)
                            && !"properties".equals(key)) {
                        throw new IllegalArgumentException(at + ": unknown field: " + key);
                    }
                }
                if (!fields.containsKey("reward")) {
                    throw new IllegalArgumentException(at + ": missing reward field");
                }
                Object target = fields.get("reward");
                Integer rewardId = null;
                ArtifactResolver.Selector artifact = null;
                WeightedItemSelector grade = null;
                WeightedItemSelector element = null;
                Pool nested = null;
                if (target instanceof List<?>) {
                    nested = parseList(target, resolver, artifacts, compiled, visiting, budget, at + ".reward", depth + 1);
                } else if (target instanceof Map<?, ?> single) {
                    // A nested mapping rerolls its reward once for every outer
                    // execution. Its own count controls the quantity per roll.
                    nested = parseList(List.of(single), resolver, artifacts,
                            compiled, visiting, budget, at + ".reward", depth + 1);
                } else if (target != null) {
                    if (!(target instanceof String reference) || reference.matches("[0-9]+")) {
                        throw new IllegalArgumentException(at + ": reward must be a named item: " + target);
                    }
                    if (reference.equals("genshin.avatar_ascension")
                            && fields.containsKey("properties")) {
                        element = compileElements(fields.get("properties"), resolver, at);
                    } else if (reference.startsWith("genshin.artifact.")) {
                        if (artifacts == null) {
                            throw new IllegalArgumentException(at + ": artifact resolver unavailable");
                        }
                        artifact = artifacts.compile(reference, fields.get("properties"));
                    } else if (fields.containsKey("properties")
                            && (reference.startsWith("genshin.avatar_ascension.")
                                || reference.startsWith("genshin.talent_up."))) {
                        grade = compileGrade(reference, fields.get("properties"), resolver, at);
                    } else {
                        int id = resolver.resolve(reference);
                        if (id <= 0 && id != emu.grasscutter.data.ItemIdRegistry.REGIONAL_SIGIL) {
                            throw new IllegalArgumentException(at + ": unknown reward: " + target);
                        }
                        rewardId = id;
                    }
                }
                if (fields.containsKey("properties")
                        && artifact == null && grade == null && element == null) {
                    throw new IllegalArgumentException(at
                            + ": properties require an artifact or advancement-family reward");
                }
                LootCount.Spec count = LootCount.parse(fields.get("count"));
                BigDecimal weight = null;
                Object value = fields.get("weight");
                if (value != null) {
                    if (!(value instanceof Number)
                            || !value.toString().matches("[0-9]+")) {
                        throw new IllegalArgumentException(at + ": weight must be an integer");
                    }
                    weight = new BigDecimal(value.toString());
                    if (weight.signum() < 0 || weight.compareTo(new BigDecimal("1000000000")) > 0) {
                        throw new IllegalArgumentException(at + ": weight outside 0..1000000000");
                    }
                    hasWeight = true;
                } else hasNull = true;
                entries.add(new Entry(rewardId, artifact, grade, element, nested, count, weight));
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
        Pool root = pools.get(resolveName(name));
        if (root == null) throw new IllegalArgumentException("Resolve the loot table level before rolling: " + name);
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
        } else if (entry.artifact != null) {
            // Distinct artifact instances are rolled independently, even for count > 1.
            for (int i = 0; i < count; i++) {
                reward.merge(entry.artifact.select(random), 1, Math::addExact);
            }
        } else if (entry.grade != null) {
            // A weighted rarity choice is made once per entry execution.
            // A count of N then awards N copies of the selected tier, matching
            // the original weighted pool of fixed-grade, fixed-count entries.
            reward.merge(entry.grade.select(random), count, Math::addExact);
        } else if (entry.element != null) {
            for (int i = 0; i < count; i++) {
                reward.merge(entry.element.select(random), 1, Math::addExact);
            }
        } else if (entry.rewardId != null) {
            reward.merge(entry.rewardId, count, Math::addExact);
        }
    }

    public boolean contains(String id) {
        return pools.containsKey(id) || levelBindings.containsKey(id);
    }

    /** Local names resolve within the loader's namespace; qualified IDs stay unchanged. */
    public String resolveName(String reference) {
        String prefix = namespace + ".loot.";
        ResourceId id = ResourceId.parse(reference.startsWith(prefix)
                ? reference : prefix + reference);
        if (!(id.size() >= 3 && id.segment(1).equals("loot")) || !contains(id.toString())) {
            throw new IllegalArgumentException("Unknown loot table: " + reference);
        }
        return id.toString();
    }

    /** Resolve a bound template using the same native level matched by reward CSS. */
    public String resolveName(String reference, Map<String, String> attributes) {
        String name = resolveName(reference);
        var variants = levelBindings.get(name);
        if (variants == null) return name;
        int level;
        try {
            level = Integer.parseInt(attributes.get("level"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Loot template requires an integer level: " + name, e);
        }
        for (var variant : variants) {
            if (level >= variant.min() && level <= variant.max()) return variant.table();
        }
        throw new IllegalArgumentException("No loot template range for " + name + " at level " + level);
    }

    /**
     * Structured reward output for AstaPS settlement. This evaluates a pool
     * only; it never grants items or modifies chest claim state.
     */
    public List<ItemParamData> rollItems(String name, RandomGenerator random) {
        var reward = roll(name, random);
        if (reward.containsKey(emu.grasscutter.data.ItemIdRegistry.REGIONAL_SIGIL)) {
            throw new IllegalArgumentException(
                    "Regional sigil in " + name + " requires reward context");
        }
        return reward.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ItemParamData(e.getKey(), e.getValue()))
                .toList();
    }

    public List<String> names() {
        var generated = new java.util.HashSet<String>();
        levelBindings.values().forEach(variants -> variants.forEach(v -> generated.add(v.table())));
        var names = new ArrayList<String>();
        pools.keySet().stream().filter(name -> !generated.contains(name)).forEach(names::add);
        names.addAll(levelBindings.keySet());
        return List.copyOf(names);
    }
}
