package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSNestedDeclarations;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriterSettings;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.game.drop.LootCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.sf.carte.doc.dom.CSSDOMImplementation;
import io.sf.carte.doc.style.css.CSSElement;
import io.sf.carte.doc.style.css.SelectorMatcher;
import io.sf.carte.doc.style.css.nsac.Selector;
import io.sf.carte.doc.style.css.nsac.SelectorList;
import io.sf.carte.doc.style.css.om.Specificity;
import io.sf.carte.doc.style.css.parser.CSSParser;

/** CSS custom properties reference typed items and named loot tables. */
public final class CssRewards {
    public record Selection(Map<Integer, Integer> items, Map<String, Integer> loot,
            Map<Integer, LootCount.Spec> rangedItems, Map<String, LootCount.Spec> rangedLoot) {
        public Selection(Map<Integer, Integer> items, Map<String, Integer> loot) {
            this(items, loot, Map.of(), Map.of());
        }
        public Selection(Map<Integer, Integer> items, Map<String, Integer> loot,
                         Map<Integer, LootCount.Spec> rangedItems) {
            this(items, loot, rangedItems, Map.of());
        }
        public Selection {
            items = Map.copyOf(items);
            loot = Map.copyOf(loot);
            rangedItems = Map.copyOf(rangedItems);
            rangedLoot = Map.copyOf(rangedLoot);
        }
    }

    // CSS presence selectors, e.g. [data-range-level-70-79] or
    // [data-range-level-93-up]. The stylesheet supplies the intervals.
    private static final Pattern RANGE_MEMBERSHIP_ATTRIBUTE = Pattern.compile(
            "\\[\\s*(data-range-([a-zA-Z_][a-zA-Z0-9_-]*)-([0-9]+)-(up|[0-9]+))\\s*\\]");

    // Dotted resource keys belong to the rewards dialect, while ph-css needs
    // conventional CSS property identifiers. Encode declarations only; leave
    // CSS selectors, values and the canonical resource IDs unchanged.
    private static final Pattern RESOURCE_DECLARATION = Pattern.compile(
            "(?m)(^|[;{}])(\\s*)(BOSS_MATERIAL|ELEMENT|getBossMaterial|[a-z][\\p{L}\\p{N}_.,<>\\[\\]\\s-]*)\\s*:");
    private static final Pattern BINDING = Pattern.compile(
            "<(BOSS_MATERIAL|ELEMENT)>(?:\\[([0-9]+)])?");

    private record RangeMembership(
            String syntheticAttribute, String sourceAttribute,
            java.math.BigInteger min, java.math.BigInteger max) {
        boolean matches(Map<String, String> attributes) {
            String sourceValue = attributes.get(sourceAttribute);
            if (sourceValue == null) return false;
            try {
                var actual = new java.math.BigInteger(sourceValue);
                return actual.compareTo(min) >= 0
                        && (max == null || actual.compareTo(max) <= 0);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }

    private record LootDeclaration(String name, Integer fixed, LootCount.Spec ranged) {}
    private record ItemDeclaration(String name, Integer fixed, LootCount.Spec ranged) {}

    private record Rule(
            Selector selector,
            List<ItemDeclaration> items, List<LootDeclaration> loot,
            List<String> bossMaterials, LootCount.Spec bossMaterialCount, List<String> elements,
            int order) {}

    private final List<Rule> rules;
    private final List<RangeMembership> rangeMemberships;
    private final YamlLootTables loot;
    private final ItemIdRegistry itemRegistry;

    private CssRewards(List<Rule> rules, List<RangeMembership> rangeMemberships,
                       YamlLootTables loot, ItemIdRegistry itemRegistry) {
        this.rules = List.copyOf(rules);
        this.rangeMemberships = List.copyOf(rangeMemberships);
        this.loot = loot;
        this.itemRegistry = itemRegistry;
    }

    public static CssRewards parse(String source, ItemIdRegistry items, YamlLootTables loot) {
        if (source.contains("${")) {
            throw new IllegalArgumentException("Reward curves are no longer supported");
        }
        // Transform resource declarations into CSS-safe temporary properties.
        // Every occurrence gets a distinct temporary key; the original key is
        // recovered before resolving resource identities and cascade rules.
        var originalProperties = new HashMap<String, String>();
        var propertyMatcher = RESOURCE_DECLARATION.matcher(quoteBindings(
                source.replaceAll("(?s)/\\*.*?\\*/", "")));
        var propertyBuffer = new StringBuffer();
        while (propertyMatcher.find()) {
            String surrogate = "--resource-path-" + originalProperties.size();
            originalProperties.put(surrogate, propertyMatcher.group(3));
            propertyMatcher.appendReplacement(propertyBuffer, Matcher.quoteReplacement(
                    propertyMatcher.group(1) + propertyMatcher.group(2) + surrogate + ":"));
        }
        propertyMatcher.appendTail(propertyBuffer);
        // Preserve unquoted count ranges and distributions across CSS tokenization.
        String normalized = propertyBuffer.toString().replaceAll(
                "(--[\\p{L}\\p{N}_-]+\\s*:\\s*)([0-9]+)\\s*~\\s*([0-9]+)(\\s*;)",
                "$1'$2~$3'$4");
        normalized = normalized.replaceAll(
                "(--[\\p{L}\\p{N}_-]+\\s*:\\s*)([0-9]+\\s*:[^;'\"{}]*)(\\s*;)",
                "$1'$2'$3");
        var sheet = CSSReader.readFromString(normalized);
        if (sheet == null) throw new IllegalArgumentException("Invalid rewards.css");

        var rules = new ArrayList<Rule>();
        var intervals = new java.util.LinkedHashMap<String, RangeMembership>();
        int[] sourceOrder = {0};
        for (var top : sheet.getAllRules()) {
            if (!(top instanceof CSSStyleRule style)) {
                throw new IllegalArgumentException("Unsupported top-level reward rule: " + top);
            }
            collectStyle(style, null, rules, intervals, sourceOrder,
                    originalProperties, items, loot);
        }
        return new CssRewards(rules, List.copyOf(intervals.values()), loot, items);
    }

    private static void collectStyle(CSSStyleRule style, SelectorList parent,
            List<Rule> rules, Map<String, RangeMembership> intervals, int[] sourceOrder,
            Map<String, String> originalProperties, ItemIdRegistry items, YamlLootTables loot) {
        var parser = new CSSParser();
        var selectors = parser.parseSelectors(
                style.getSelectorsAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0));
        if (parent != null) {
            // css4j performs the standard nesting substitution, including '&'.
            selectors = selectors.replaceNested(parent);
        }
        collectDeclarations(style.getAllDeclarations(), selectors, rules, intervals,
                sourceOrder, originalProperties, items, loot);
        for (var nested : style.getAllRules()) {
            if (nested instanceof CSSStyleRule child) {
                collectStyle(child, selectors, rules, intervals, sourceOrder,
                        originalProperties, items, loot);
            } else if (nested instanceof CSSNestedDeclarations continuation) {
                collectDeclarations(continuation.getAllDeclarations(), selectors, rules,
                        intervals, sourceOrder, originalProperties, items, loot);
            } else {
                throw new IllegalArgumentException("Unsupported conditional reward rule: " + nested);
            }
        }
    }

    private static void collectDeclarations(List<CSSDeclaration> declarations,
            SelectorList selectors, List<Rule> rules,
            Map<String, RangeMembership> intervals, int[] sourceOrder,
            Map<String, String> originalProperties,
            ItemIdRegistry items, YamlLootTables loot) {
        if (declarations.isEmpty()) return;
        var itemDeclarations = new ArrayList<ItemDeclaration>();
        var lootDeclarations = new ArrayList<LootDeclaration>();
        List<String> bossMaterials = null;
        LootCount.Spec bossMaterialCount = null;
        List<String> elements = null;
        for (var declaration : declarations) {
            String property = declaration.getProperty();
            String value = declaration.getExpressionAsCSSString().trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            String original = originalProperties.getOrDefault(property, property);
            if ("BOSS_MATERIAL".equals(original)) {
                bossMaterials = parseMaterialBinding(value);
                continue;
            }
            if ("ELEMENT".equals(original)) {
                elements = parseElementBinding(value);
                continue;
            }
            if ("getBossMaterial".equals(original)) {
                bossMaterialCount = LootCount.parseRoll(value);
                continue;
            }
            String compact = original.replaceAll("\\s+", "");
            Integer fixedCount = value.matches("[0-9]+") ? Integer.valueOf(value) : null;
            LootCount.Spec rangedCount = fixedCount == null ? LootCount.parseRoll(value) : null;
            if (compact.matches("[a-z][a-z0-9_]*\\.loot\\..*")) {
                String name = BINDING.matcher(compact).find() ? compact : loot.resolveName(compact);
                lootDeclarations.add(new LootDeclaration(name, fixedCount, rangedCount));
            } else {
                if (!BINDING.matcher(compact).find()) items.resolve(compact);
                itemDeclarations.add(new ItemDeclaration(compact, fixedCount, rangedCount));
            }
        }
        for (var selector : selectors) {
            String css = selector.toString();
            var range = RANGE_MEMBERSHIP_ATTRIBUTE.matcher(css);
            while (range.find()) {
                String attribute = range.group(1);
                var min = new java.math.BigInteger(range.group(3));
                var max = "up".equals(range.group(4))
                        ? null : new java.math.BigInteger(range.group(4));
                if (max != null && max.compareTo(min) < 0) {
                    throw new IllegalArgumentException("Reversed reward range attribute: " + css);
                }
                intervals.putIfAbsent(attribute, new RangeMembership(
                        attribute, range.group(2), min, max));
            }
            rules.add(new Rule(selector, List.copyOf(itemDeclarations),
                    List.copyOf(lootDeclarations), bossMaterials,
                    bossMaterialCount, elements, sourceOrder[0]++));
        }
    }

    private static String quoteBindings(String source) {
        var matcher = Pattern.compile(
                "(?m)(^|[;{}])(\\s*)(BOSS_MATERIAL|ELEMENT)\\s*:\\s*([^;{}]+)").matcher(source);
        var buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(4).trim();
            if (value.length() >= 2
                    && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            // Resource names contain no whitespace, quotes, or backslashes.
            value = value.replaceAll("\\s+", "");
            if (value.contains("\"") || value.contains("'") || value.contains("\\")) {
                throw new IllegalArgumentException("Invalid reward binding: " + value);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + matcher.group(3) + ": \"" + value + "\""));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static List<String> parseMaterialBinding(String value) {
        String names = value.replaceAll("\\s+", "");
        if (names.startsWith("[") && names.endsWith("]")) names = names.substring(1, names.length() - 1);
        else if (names.contains(",") || names.contains("[") || names.contains("]")) {
            throw new IllegalArgumentException("Boss material lists require brackets: " + value);
        }
        var bindings = new ArrayList<String>();
        for (String name : names.split(",", -1)) {
            String prefix = "genshin.avatar_ascension.";
            if (name.startsWith(prefix)) name = name.substring(prefix.length());
            ResourceId.parse(prefix + name);
            if (bindings.contains(name)) throw new IllegalArgumentException("Duplicate Boss material: " + name);
            bindings.add(name);
        }
        return List.copyOf(bindings);
    }

    private static List<String> parseElementBinding(String value) {
        String names = value.replaceAll("\\s+", "");
        if (names.startsWith("[")) {
            if (!names.endsWith("]")) throw new IllegalArgumentException("Invalid ELEMENT list: " + value);
            names = names.substring(1, names.length() - 1);
        } else if (names.contains(",") || names.contains("]")) {
            throw new IllegalArgumentException("ELEMENT lists require brackets: " + value);
        }
        var elements = new ArrayList<String>();
        for (String name : names.split(",", -1)) {
            if (!List.of("anemo", "geo", "electro", "dendro", "hydro", "pyro", "cryo", "lunar").contains(name)
                    || elements.contains(name)) {
                throw new IllegalArgumentException("Unknown or duplicate ELEMENT binding: " + name);
            }
            elements.add(name);
        }
        return List.copyOf(elements);
    }

    private static List<String> expandBindings(
            String name, List<String> materials, List<String> elements, boolean table) {
        var matcher = BINDING.matcher(name);
        if (!matcher.find()) return List.of(name);
        List<String> bound = "BOSS_MATERIAL".equals(matcher.group(1)) ? materials : elements;
        if (bound == null) return List.of();
        List<String> choices;
        if (matcher.group(2) != null) {
            int index = Integer.parseInt(matcher.group(2));
            if (index >= bound.size()) throw new IllegalArgumentException("Reward binding index out of range: " + name);
            choices = List.of(bound.get(index));
        } else {
            if ((table || "ELEMENT".equals(matcher.group(1))) && bound.size() != 1) {
                throw new IllegalArgumentException("List binding requires an explicit index: " + name);
            }
            choices = bound;
        }
        var expanded = new ArrayList<String>();
        for (String choice : choices) {
            String next = name.substring(0, matcher.start()) + choice + name.substring(matcher.end());
            expanded.addAll(expandBindings(next, materials, elements, table));
        }
        return expanded;
    }

    public Selection resolve(String type, Map<String, String> attributes) {
        var doc = new CSSDOMImplementation().createDocument(null, "rewards", null);
        var element = (CSSElement) doc.createElement(type);
        doc.getDocumentElement().appendChild(element);
        attributes.forEach(element::setAttribute);
        for (var interval : rangeMemberships) {
            element.removeAttribute(interval.syntheticAttribute());
            if (interval.matches(attributes)) {
                element.setAttribute(interval.syntheticAttribute(), "");
            }
        }
        SelectorMatcher matcher = element.getSelectorMatcher();
        record Match(Rule rule, Specificity specificity) {}
        var matched = new ArrayList<Match>();
        for (var rule : rules) {
            if (matcher.matches(rule.selector())) {
                matched.add(new Match(rule, new Specificity(rule.selector(), matcher)));
            }
        }
        matched.sort(Comparator.comparing(Match::specificity, Specificity::selectorCompare)
                .thenComparingInt(match -> match.rule().order()));
        List<String> bossMaterials = null;
        List<String> elements = null;
        for (var match : matched) {
            var rule = match.rule();
            if (rule.bossMaterials != null) bossMaterials = rule.bossMaterials;
            if (rule.elements != null) elements = rule.elements;
        }
        var fixed = new HashMap<Integer, Integer>();
        var ranged = new HashMap<Integer, LootCount.Spec>();
        var selectedLoot = new HashMap<String, Integer>();
        var rangedLoot = new HashMap<String, LootCount.Spec>();
        for (var match : matched) {
            var rule = match.rule();
            for (var declaration : rule.items) {
                for (String name : expandBindings(declaration.name, bossMaterials, elements, false)) {
                    int id = itemRegistry.resolve(name);
                    if (declaration.fixed != null) {
                        ranged.remove(id);
                        fixed.put(id, declaration.fixed);
                    } else {
                        fixed.remove(id);
                        ranged.put(id, declaration.ranged);
                    }
                }
            }
            for (var declaration : rule.loot) {
                for (String expanded : expandBindings(declaration.name, bossMaterials, elements, true)) {
                    String name = loot.resolveName(expanded);
                    if (declaration.fixed != null) {
                        rangedLoot.remove(name);
                        selectedLoot.put(name, declaration.fixed);
                    } else {
                        selectedLoot.remove(name);
                        rangedLoot.put(name, declaration.ranged);
                    }
                }
            }
            if (rule.bossMaterialCount != null && bossMaterials != null) {
                for (String material : bossMaterials) {
                    int id = itemRegistry.resolve("genshin.avatar_ascension." + material);
                    fixed.remove(id);
                    // Each item samples separately in RewardRoller, including an explicit zero.
                    ranged.put(id, rule.bossMaterialCount);
                }
            }
        }
        return new Selection(fixed, selectedLoot, ranged, rangedLoot);
    }
}
