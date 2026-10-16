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

    private record Rule(
            Evaluator selector, List<AttributeRange> attributeRanges,
            List<AttributeOptions> attributeOptions,
            Map<Integer, Integer> items, Map<String, Integer> loot,
            Map<Integer, LootCount.Spec> rangedItems,
            Map<String, LootCount.Spec> rangedLoot, int specificity, int order) {}

    private final List<Rule> rules;

    private CssRewards(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static CssRewards parse(String source, ItemIdRegistry items, YamlLootTables loot) {
        // Preserve unquoted count ranges and distributions across CSS tokenization.
        String normalized = source.replaceAll(
                "(--(?:genshin-(?:currency|experience|avatar_exp|avatar_ascension|weapon_exp|artifact_exp|monster_drop|material|food|furnishing|weapon)|astaps-loot)-[\\p{L}\\p{N}_-]+\\s*:\\s*)([0-9]+)\\s*~\\s*([0-9]+)(\\s*;)",
                "$1'$2~$3'$4");
        normalized = normalized.replaceAll(
                "(--astaps-loot-[\\p{L}\\p{N}_-]+\\s*:\\s*)([0-9]+\\s*:[^;'\\\"{}]*)(\\s*;)",
                "$1'$2'$3");
        var sheet = CSSReader.readFromString(normalized);
        if (sheet == null) throw new IllegalArgumentException("Invalid rewards.css");

        var rules = new ArrayList<Rule>();
        int order = 0;
        for (var top : sheet.getAllRules()) {
            if (!(top instanceof CSSStyleRule style) || !style.getAllRules().isEmpty()) {
                throw new IllegalArgumentException("Expected CSS style rules");
            }

            var fixed = new HashMap<Integer, Integer>();
            var ranged = new HashMap<Integer, LootCount.Spec>();
            var selectedLoot = new HashMap<String, Integer>();
            var rangedLoot = new HashMap<String, LootCount.Spec>();
            for (var declaration : style.getAllDeclarations()) {
                String property = declaration.getProperty();
                String value = declaration.getExpressionAsCSSString().trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                ResourceId resource = ResourceId.fromCssProperty(property);
                if ("loot".equals(resource.type())) {
                    String name = loot.resolveName(resource.toString());
                    if (value.matches("[0-9]+")) {
                        selectedLoot.put(name, Integer.parseInt(value));
                        rangedLoot.remove(name);
                    } else {
                        rangedLoot.put(name, LootCount.parse(value));
                        selectedLoot.remove(name);
                    }
                } else {
                    int id = items.resolve(resource.toString());
                    if (value.matches("[0-9]+")) {
                        fixed.put(id, Integer.parseInt(value));
                        ranged.remove(id);
                    } else {
                        ranged.put(id, LootCount.parse(value.replaceAll("\\s+", "")));
                        fixed.remove(id);
                    }
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
                        List.copyOf(attributeRanges), List.copyOf(options), Map.copyOf(fixed),
                        Map.copyOf(selectedLoot), Map.copyOf(ranged),
                        Map.copyOf(rangedLoot), specificity, order++));
            }
        }

        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));
        return new CssRewards(rules);
    }

    public Selection resolve(String type, Map<String, String> attributes) {
        var element = new Element(type);
        attributes.forEach(element::attr);
        var fixed = new HashMap<Integer, Integer>();
        var ranged = new HashMap<Integer, LootCount.Spec>();
        var selectedLoot = new HashMap<String, Integer>();
        var rangedLoot = new HashMap<String, LootCount.Spec>();
        for (var rule : rules) {
            if (rule.selector.matches(element.root(), element)
                    && rule.attributeRanges.stream().allMatch(range ->
                            range.matches(attributes))
                    && rule.attributeOptions.stream().allMatch(option ->
                            option.matches(attributes))) {
                rule.items.keySet().forEach(ranged::remove);
                rule.rangedItems.keySet().forEach(fixed::remove);
                fixed.putAll(rule.items);
                ranged.putAll(rule.rangedItems);
                rule.loot.keySet().forEach(rangedLoot::remove);
                rule.rangedLoot.keySet().forEach(selectedLoot::remove);
                selectedLoot.putAll(rule.loot);
                rangedLoot.putAll(rule.rangedLoot);
            }
        }
        return new Selection(fixed, selectedLoot, ranged, rangedLoot);
    }
}
