package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSStyleRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriterSettings;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/**
 * Selects item quantities and loot-table execution counts from CSS.
 * Loot definitions never inspect the selector or the matched game object.
 */
public final class CssRewards {
    public record Selection(Map<Integer, Integer> items, Map<String, Integer> loot) {
        public Selection {
            items = Map.copyOf(items);
            loot = Map.copyOf(loot);
        }
    }

    private record Rule(
            Evaluator selector, Map<ResourceId, Integer> values, int specificity, int order) {}

    private final List<Rule> rules;
    private final ItemIdRegistry items;

    private CssRewards(List<Rule> rules, ItemIdRegistry items) {
        this.rules = List.copyOf(rules);
        this.items = items;
    }

    public static CssRewards parse(String source, ItemIdRegistry items, YamlLootTables loot) {
        var sheet = CSSReader.readFromString(source);
        if (sheet == null) throw new IllegalArgumentException("Invalid rewards.css");

        var rules = new ArrayList<Rule>();
        int order = 0;
        for (var top : sheet.getAllRules()) {
            if (!(top instanceof CSSStyleRule style) || !style.getAllRules().isEmpty()) {
                throw new IllegalArgumentException("Expected CSS style rules");
            }

            var values = new HashMap<ResourceId, Integer>();
            for (var declaration : style.getAllDeclarations()) {
                ResourceId resource = ResourceId.fromCssProperty(declaration.getProperty());
                switch (resource.type()) {
                    case "item" -> items.resolve(resource.toString());
                    case "loot" -> {
                        if (!loot.contains(resource.toString())) {
                            throw new IllegalArgumentException("Unknown loot table: " + resource);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unsupported reward type: " + resource);
                }
                String raw = declaration.getExpressionAsCSSString().trim();
                if (!raw.matches("[0-9]+")) {
                    throw new IllegalArgumentException("Invalid quantity for " + resource + ": " + raw);
                }
                values.put(resource, Integer.parseInt(raw));
            }

            for (var selector : style.getAllSelectors()) {
                String css = selector.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                int specificity = (int) css.chars().filter(ch -> ch == '[').count();
                rules.add(new Rule(
                        Selector.evaluatorOf(css), Map.copyOf(values), specificity, order++));
            }
        }

        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));
        return new CssRewards(rules, items);
    }

    public Selection resolve(String type, Map<String, String> attributes) {
        var element = new Element(type);
        attributes.forEach(element::attr);
        var selected = new HashMap<ResourceId, Integer>();
        for (var rule : rules) {
            if (rule.selector.matches(element.root(), element)) {
                selected.putAll(rule.values);
            }
        }

        var fixedItems = new HashMap<Integer, Integer>();
        var selectedLoot = new HashMap<String, Integer>();
        selected.forEach((resource, quantity) -> {
            switch (resource.type()) {
                case "item" -> fixedItems.put(items.resolve(resource.toString()), quantity);
                case "loot" -> selectedLoot.put(resource.toString(), quantity);
                default -> throw new IllegalStateException("Unsupported reward type: " + resource);
            }
        });
        return new Selection(fixedItems, selectedLoot);
    }
}
