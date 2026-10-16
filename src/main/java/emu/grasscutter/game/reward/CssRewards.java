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
    // A quoted level value such as "1~5" selects an inclusive numeric range.
    // A missing upper bound ("26~") means all levels from 26 onwards.
    private static final Pattern LEVEL_RANGE = Pattern.compile(
            "\\[\\s*level\\s*=\\s*([\"'])([0-9]+)~([0-9]*)\\1\\s*\\]");
    private static final Pattern MALFORMED_LEVEL_RANGE = Pattern.compile(
            "\\[\\s*level\\s*=\\s*([\"'])[^\"']*~[^\"']*\\1\\s*\\]");

    private record LevelRange(long min, Long max) {
        boolean matches(Map<String, String> attributes) {
            String value = attributes.get("level");
            if (value == null) return false;
            try {
                long actual = Long.parseLong(value);
                return actual >= min && (max == null || actual <= max);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private record Rule(
            Evaluator selector, List<LevelRange> levelRanges,
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
                "(--(?:genshin-item|astaps-loot)-[\\p{L}\\p{N}_-]+\\s*:\\s*)([0-9]+)\\s*~\\s*([0-9]+)(\\s*;)",
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
                    if (value.matches("[0-9]+\\s*~\\s*[0-9]+")) {
                        ranged.put(id, LootCount.parse(value.replaceAll("\\s+", "")));
                        fixed.remove(id);
                    } else {
                        if (!value.matches("[0-9]+")) {
                            throw new IllegalArgumentException("Invalid item count for "
                                    + resource + ": " + value);
                        }
                        fixed.put(id, Integer.parseInt(value));
                        ranged.remove(id);
                    }
                }
            }

            for (var selector : style.getAllSelectors()) {
                String css = selector.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                var levelRanges = new ArrayList<LevelRange>();
                var selectorBuffer = new StringBuffer();
                var matcher = LEVEL_RANGE.matcher(css);
                while (matcher.find()) {
                    long min = Long.parseLong(matcher.group(2));
                    Long max = matcher.group(3).isEmpty()
                            ? null : Long.parseLong(matcher.group(3));
                    if (max != null && max < min) {
                        throw new IllegalArgumentException("Reversed reward level range: " + css);
                    }
                    levelRanges.add(new LevelRange(min, max));
                    matcher.appendReplacement(selectorBuffer, "[level]");
                }
                matcher.appendTail(selectorBuffer);
                String normalizedSelector = selectorBuffer.toString();
                if (MALFORMED_LEVEL_RANGE.matcher(normalizedSelector).find()) {
                    throw new IllegalArgumentException("Invalid reward level range: " + css);
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
                        List.copyOf(levelRanges), Map.copyOf(fixed),
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
                    && rule.levelRanges.stream().allMatch(range ->
                            range.matches(attributes))) {
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
