package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSStyleRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriterSettings;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/**
 * Fixed item rewards use CSS custom properties.
 * A resource like genshin.item.mora is written --genshin-item-mora: 300.
 */
public final class CssItemRewards {
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
                ResourceId resource = ResourceId.fromCssProperty(declaration.getProperty());
                int id = items.resolve(resource.toString());
                String value = declaration.getExpressionAsCSSString().trim();
                if (!value.matches("[0-9]+")) {
                    throw new IllegalArgumentException("Invalid item count for " + resource + ": " + value);
                }
                amounts.put(id, Integer.parseInt(value));
            }

            for (var selector : style.getAllSelectors()) {
                String css = selector.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
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
