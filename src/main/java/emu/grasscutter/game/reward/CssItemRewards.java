package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSStyleRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriterSettings;
import emu.grasscutter.data.ItemIdRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/**
 * Standalone item declaration prototype for rewards.css; not wired to reward settlement.
 * A declaration is "reward: genshin:item:mora 300;" and may repeat in one rule.
 * Explicit zero is an override, not an absent reward.
 */
public final class CssItemRewards {
    private static final Pattern TARGET =
            Pattern.compile("^(chest|transPoint)(?=\\[|$)");
    private static final Pattern ATTRIBUTE =
            Pattern.compile("\\[([a-z][a-zA-Z0-9]*)\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)')\\]");
    private static final Pattern VALUE =
            Pattern.compile("^(?:\"([^\"]+)\"|'([^']+)'|([^\\s]+))\\s+([0-9]+)$");
    private static final Set<String> CHEST = Set.of("kind", "scene", "area1", "area2", "pool");
    private static final Set<String> POINT = Set.of("kind", "scene", "point");

    private record Rule(Evaluator selector, Map<Integer, Integer> amounts,
                        int specificity, int order) {}

    private final List<Rule> rules;

    private CssItemRewards(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static CssItemRewards parse(String source, ItemIdRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("Missing item registry");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Empty rewards.css");
        var sheet = CSSReader.readFromString(source);
        if (sheet == null || sheet.hasImportRules() || sheet.hasNamespaceRules()
                || sheet.getAllRules().isEmpty()) {
            throw new IllegalArgumentException("Invalid rewards.css");
        }
        var rules = new ArrayList<Rule>();
        int order = 0;
        for (var top : sheet.getAllRules()) {
            if (!(top instanceof CSSStyleRule style) || !style.getAllRules().isEmpty()) {
                throw new IllegalArgumentException("Unsupported CSS rule");
            }
            var amounts = new HashMap<Integer, Integer>();
            for (var decl : style.getAllDeclarations()) {
                if (decl.isImportant()) throw new IllegalArgumentException("!important is unsupported");
                String property = decl.getProperty();
                String value = decl.getExpressionAsCSSString().trim();
                int id;
                int count;
                if (property.equalsIgnoreCase("reward")) {
                    var match = VALUE.matcher(value);
                    if (!match.matches()) {
                        throw new IllegalArgumentException("Expected reward: resource-id count: " + value);
                    }
                    String name = match.group(1) != null ? match.group(1)
                            : match.group(2) != null ? match.group(2) : match.group(3);
                    id = registry.resolve(name);
                    count = amount(match.group(4));
                } else {
                    id = switch (property.toLowerCase(java.util.Locale.ROOT)) {
                        case "adventureexp", "adventure-exp" -> registry.resolve("102");
                        case "primogems" -> registry.resolve("genshin:item:primogem");
                        case "mora" -> registry.resolve("genshin:item:mora");
                        default -> {
                            if (property.startsWith("item-") && property.substring(5).matches("[0-9]+")) {
                                yield registry.resolve(property.substring(5));
                            }
                            throw new IllegalArgumentException("Unsupported reward field: " + property);
                        }
                    };
                    count = amount(value);
                }
                if (amounts.putIfAbsent(id, count) != null) {
                    throw new IllegalArgumentException("Duplicate item reward ID: " + id);
                }
            }
            if (amounts.isEmpty()) throw new IllegalArgumentException("Empty CSS reward declaration");
            for (var raw : style.getAllSelectors()) {
                String selector = raw.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                var type = TARGET.matcher(selector);
                if (!type.find()) throw new IllegalArgumentException("Unknown reward selector: " + selector);
                Set<String> allowed = type.group(1).equals("chest") ? CHEST : POINT;
                var attributes = new java.util.HashSet<String>();
                var matcher = ATTRIBUTE.matcher(selector.substring(type.end()));
                int cursor = 0;
                while (matcher.find()) {
                    if (matcher.start() != cursor || !allowed.contains(matcher.group(1))
                            || !attributes.add(matcher.group(1))) {
                        throw new IllegalArgumentException("Invalid reward selector: " + selector);
                    }
                    cursor = matcher.end();
                }
                if (cursor != selector.length() - type.end()
                        || (attributes.contains("area2") && !attributes.contains("area1"))
                        || (attributes.contains("area1") && !attributes.contains("scene"))
                        || (attributes.contains("point") && !attributes.contains("scene"))) {
                    throw new IllegalArgumentException("Invalid reward selector: " + selector);
                }
                rules.add(new Rule(Selector.evaluatorOf(selector), Map.copyOf(amounts),
                        attributes.size(), order++));
            }
        }
        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));
        return new CssItemRewards(rules);
    }

    private static int amount(String value) {
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException("Reward amount must be a nonnegative integer: " + value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Reward amount out of range: " + value, e);
        }
    }

    public Map<Integer, Integer> resolve(String type, Map<String, String> attributes) {
        if (type == null || attributes == null) {
            throw new IllegalArgumentException("Missing reward selector target");
        }
        if (!type.equals("chest") && !type.equals("transPoint")) {
            throw new IllegalArgumentException("Unknown reward selector type: " + type);
        }
        var element = new Element(type);
        attributes.forEach(element::attr);
        var result = new HashMap<Integer, Integer>();
        for (var rule : rules) {
            if (rule.selector.matches(element.root(), element)) result.putAll(rule.amounts);
        }
        return Map.copyOf(result);
    }
}
