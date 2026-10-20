package emu.grasscutter.game.drop;

import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.common.ItemParamData;
import java.math.BigDecimal;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
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

    private record TypeTarget(Integer itemId, String table,
                              ArtifactResolver.Selector artifact,
                              WeightedChoice<Integer> grade,
                              WeightedChoice<Integer> element) {}

    private record Entry(Integer rewardId, ArtifactResolver.Selector artifact,
                         WeightedChoice<Integer> grade, WeightedChoice<Integer> element,
                         WeightedChoice<TypeTarget> typeChoice,
                         Pool nested, LootCount.Spec count,
                         LootRecursionSafety.Fraction expectedCount, BigDecimal weight) {}

    /**
     * An advancement family resolves one native item per rarity. Rarity choices
     * use relative weights; weights on outer entries are independent.
     * A chosen grade applies to the entire count of that entry execution.
     */
    private static WeightedChoice<Integer> compileGrade(
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
        return WeightedChoice.compile(rarity, location + ".rarity", raw -> {
            String name = raw.toString();
            if (!name.matches("[0-9]+")) {
                throw new IllegalArgumentException(location + ": invalid rarity: " + name);
            }
            int grade = Integer.parseInt(name);
            if (grade < 2 || grade > maxGrade) {
                throw new IllegalArgumentException(location + ": invalid rarity: " + grade);
            }
            int itemId = resolver.resolve(family + "." + grade);
            if (itemId <= 0) {
                throw new IllegalArgumentException(location
                        + ": unknown advancement family or rarity: " + family + "." + grade);
            }
            return itemId;
        });
    }

    /** Each gem copy independently evaluates both the element and rarity fields. */
    private static WeightedChoice<Integer> compileElements(
            Object rawProperties, ItemResolver resolver, String location) {
        if (!(rawProperties instanceof Map<?, ?> props)
                || props.size() != 2 || !props.containsKey("element") || !props.containsKey("rarity")) {
            throw new IllegalArgumentException(location
                    + ": elemental ascension gems require element and rarity fields");
        }
        var elements = WeightedChoice.compile(props.get("element"), location + ".element", raw -> {
            String name = raw.toString();
            if (!List.of("anemo", "geo", "electro", "dendro",
                    "hydro", "pyro", "cryo").contains(name)) {
                throw new IllegalArgumentException(location + ": invalid element: " + name);
            }
            return name;
        });
        var rarities = WeightedChoice.compile(props.get("rarity"), location + ".rarity", raw -> {
            String name = raw.toString();
            if (!name.matches("[2-5]")) {
                throw new IllegalArgumentException(location + ": invalid gem rarity: " + name);
            }
            return Integer.parseInt(name);
        });
        var items = new ArrayList<Integer>();
        var weights = new ArrayList<BigDecimal>();
        for (int i = 0; i < elements.candidates().size(); i++) {
            String element = elements.candidates().get(i);
            for (int j = 0; j < rarities.candidates().size(); j++) {
                int rarity = rarities.candidates().get(j);
                int id = resolver.resolve("genshin.avatar_ascension." + element + "." + rarity);
                if (id <= 0) {
                    throw new IllegalArgumentException(location
                            + ": unknown ascension gem: " + element + "." + rarity);
                }
                items.add(id);
                weights.add(elements.weights().get(i).multiply(rarities.weights().get(j)));
            }
        }
        return WeightedChoice.of(items, weights, location + ".element/rarity");
    }

    /** Resolve a talent subject and its grade into a native item once per entry roll. */
    private static WeightedChoice<Integer> compileTalentSeries(
            Map<?, ?> properties, ItemResolver resolver, String location) {
        if (properties == null || properties.size() != 2
                || !properties.containsKey("series") || !properties.containsKey("rarity")) {
            throw new IllegalArgumentException(location
                    + ": talent books require series and rarity attributes");
        }
        var series = WeightedChoice.compile(properties.get("series"),
                location + ".series", raw -> raw.toString());
        var rarities = WeightedChoice.compile(properties.get("rarity"),
                location + ".rarity", raw -> {
                    String value = raw.toString();
                    if (!value.matches("[234]")) {
                        throw new IllegalArgumentException(location + ": invalid talent rarity: " + value);
                    }
                    return Integer.parseInt(value);
                });
        var items = new ArrayList<Integer>();
        var weights = new ArrayList<BigDecimal>();
        for (int i = 0; i < series.candidates().size(); i++) {
            for (int j = 0; j < rarities.candidates().size(); j++) {
                String name = "genshin.talent_up." + series.candidates().get(i)
                        + "." + rarities.candidates().get(j);
                int id = resolver.resolve(name);
                if (id <= 0) {
                    throw new IllegalArgumentException(location + ": unknown talent book: " + name);
                }
                items.add(id);
                weights.add(series.weights().get(i).multiply(rarities.weights().get(j)));
            }
        }
        return WeightedChoice.of(items, weights, location + ".series/rarity");
    }

    /** Metadata is compiled by its owning reward type; all fields use WeightedChoice. */
    private static final Set<String> CORE_FIELDS =
            Set.of("reward", "type", "count", "weight", "properties");

    private static Map<?, ?> entryProperties(Map<?, ?> fields, String location) {
        Object nested = fields.get("properties");
        if (nested != null && !(nested instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(location + ": properties must be a mapping");
        }
        var merged = new LinkedHashMap<String, Object>();
        if (nested instanceof Map<?, ?> props) {
            for (var prop : props.entrySet()) {
                if (!(prop.getKey() instanceof String name)) {
                    throw new IllegalArgumentException(location + ": property name must be a string");
                }
                merged.put(name, prop.getValue());
            }
        }
        for (var field : fields.entrySet()) {
            if (!(field.getKey() instanceof String key)) {
                throw new IllegalArgumentException(location + ": field name must be a string");
            }
            if (CORE_FIELDS.contains(key)) continue;
            if (merged.containsKey(key)) {
                throw new IllegalArgumentException(location + ": duplicate property: " + key);
            }
            merged.put(key, field.getValue());
        }
        return merged.isEmpty() ? null : merged;
    }

    /**
     * A YAML single-key mapping may be a weighted branch whose key is an
     * entire entry or sequence. Convert it to the same pool entry model.
     */
    private static Map<?, ?> weightedBranch(Map<?, ?> value) {
        if (value.size() != 1 || value.containsKey("type") || value.containsKey("reward")) {
            return value;
        }
        var only = value.entrySet().iterator().next();
        if (!(only.getValue() instanceof Number)) return value;
        var result = new LinkedHashMap<String, Object>();
        result.put("type", only.getKey());
        result.put("weight", only.getValue());
        return result;
    }

    private static String qualifiedTable(String name, String namespace) {
        return name.startsWith(namespace + ".loot.") ? name : namespace + ".loot." + name;
    }

    private static String namespaceOf(String qualifiedName) {
        return qualifiedName.substring(0, qualifiedName.indexOf(".loot."));
    }

    private static TypeTarget typeTarget(String name, ItemResolver resolver,
            Set<String> tableNames, String namespace, String location,
            Map<?, ?> properties, ArtifactResolver artifacts,
            LootTemplates templates) {
        if ("null".equals(name)) {
            if (properties != null) {
                throw new IllegalArgumentException(location + ": null reward cannot have attributes");
            }
            return new TypeTarget(null, null, null, null, null);
        }
        String qualified = qualifiedTable(name, namespace);
        if (tableNames.contains(qualified)) {
            if (properties != null) {
                throw new IllegalArgumentException(location
                        + ": reward table does not accept item attributes: " + name);
            }
            if (resolver.resolve(name) > 0) {
                throw new IllegalArgumentException(location + ": ambiguous item/table name: " + name);
            }
            return new TypeTarget(null, qualified, null, null, null);
        }
        if (templates.isTemplateReference(name)) {
            if (properties != null) {
                throw new IllegalArgumentException(location + ": template invocation cannot have item attributes");
            }
            return new TypeTarget(null, templates.qualify(name), null, null, null);
        }
        if (name.equals("genshin.avatar_ascension") && properties != null) {
            // One material variant is chosen, then count awards copies of it.
            return new TypeTarget(null, null, null,
                    compileElements(properties, resolver, location), null);
        }
        if (name.equals("genshin.talent_up") && properties != null) {
            return new TypeTarget(null, null, null,
                    compileTalentSeries(properties, resolver, location), null);
        }
        if (name.equals("genshin.artifact") || name.startsWith("genshin.artifact.")) {
            if (artifacts == null) {
                throw new IllegalArgumentException(location + ": artifact resolver unavailable");
            }
            return new TypeTarget(null, null, artifacts.compile(name, properties), null, null);
        }
        if (properties != null && (name.startsWith("genshin.avatar_ascension.")
                || name.startsWith("genshin.talent_up."))) {
            return new TypeTarget(null, null, null,
                    compileGrade(name, properties, resolver, location), null);
        }
        if (properties != null) {
            throw new IllegalArgumentException(location + ": unsupported type attributes: " + name);
        }
        int id = resolver.resolve(name);
        if (id <= 0 && id != emu.grasscutter.data.ItemIdRegistry.REGIONAL_SIGIL) {
            throw new IllegalArgumentException(location + ": unknown type: " + name);
        }
        return new TypeTarget(id, null, null, null, null);
    }

    /** Validate table names without rejecting recursive reference graphs. */
    private static void validateReferences(Map<String, Pool> pools) {
        var visited = new IdentityHashMap<Pool, Boolean>();
        for (Pool pool : pools.values()) checkPool(pool, pools, visited);
    }

    private static void checkPool(Pool pool, Map<String, Pool> pools,
            IdentityHashMap<Pool, Boolean> visited) {
        if (visited.putIfAbsent(pool, true) != null) return;
        for (Entry entry : pool.entries) {
            if (entry.nested != null) checkPool(entry.nested, pools, visited);
            if (entry.typeChoice != null) {
                for (TypeTarget candidate : entry.typeChoice.candidates()) {
                    if (candidate.table != null && !candidate.table.contains("<")) {
                        checkPool(requireTable(pools, candidate.table), pools, visited);
                    }
                }
            }
        }
    }

    private static Pool requireTable(Map<String, Pool> pools, String name) {
        Pool pool = pools.get(name);
        if (pool == null) throw new IllegalArgumentException("Unknown named loot table: " + name);
        return pool;
    }

    private void emitType(TypeTarget selected, int count, RandomGenerator random,
            Map<Integer, Integer> reward) {
        if (selected.table != null) {
            Pool table = pools.get(selected.table);
            if (table == null) table = instances.get(selected.table);
            if (table == null && templates.isTemplateReference(selected.table)) {
                String key = instantiate(selected.table);
                table = instances.get(key);
            }
            if (table == null) throw new IllegalStateException("Unknown named loot table: " + selected.table);
            rollPool(table, count, random, reward);
        } else if (selected.artifact != null) {
            var artifact = selected.artifact.bindSet(random);
            for (int i = 0; i < count; i++) {
                reward.merge(artifact.select(random), 1, Math::addExact);
            }
        } else if (selected.grade != null) {
            reward.merge(selected.grade.sample(random), count, Math::addExact);
        } else if (selected.element != null) {
            for (int i = 0; i < count; i++) {
                reward.merge(selected.element.sample(random), 1, Math::addExact);
            }
        } else if (selected.itemId != null) {
            reward.merge(selected.itemId, count, Math::addExact);
        }
    }

    /** A placeholder allows recursive YAML aliases to refer to a pool while it compiles. */
    private static final class Pool {
        List<Entry> entries;
        WeightedChoice<Entry> weightedChoice;

        void initialize(List<Entry> entries, WeightedChoice<Entry> weightedChoice) {
            this.entries = entries;
            this.weightedChoice = weightedChoice;
        }
    }

    private final Map<String, Pool> pools;
    private final Map<String, Pool> instances = new java.util.concurrent.ConcurrentHashMap<>();
    private final LootTemplates templates;
    private final ItemResolver resolver;
    private final ArtifactResolver artifacts;
    private YamlLootTables(Map<String, Pool> pools,
            LootTemplates templates, ItemResolver resolver, ArtifactResolver artifacts) {
        this.templates = templates;
        this.resolver = resolver;
        this.artifacts = artifacts;
        this.pools = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pools));
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
                // Weighted branches allow sequence and mapping keys.
                .setAllowNonScalarKeys(true)
                .setAllowDuplicateKeys(false)
                // API values are integers. Avoid imposing lower application limits.
                .setMaxAliasesForCollections(Integer.MAX_VALUE)
                .setCodePointLimit(Integer.MAX_VALUE)
                .build();
        Object document = LootYamlSyntax.parse(yaml, settings);
        if (!(document instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Loot YAML root must be a mapping of named lists");
        }
        // Expand parameterized templates before compiling ordinary loot pools.
        // Template references become nested lists, retaining independent rolls.
        var prepared = LootTemplates.prepare(raw, namespace);
        raw = prepared.tables();
        var parsed = new LinkedHashMap<String, Pool>();
        var compiled = new IdentityHashMap<Object, Pool>();
        var names = new java.util.HashSet<String>();
        for (var key : raw.keySet()) {
            if (!(key instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Loot table name must be a nonempty string");
            }
            names.add(ResourceId.parse(namespace + ".loot." + name).toString());
        }
        for (var e : raw.entrySet()) {
            String name = (String) e.getKey();
            String id = ResourceId.parse(namespace + ".loot." + name).toString();
            parsed.put(id, parseList(e.getValue(), resolver, artifacts, compiled,
                    name, names, namespace, prepared.templates()));
        }
        validateReferences(parsed);
        var result = new YamlLootTables(parsed, prepared.templates(), resolver, artifacts);
        result.validateExpectedNodes();
        return result;
    }

    private static Pool parseList(Object raw, ItemResolver resolver, ArtifactResolver artifacts,
            IdentityHashMap<Object, Pool> compiled, String location,
            Set<String> tableNames, String namespace, LootTemplates templates) {
        Pool cached = compiled.get(raw);
        if (cached != null) return cached;
        if (!(raw instanceof Map<?, ?> || raw instanceof List<?>)) {
            throw new IllegalArgumentException(location + ": expected a YAML list or expression");
        }
        var pending = new Pool();
        compiled.put(raw, pending);
        Object source = raw;
        if (raw instanceof Map<?, ?> single) raw = List.of(single);
        List<?> list = (List<?>) raw;
        try {
            var entries = new ArrayList<Entry>();
            boolean hasWeight = false, hasNull = false;
            for (int i = 0; i < list.size(); i++) {
                String at = location + "[" + i + "]";
                if (!(list.get(i) instanceof Map<?, ?> mapping)) {
                    throw new IllegalArgumentException(at + ": expected {type, count?} or weighted branch");
                }
                Map<?, ?> fields = weightedBranch(mapping);
                // Unknown non-core fields are typed attributes. Their owning
                // item compiler validates the names and candidate values.
                if (fields.containsKey("reward") == fields.containsKey("type")) {
                    throw new IllegalArgumentException(at + ": exactly one of type or reward is required");
                }
                boolean useType = fields.containsKey("type");
                Object target = fields.get(useType ? "type" : "reward");
                Map<?, ?> properties = entryProperties(fields, at);
                Integer rewardId = null;
                ArtifactResolver.Selector artifact = null;
                WeightedChoice<Integer> grade = null;
                WeightedChoice<Integer> element = null;
                WeightedChoice<TypeTarget> typeChoice = null;
                Pool nested = null;
                if (target instanceof List<?>) {
                    nested = parseList(target, resolver, artifacts, compiled,
                            at + ".type", tableNames, namespace, templates);
                } else if (target instanceof Map<?, ?> single) {
                    // A nested mapping rerolls its reward once for every outer
                    // execution. Its own count controls the quantity per roll.
                    nested = parseList(single, resolver, artifacts,
                            compiled, at + ".type", tableNames, namespace, templates);
                } else if (target != null) {
                    if (!(target instanceof String reference) || reference.matches("[0-9]+")) {
                        throw new IllegalArgumentException(at + ": type must be a named item: " + target);
                    }
                    if (useType) {
                        typeChoice = WeightedChoice.compile(reference, at + ".type",
                                candidate -> typeTarget(candidate.toString(), resolver,
                                        tableNames, namespace, at, properties, artifacts, templates));
                    } else if (reference.equals("genshin.avatar_ascension")
                            && properties != null) {
                        element = compileElements(properties, resolver, at);
                    } else if (reference.startsWith("genshin.artifact.")) {
                        if (artifacts == null) {
                            throw new IllegalArgumentException(at + ": artifact resolver unavailable");
                        }
                        artifact = artifacts.compile(reference, properties);
                    } else if (properties != null
                            && (reference.startsWith("genshin.avatar_ascension.")
                                || reference.startsWith("genshin.talent_up."))) {
                        grade = compileGrade(reference, properties, resolver, at);
                    } else {
                        int id = resolver.resolve(reference);
                        if (id <= 0 && id != emu.grasscutter.data.ItemIdRegistry.REGIONAL_SIGIL) {
                            throw new IllegalArgumentException(at + ": unknown reward: " + target);
                        }
                        rewardId = id;
                    }
                }
                if (properties != null && typeChoice == null
                        && artifact == null && grade == null && element == null) {
                    throw new IllegalArgumentException(at
                            + ": properties require an artifact or advancement-family reward");
                }
                LootCount.Spec count = LootCount.parse(fields.get("count"));
                BigDecimal weight = null;
                Object value = fields.get("weight");
                if (value != null) {
                    weight = WeightedChoice.weight(value, at);
                    hasWeight = true;
                } else hasNull = true;
                entries.add(new Entry(rewardId, artifact, grade, element, typeChoice,
                        nested, count, LootRecursionSafety.expectedCount(fields.get("count")), weight));
            }
            if (hasWeight && hasNull) {
                throw new IllegalArgumentException(location + ": cannot mix null and numeric weights");
            }
            WeightedChoice<Entry> choice = hasWeight
                    ? WeightedChoice.of(entries, entries.stream().map(Entry::weight).toList(), location)
                    : null;
            pending.initialize(List.copyOf(entries), choice);
            return pending;
        } catch (RuntimeException | Error failure) {
            compiled.remove(source);
            throw failure;
        }
    }

    /**
     * Build the full pool-invocation graph, including aliases and instantiated
     * templates. Item count contributes only when the target is another pool.
     */
    private void validateExpectedNodes() {
        var graph = new LootRecursionSafety.Graph();
        var pending = new ArrayDeque<Pool>();
        var seen = new IdentityHashMap<Pool, Boolean>();
        for (var table : pools.entrySet()) {
            graph.root(table.getKey(), table.getValue());
            pending.add(table.getValue());
        }
        for (var table : instances.entrySet()) {
            graph.root(table.getKey(), table.getValue());
            pending.add(table.getValue());
        }
        while (!pending.isEmpty()) {
            Pool pool = pending.removeFirst();
            if (seen.putIfAbsent(pool, true) != null) continue;
            var from = graph.node(pool, "nested loot pool");
            if (pool.weightedChoice == null) {
                for (Entry entry : pool.entries) {
                    includePoolCalls(graph, from, entry,
                            LootRecursionSafety.Fraction.ONE, pending);
                }
            } else {
                var choice = pool.weightedChoice;
                LootRecursionSafety.Fraction total = LootRecursionSafety.Fraction.ZERO;
                for (BigDecimal weight : choice.weights()) {
                    total = total.add(LootRecursionSafety.Fraction.decimal(weight));
                }
                for (int i = 0; i < choice.candidates().size(); i++) {
                    var weight = LootRecursionSafety.Fraction.decimal(choice.weights().get(i));
                    includePoolCalls(graph, from, choice.candidates().get(i),
                            weight.divide(total), pending);
                }
            }
        }
        graph.verify();
    }

    private void includePoolCalls(LootRecursionSafety.Graph graph,
            LootRecursionSafety.Graph.Node from, Entry entry,
            LootRecursionSafety.Fraction probability, ArrayDeque<Pool> pending) {
        var count = probability.multiply(entry.expectedCount);
        if (count.isZero()) return;
        if (entry.nested != null) {
            graph.edge(from, graph.node(entry.nested, "nested loot pool"), count);
            pending.add(entry.nested);
        }
        if (entry.typeChoice == null) return;
        var choice = entry.typeChoice;
        LootRecursionSafety.Fraction total = LootRecursionSafety.Fraction.ZERO;
        for (BigDecimal weight : choice.weights()) {
            total = total.add(LootRecursionSafety.Fraction.decimal(weight));
        }
        for (int i = 0; i < choice.candidates().size(); i++) {
            String tableName = choice.candidates().get(i).table;
            if (tableName == null) continue;
            Pool child = pools.get(tableName);
            if (child == null) child = instances.get(tableName);
            if (child == null && templates.isTemplateReference(tableName)) {
                instantiateUnchecked(tableName);
                child = instances.get(tableName);
                graph.root(tableName, child);
            }
            if (child == null) {
                throw new IllegalArgumentException("Unknown named loot table: " + tableName);
            }
            graph.edge(from, graph.node(child, tableName),
                    count.multiply(LootRecursionSafety.Fraction.decimal(
                            choice.weights().get(i))).divide(total));
            pending.add(child);
        }
    }

    /** Returns an immutable item-ID-to-count map. Each invocation rolls afresh. */
    public Map<Integer, Integer> roll(String name, RandomGenerator random) {
        String resolved = resolveName(name);
        Pool root = pools.containsKey(resolved) ? pools.get(resolved) : instances.get(resolved);
        if (random == null) throw new IllegalArgumentException("Missing random generator");
        var reward = new TreeMap<Integer, Integer>();
        rollPool(root, 1, random, reward);
        return Map.copyOf(reward);
    }

    private void rollPool(Pool pool, int times, RandomGenerator random,
            Map<Integer, Integer> reward) {
        for (int n = 0; n < times; n++) {
            if (pool.weightedChoice != null) {
                rollEntry(pool.weightedChoice.sample(random), random, reward);
            } else {
                for (Entry e : pool.entries) rollEntry(e, random, reward);
            }
        }
    }

    private void rollEntry(Entry entry, RandomGenerator random,
            Map<Integer, Integer> reward) {
        int count = entry.count.sample(random);
        if (count == 0) return;
        if (entry.nested != null) {
            rollPool(entry.nested, count, random, reward);
        } else if (entry.artifact != null) {
            // Select a set once; roll native pieces independently for each copy.
            var artifact = entry.artifact.bindSet(random);
            for (int i = 0; i < count; i++) {
                reward.merge(artifact.select(random), 1, Math::addExact);
            }
        } else if (entry.grade != null) {
            // A weighted rarity choice is made once per entry execution.
            // A count of N then awards N copies of the selected tier, matching
            // the original weighted pool of fixed-grade, fixed-count entries.
            reward.merge(entry.grade.sample(random), count, Math::addExact);
        } else if (entry.element != null) {
            for (int i = 0; i < count; i++) {
                reward.merge(entry.element.sample(random), 1, Math::addExact);
            }
        } else if (entry.typeChoice != null) {
            emitType(entry.typeChoice.sample(random), count, random, reward);
        } else if (entry.rewardId != null) {
            reward.merge(entry.rewardId, count, Math::addExact);
        }
    }

    public boolean contains(String id) {
        return pools.containsKey(id) || instances.containsKey(id);
    }

    /** Local names resolve within the loader's namespace; qualified IDs stay unchanged. */
    public String resolveName(String reference) {
        String name = templates.qualify(reference);
        if (contains(name)) return name;
        if (name.contains("<")) return instantiate(name);
        throw new IllegalArgumentException("Unknown loot table: " + reference);
    }

    /** CSS references instantiate item-parameterized tables while loading configuration. */
    private synchronized String instantiate(String name) {
        if (!instances.containsKey(name)) {
            instantiateUnchecked(name);
            validateExpectedNodes();
        }
        return name;
    }

    /** Used by graph construction; validation runs after all references materialize. */
    private void instantiateUnchecked(String name) {
        if (instances.containsKey(name)) return;
        var entries = templates.instantiateReference(name);
        var pool = parseList(entries, resolver, artifacts, new IdentityHashMap<>(),
                name, pools.keySet(), namespaceOf(name), templates);
        instances.put(name, pool);
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
        var names = new ArrayList<>(pools.keySet());
        instances.keySet().stream().sorted().forEach(names::add);
        return List.copyOf(names);
    }
}
