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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/**
 * CSS selects named loot tables and fixes item quantities.
 * Each --loot declaration is one complete list; item properties cascade independently.
 */
public final class CssRewards {
    public record Selection(Map<Integer, Integer> items, Map<String, Integer> loot) {
        public Selection {
            items = Map.copyOf(items);
            loot = Map.copyOf(loot);
        }
    }

    private static final Pattern TABLE = Pattern.compile("^(.+?)(?:\\s+([0-9]+))?$");

    private record Rule(
            Evaluator selector, Map<Integer, Integer> items, Map<String, Integer> loot,
            int specificity, int order) {}

    private final List<Rule> rules;

    private CssRewards(List<Rule> rules) {
        this.rules = List.copyOf(rules);
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

            var fixed = new HashMap<Integer, Integer>();
            Map<String, Integer> selectedLoot = null;
            for (var declaration : style.getAllDeclarations()) {
                String property = declaration.getProperty();
                String value = declaration.getExpressionAsCSSString().trim();
                if (property.equals("--loot")) {
                    selectedLoot = parseLoot(value, loot);
                } else {
                    ResourceId resource = ResourceId.fromCssProperty(property);
                    int id = items.resolve(resource.toString());
                    if (!value.matches("[0-9]+")) {
                        throw new IllegalArgumentException("Invalid item count for "
                                + resource + ": " + value);
                    }
                    fixed.put(id, Integer.parseInt(value));
                }
            }

            for (var selector : style.getAllSelectors()) {
                String css = selector.getAsCSSString(CSSWriterSettings.DEFAULT_SETTINGS, 0).trim();
                // Element and attribute selectors are the supported subset for now.
                int specificity = (int) css.chars().filter(ch -> ch == '[').count();
                rules.add(new Rule(Selector.evaluatorOf(css), Map.copyOf(fixed),
                        selectedLoot, specificity, order++));
            }
        }

        rules.sort(Comparator.comparingInt(Rule::specificity).thenComparingInt(Rule::order));
        return new CssRewards(rules);
    }

    private static Map<String, Integer> parseLoot(String value, YamlLootTables loot) {
        if (value.equals("none")) return Map.of();
        var selected = new LinkedHashMap<String, Integer>();
        for (String entry : value.split(",", -1)) {
            // The CSS serializer may insert spaces around a qualified ID's dots.
            // Resource IDs use those dots as separators, not CSS selector punctuation.
            var match = TABLE.matcher(entry.trim().replaceAll("\\s*\\.\\s*", "."));
            if (!match.matches()) {
                throw new IllegalArgumentException("Invalid loot entry: " + entry);
            }
            String name = match.group(1).trim();
            if ((name.startsWith("\"") && name.endsWith("\""))
                    || (name.startsWith("'") && name.endsWith("'"))) {
                name = name.substring(1, name.length() - 1);
            }
            String id = loot.resolveName(name);
            int count = match.group(2) == null ? 1 : Integer.parseInt(match.group(2));
            selected.merge(id, count, Math::addExact);
        }
        return Map.copyOf(selected);
    }

    public Selection resolve(String type, Map<String, String> attributes) {
        var element = new Element(type);
        attributes.forEach(element::attr);
        var fixed = new HashMap<Integer, Integer>();
        Map<String, Integer> selectedLoot = Map.of();
        for (var rule : rules) {
            if (rule.selector.matches(element.root(), element)) {
                fixed.putAll(rule.items);
                if (rule.loot != null) selectedLoot = rule.loot;
            }
        }
        return new Selection(fixed, selectedLoot);
    }
}
