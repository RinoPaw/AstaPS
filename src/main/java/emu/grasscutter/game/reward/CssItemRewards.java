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
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/**
 * CSS rules for fixed item rewards. Only the reward declaration is supported.
 * Reward sources and selector attributes are not hard-coded into this parser.
 */
public final class CssItemRewards {
    private static final Pattern REWARD_VALUE =
            Pattern.compile("^(?:\"([^\"]+)\"|'([^']+)'|(\\S+))\\s+([0-9]+)$");

    private record Rule(Evaluator selector, Map<Integer, Integer> amounts,
                        int specificity, int order) {}

    private final List<Rule> rules;

    private CssItemRewards(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static CssItemRewards parse(String source, ItemIdRegistry items) {
        var sheet = CSSReader.readFromString(source);
        if (sheet == null) throw new IllegalArgumentException("Invalid rewards.css");

        var rules = new ArrayList<Rule>();
        int order = 0;
        for (var top : sheet.getAllRules()) {
            if (!(top instanceof CSSStyleRule style) || !style.getAllRules().isEmpty()) {
                throw new IllegalArgumentException("Expected CSS style rules");
            }

            var amounts = new HashMap<Integer, Integer>();
            for (var declaration : style.getAllDeclarations()) {
                if (!declaration.getProperty().equals("reward")) {
                    throw new IllegalArgumentException("Unknown reward property: "
                            + declaration.getProperty());
                }
                if (declaration.isImportant()) {
                    throw new IllegalArgumentException("!important is not supported");
                }

                var match = REWARD_VALUE.matcher(declaration.getExpressionAsCSSString().trim());
                if (!match.matches()) {
                    throw new IllegalArgumentException("Expected reward: resource-id count");
                }
                String name = match.group(1) != null ? match.group(1)
                        : match.group(2) != null ? match.group(2) : match.group(3);
                int id = items.resolve(name);
                int count = Integer.parseInt(match.group(4));
                amounts.put(id, count);
            }

            for (var selector : style.getAllSelectors()) {
                String css = selector.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                // For the current element+attribute selector form, each attribute
                // increases specificity; later rules win ties.
                int specificity = (int) css.chars().filter(ch -> ch == '[').count();
                rules.add(new Rule(Selector.evaluatorOf(css), Map.copyOf(amounts),
                        specificity, order++));
            }
        }

        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));
        return new CssItemRewards(rules);
    }

    public Map<Integer, Integer> resolve(String type, Map<String, String> attributes) {
        var element = new Element(type);
        attributes.forEach(element::attr);
        var resolved = new HashMap<Integer, Integer>();
        for (var rule : rules) {
            if (rule.selector.matches(element.root(), element)) {
                resolved.putAll(rule.amounts);
            }
        }
        return Map.copyOf(resolved);
    }
}
