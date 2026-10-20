package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSStyleRule;
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
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

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

    // A type (or *) followed by attribute filters is the only supported
    // selector syntax. Do not silently miscalculate priority for other CSS.
    private static final Pattern SIMPLE_SELECTOR = Pattern.compile(
            "^(?:\\*|[a-zA-Z][a-zA-Z0-9_-]*)(?:\\[[^\\[\\]]+\\])*$");
    // Inclusive numeric ranges work on any attribute; "93~" has no upper bound.
    private static final Pattern ATTRIBUTE_RANGE = Pattern.compile(
            "\\[\\s*([a-zA-Z_][a-zA-Z0-9_-]*)\\s*=\\s*([\"'])([0-9]+)~([0-9]*)\\2\\s*\\]");
    private static final Pattern MALFORMED_ATTRIBUTE_RANGE = Pattern.compile(
            "\\[\\s*([a-zA-Z_][a-zA-Z0-9_-]*)\\s*=\\s*([\"'])[^\"']*~[^\"']*\\2\\s*\\]");

    // Dotted resource keys belong to the rewards dialect, while ph-css needs
    // conventional CSS property identifiers. Encode declarations only; leave
    // CSS selectors, values and the canonical resource IDs unchanged.
    private static final Pattern RESOURCE_DECLARATION = Pattern.compile(
            "(?m)(^|[;{])(\\s*)(BOSS_MATERIAL|ELEMENT|getBossMaterial|[a-z][\\p{L}\\p{N}_.,<>\\[\\]\\s-]*)\\s*:");
    private static final Pattern BINDING = Pattern.compile(
            "<(BOSS_MATERIAL|ELEMENT)>(?:\\[([0-9]+)])?");

    private record AttributeRange(String name, long min, Long max) {
        boolean matches(Map<String, String> attributes) {
            String value = attributes.get(name);
            if (value == null) return false;
            try {
                long actual = Long.parseLong(value);
                return actual >= min && (max == null || actual <= max);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    // Match comma-separated alternatives by exact attribute value.
    private static final Pattern ATTRIBUTE_OPTIONS = Pattern.compile(
            "\\[\\s*([a-zA-Z_][a-zA-Z0-9_-]*)\\s*=\\s*([\"'])([^\"']*,[^\"']*)\\2\\s*\\]");

    private record AttributeOptions(String name, java.util.Set<String> values) {
        boolean matches(Map<String, String> attributes) {
            return values.contains(attributes.get(name));
        }
    }

    private record LootDeclaration(String name, Integer fixed, LootCount.Spec ranged) {}
    private record ItemDeclaration(String name, Integer fixed, LootCount.Spec ranged) {}

    private record Rule(
            Evaluator selector, List<AttributeRange> attributeRanges,
            List<AttributeOptions> attributeOptions,
            List<ItemDeclaration> items, List<LootDeclaration> loot,
            List<String> bossMaterials, LootCount.Spec bossMaterialCount, List<String> elements,
            int specificity, int order) {}

    private final List<Rule> rules;
    private final YamlLootTables loot;
    private final ItemIdRegistry itemRegistry;

    private CssRewards(List<Rule> rules, YamlLootTables loot, ItemIdRegistry itemRegistry) {
        this.rules = List.copyOf(rules);
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
        int order = 0;
        for (var top : sheet.getAllRules()) {
            if (!(top instanceof CSSStyleRule style) || !style.getAllRules().isEmpty()) {
                throw new IllegalArgumentException("Expected CSS style rules");
            }

            var itemDeclarations = new ArrayList<ItemDeclaration>();
            var lootDeclarations = new ArrayList<LootDeclaration>();
            List<String> bossMaterials = null;
            LootCount.Spec bossMaterialCount = null;
            List<String> elements = null;
            for (var declaration : style.getAllDeclarations()) {
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

            for (var selector : style.getAllSelectors()) {
                String css = selector.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                var attributeRanges = new ArrayList<AttributeRange>();
                var selectorBuffer = new StringBuffer();
                var matcher = ATTRIBUTE_RANGE.matcher(css);
                while (matcher.find()) {
                    String name = matcher.group(1);
                    long min = Long.parseLong(matcher.group(3));
                    Long max = matcher.group(4).isEmpty()
                            ? null : Long.parseLong(matcher.group(4));
                    if (max != null && max < min) {
                        throw new IllegalArgumentException("Reversed reward attribute range: " + css);
                    }
                    attributeRanges.add(new AttributeRange(name, min, max));
                    matcher.appendReplacement(selectorBuffer, "[" + name + "]");
                }
                matcher.appendTail(selectorBuffer);
                var options = new ArrayList<AttributeOptions>();
                String withRanges = selectorBuffer.toString();
                selectorBuffer = new StringBuffer();
                var optionsMatcher = ATTRIBUTE_OPTIONS.matcher(withRanges);
                while (optionsMatcher.find()) {
                    String name = optionsMatcher.group(1);
                    var choices = java.util.Arrays.stream(optionsMatcher.group(3).split(",", -1))
                            .map(String::trim).toList();
                    if (choices.stream().anyMatch(String::isEmpty)) {
                        throw new IllegalArgumentException("Empty reward attribute choice: " + css);
                    }
                    options.add(new AttributeOptions(name, java.util.Set.copyOf(choices)));
                    optionsMatcher.appendReplacement(selectorBuffer, "[" + name + "]");
                }
                optionsMatcher.appendTail(selectorBuffer);
                String normalizedSelector = selectorBuffer.toString();
                if (MALFORMED_ATTRIBUTE_RANGE.matcher(normalizedSelector).find()) {
                    throw new IllegalArgumentException("Invalid reward attribute range: " + css);
                }
                if (!SIMPLE_SELECTOR.matcher(normalizedSelector).matches()) {
                    throw new IllegalArgumentException("Unsupported reward selector: " + css);
                }
                int attributeCount = (int) normalizedSelector.chars()
                        .filter(ch -> ch == '[').count();
                // Within our supported type-and-attribute subset, a type selector
                // outranks a universal selector when attribute counts are equal.
                int specificity = attributeCount * 100
                        + (css.matches("^[a-zA-Z][a-zA-Z0-9_-]*.*") ? 1 : 0);
                rules.add(new Rule(Selector.evaluatorOf(normalizedSelector),
                        List.copyOf(attributeRanges), List.copyOf(options), List.copyOf(itemDeclarations),
                        List.copyOf(lootDeclarations),
                        bossMaterials, bossMaterialCount, elements,
                        specificity, order++));
            }
        }

        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));
        return new CssRewards(rules, loot, items);
    }

    /** Preserve resource and element bindings across CSS tokenization. */
    private static String quoteBindings(String source) {
        var matcher = Pattern.compile(
                "(?m)(^|[;{])(\\s*)(BOSS_MATERIAL|ELEMENT)\\s*:\\s*([^;{}]+)").matcher(source);
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
        // Retain the previous variadic spelling for existing configurations.
        if (table && name.endsWith("<ELEMENT>") && !name.endsWith("<<ELEMENT>>")) {
            if (elements == null) return List.of();
            return List.of(name.substring(0, name.length() - 9) + "<" + String.join(",", elements) + ">");
        }
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
        var element = new Element(type);
        attributes.forEach(element::attr);
        var matched = new ArrayList<Rule>();
        List<String> bossMaterials = null;
        List<String> elements = null;
        // Resolve bindings independently of the count rules' position/specificity.
        for (var rule : rules) {
            if (rule.selector.matches(element.root(), element)
                    && rule.attributeRanges.stream().allMatch(range -> range.matches(attributes))
                    && rule.attributeOptions.stream().allMatch(option -> option.matches(attributes))) {
                matched.add(rule);
                if (rule.bossMaterials != null) bossMaterials = rule.bossMaterials;
                if (rule.elements != null) elements = rule.elements;
            }
        }
        var fixed = new HashMap<Integer, Integer>();
        var ranged = new HashMap<Integer, LootCount.Spec>();
        var selectedLoot = new HashMap<String, Integer>();
        var rangedLoot = new HashMap<String, LootCount.Spec>();
        for (var rule : matched) {
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
