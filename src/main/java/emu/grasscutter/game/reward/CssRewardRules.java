package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSStyleRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriterSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/**
 * A deliberately narrow application of real CSS syntax to game reward objects.
 * ph-css parses the stylesheet and declaration values; jsoup compiles/matches
 * CSS selectors. The only supported selectors are a type and equality attributes.
 * Unsupported CSS features fail startup rather than being ignored.
 */
final class CssRewardRules {
    private static final Pattern TYPE = Pattern.compile("^(transPoint|chest)");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\[([a-z][a-zA-Z0-9]*)\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)')\\]");
    private static final Pattern POSITIVE = Pattern.compile("[1-9][0-9]*");
    private static final Set<String> TRANSPORT_FIELDS = Set.of("kind", "scene", "point");
    private static final Set<String> CHEST_FIELDS = Set.of("kind", "scene", "area1", "area2");

    record Rule(String type, String selector, Evaluator evaluator, Map<String,String> filters,
                int specificity, int sourceOrder, Map<Integer,Integer> amounts) {
        boolean matches(Element target) {
            return evaluator.matches(target.root(), target);
        }

        boolean isDefaultFor(String kind) {
            return !filters.containsKey("scene")
                    && !filters.containsKey("point")
                    && !filters.containsKey("area1")
                    && !filters.containsKey("area2")
                    && (!filters.containsKey("kind") || filters.get("kind").equals(kind));
        }
    }

    private CssRewardRules() {}

    static List<Rule> parse(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("rewards.css must contain reward rules");
        }
        // Default ph-css settings refuse recoverable syntax errors. Do not silently
        // skip unsupported at-rules or syntactically invalid declarations.
        var sheet = CSSReader.readFromString(source);
        if (sheet == null || sheet.hasImportRules() || sheet.hasNamespaceRules()
                || sheet.getAllRules().isEmpty()) {
            throw new IllegalArgumentException("Invalid or unsupported rewards.css stylesheet");
        }
        var result = new ArrayList<Rule>();
        int order = 0;
        for (var topLevel : sheet.getAllRules()) {
            if (!(topLevel instanceof CSSStyleRule style) || style.getAllRules().size() > 0) {
                throw new IllegalArgumentException(
                        "Only simple CSS style rules are supported in rewards.css");
            }
            Map<Integer,Integer> amounts = DirectReward.parse(
                    style.getAllDeclarations(), "rewards.css rule " + (order + 1));
            if (amounts.isEmpty()) {
                throw new IllegalArgumentException("Empty reward declaration block");
            }
            for (var rawSelector : style.getAllSelectors()) {
                String selector = rawSelector.getAsCSSString(
                        CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                Matcher type = TYPE.matcher(selector);
                if (!type.find()) {
                    throw new IllegalArgumentException("Unsupported reward selector: " + selector);
                }
                String target = type.group(1);
                String remainder = selector.substring(type.end());
                Map<String,String> attributes = new HashMap<>();
                Matcher matcher = ATTRIBUTE.matcher(remainder);
                int offset = 0;
                Set<String> valid = target.equals("chest") ? CHEST_FIELDS : TRANSPORT_FIELDS;
                while (matcher.find()) {
                    if (matcher.start() != offset) {
                        throw new IllegalArgumentException("Unsupported reward selector: " + selector);
                    }
                    offset = matcher.end();
                    String key = matcher.group(1);
                    String value = matcher.group(2) == null
                            ? matcher.group(3) : matcher.group(2);
                    if (!valid.contains(key) || attributes.putIfAbsent(key, value) != null) {
                        throw new IllegalArgumentException("Invalid selector attribute in " + selector);
                    }
                    if (key.equals("kind")) {
                        Set<String> allowed = target.equals("chest")
                                ? Set.of("common", "exquisite", "precious", "luxurious")
                                : Set.of("waypoint", "statue", "domain");
                        if (!allowed.contains(value)) {
                            throw new IllegalArgumentException("Invalid kind in " + selector);
                        }
                    } else if (!POSITIVE.matcher(value).matches()) {
                        throw new IllegalArgumentException("Expected positive decimal ID in " + selector);
                    } else {
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Selector ID too large in " + selector, e);
                        }
                    }
                }
                if (offset != remainder.length()
                        || (attributes.containsKey("point") && !attributes.containsKey("scene"))
                        || (attributes.containsKey("area1") && !attributes.containsKey("scene"))
                        || (attributes.containsKey("area2") && !attributes.containsKey("area1"))) {
                    throw new IllegalArgumentException("Unsupported reward selector: " + selector);
                }
                Evaluator evaluator;
                try {
                    evaluator = Selector.evaluatorOf(selector);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid CSS selector: " + selector, e);
                }
                result.add(new Rule(target, selector, evaluator, Map.copyOf(attributes),
                        attributes.size(), order, amounts));
            }
            order++;
        }
        return List.copyOf(result);
    }

    /** CSS specificity within our supported subset is the attribute count. */
    static List<Rule> ofType(List<Rule> all, String type) {
        return all.stream().filter(rule -> rule.type().equals(type))
                .sorted(java.util.Comparator.comparingInt(Rule::specificity)
                        .thenComparingInt(Rule::sourceOrder))
                .toList();
    }

    static Element target(String type, Map<String,String> properties) {
        Element target = new Element(type);
        properties.forEach(target::attr);
        return target;
    }

    static void requireDefaults(List<Rule> rules, String type, String... kinds) {
        for (String kind : kinds) {
            if (rules.stream().noneMatch(rule -> rule.isDefaultFor(kind))) {
                throw new IllegalArgumentException(
                        "Missing default rewards.css rule for " + type + "[kind=\"" + kind + "\"]");
            }
        }
    }
}
