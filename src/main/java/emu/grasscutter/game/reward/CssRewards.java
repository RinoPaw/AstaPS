package emu.grasscutter.game.reward;

import io.sf.carte.doc.style.css.CSSNestedDeclarations;
import io.sf.carte.doc.style.css.CSSStyleSheet;
import io.sf.carte.doc.style.css.om.StyleRule;
import java.io.IOException;
import java.io.StringReader;
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

/** Dotted reward declarations use css4j for selectors, nesting and CSS declaration semantics. */
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

    // Range membership is domain data represented by ordinary CSS attributes.
    private static final Pattern RANGE_MEMBERSHIP_ATTRIBUTE = Pattern.compile(
            "\\[\\s*(data-range-([a-zA-Z_][a-zA-Z0-9_-]*)-([0-9]+)-(up|[0-9]+))\\s*\\]");

    private static String resourceName(String property, Map<String, String> names) {
        String name = names.get(io.sf.carte.doc.style.css.parser.ParseHelper.unescapeStringValue(property));
        if (name == null) {
            throw new IllegalArgumentException("Unknown reward property: " + property);
        }
        if (!name.startsWith("genshin.") && !name.contains(".loot.")) {
            throw new IllegalArgumentException("Invalid reward resource property: " + name);
        }
        return name;
    }

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

    private record LootDeclaration(RewardResourceExpression resource, Integer fixed, LootCount.Spec ranged) {}
    private record ItemDeclaration(RewardResourceExpression resource, Integer fixed, LootCount.Spec ranged) {}

    private record Rule(
            Selector selector,
            List<ItemDeclaration> items, List<LootDeclaration> loot,
            Map<String, List<String>> bindings,
            boolean important, int order) {}

    private final List<Rule> rules;
    private final List<String> bindingNames;
    private final List<RangeMembership> rangeMemberships;
    private final YamlLootTables loot;
    private final ItemIdRegistry itemRegistry;

    private CssRewards(List<Rule> rules, List<RangeMembership> rangeMemberships,
                       YamlLootTables loot, ItemIdRegistry itemRegistry, Map<String, String> names) {
        this.rules = List.copyOf(rules);
        this.bindingNames = names.values().stream().filter(name -> name.startsWith("--"))
                .map(name -> name.substring(2)).toList();
        this.rangeMemberships = List.copyOf(rangeMemberships);
        this.loot = loot;
        this.itemRegistry = itemRegistry;
    }

    public static CssRewards parse(String source, ItemIdRegistry items, YamlLootTables loot) {
        var translated = RewardCssSource.translate(source);
        var sheet = new CSSDOMImplementation().createStyleSheet(null, null);
        try {
            if (!sheet.parseStyleSheet(new StringReader(translated.css()), CSSStyleSheet.COMMENTS_IGNORE)) {
                var handler = sheet.getErrorHandler();
                String details;
                if (handler instanceof io.sf.carte.doc.style.css.om.DefaultSheetErrorHandler errors) {
                    var causes = new ArrayList<io.sf.carte.doc.style.css.nsac.CSSParseException>();
                    if (errors.getSacErrors() != null) causes.addAll(errors.getSacErrors());
                    if (errors.getRuleParseErrors() != null) {
                        errors.getRuleParseErrors().forEach(error -> causes.add(error.getCause()));
                    }
                    details = causes.stream().limit(8).map(error ->
                            translated.location(error.getLineNumber(), error.getColumnNumber())
                                    + " " + translated.display(error.getMessage())).toList().toString();
                } else {
                    details = translated.display(String.valueOf(handler));
                }
                throw new IllegalArgumentException("Invalid reward CSS: " + details);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot parse reward CSS", ex);
        }
        var rules = new ArrayList<Rule>();
        var intervals = new java.util.LinkedHashMap<String, RangeMembership>();
        int[] order = {0};
        for (var top : sheet.getCssRules()) {
            if (!(top instanceof StyleRule style)) {
                throw new IllegalArgumentException("Unsupported conditional CSS rule: " + top);
            }
            collectStyle(style, null, rules, intervals, order, items, loot, translated.names());
        }
        return new CssRewards(rules, List.copyOf(intervals.values()), loot, items, translated.names());
    }

    private static void collectStyle(StyleRule style, SelectorList parent,
            List<Rule> rules, Map<String, RangeMembership> intervals, int[] sourceOrder,
            ItemIdRegistry items, YamlLootTables loot, Map<String, String> names) {
        // Use css4j's CSSOM selector tree directly, without serializing/reparsing it.
        SelectorList selectors = style.getSelectorList();
        if (parent != null) {
            selectors = selectors.replaceNested(parent);
        }
        collectDeclarations(style.getStyle(), selectors, rules, intervals,
                sourceOrder, items, loot, names);
        var nestedRules = style.getCssRules();
        if (nestedRules != null) {
            for (var nested : nestedRules) {
                if (nested instanceof StyleRule child) {
                    collectStyle(child, selectors, rules, intervals, sourceOrder,
                            items, loot, names);
                } else if (nested instanceof CSSNestedDeclarations continuation) {
                    collectDeclarations(continuation.getStyle(), selectors, rules,
                            intervals, sourceOrder, items, loot, names);
                } else {
                    throw new IllegalArgumentException("Unsupported conditional reward rule: " + nested);
                }
            }
        }
    }

    private static void collectDeclarations(io.sf.carte.doc.style.css.CSSStyleDeclaration declarations,
            SelectorList selectors, List<Rule> rules,
            Map<String, RangeMembership> intervals, int[] sourceOrder,
            ItemIdRegistry items, YamlLootTables loot, Map<String, String> names) {
        if (declarations.getLength() == 0) return;
        collectDeclarationsForPriority(declarations, selectors, rules, intervals,
                sourceOrder, items, loot, names, false);
        collectDeclarationsForPriority(declarations, selectors, rules, intervals,
                sourceOrder, items, loot, names, true);
    }

    private static void collectDeclarationsForPriority(
            io.sf.carte.doc.style.css.CSSStyleDeclaration declarations,
            SelectorList selectors, List<Rule> rules,
            Map<String, RangeMembership> intervals, int[] sourceOrder,
            ItemIdRegistry items, YamlLootTables loot, Map<String, String> names, boolean important) {
        var itemDeclarations = new ArrayList<ItemDeclaration>();
        var lootDeclarations = new ArrayList<LootDeclaration>();
        var bindings = new HashMap<String, List<String>>();
        boolean hasDeclarations = false;
        for (int index = 0; index < declarations.getLength(); index++) {
            String property = declarations.item(index);
            if ("important".equalsIgnoreCase(declarations.getPropertyPriority(property)) != important) {
                continue;
            }
            hasDeclarations = true;
            String value = declarations.getPropertyValue(property).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            String declaredName = names.get(io.sf.carte.doc.style.css.parser.ParseHelper.unescapeStringValue(property));
            if (declaredName != null && declaredName.startsWith("--")) {
                bindings.put(declaredName.substring(2), parseBinding(value));
            } else {
                String name = resourceName(property, names);
                Integer fixed = value.matches("[0-9]+") ? Integer.valueOf(value) : null;
                LootCount.Spec rolled = fixed == null ? LootCount.parseRoll(value) : null;
                boolean table = name.matches("[a-z][a-z0-9_]*\\.loot\\..*");
                var resource = RewardResourceExpression.parse(name, table);
                var declaredBindings = names.values().stream().filter(key -> key.startsWith("--"))
                        .map(key -> key.substring(2)).collect(java.util.stream.Collectors.toSet());
                resource.requireDeclared(declaredBindings);
                if (table) {
                    if (!resource.hasVariables()) loot.resolveName(name);
                    lootDeclarations.add(new LootDeclaration(resource, fixed, rolled));
                } else {
                    if (!resource.hasVariables()) items.resolve(name);
                    itemDeclarations.add(new ItemDeclaration(resource, fixed, rolled));
                }
            }
        }
        if (!hasDeclarations) return;
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
                    List.copyOf(lootDeclarations), Map.copyOf(bindings),
                    important, sourceOrder[0]++));
        }
    }

    private static List<String> parseBinding(String value) {
        String names = value.replaceAll("\\s+", "");
        if (names.startsWith("[") && names.endsWith("]")) names = names.substring(1, names.length() - 1);
        else if (names.contains(",") || names.contains("[") || names.contains("]")) {
            throw new IllegalArgumentException("Binding lists require brackets: " + value);
        }
        var bindings = new ArrayList<String>();
        for (String name : names.split(",", -1)) {
            // Bindings contain literal resource paths or template arguments, never more bindings.
            ResourceId.parse("binding." + name);
            if (bindings.contains(name)) throw new IllegalArgumentException("Duplicate binding value: " + name);
            bindings.add(name);
        }
        return List.copyOf(bindings);
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
        matched.sort(Comparator.comparing((Match match) -> match.rule().important())
                .thenComparing(Match::specificity, Specificity::selectorCompare)
                .thenComparingInt(match -> match.rule().order()));
        var bindings = new HashMap<String, List<String>>();
        bindingNames.forEach(name -> bindings.put(name, List.of()));
        for (var match : matched) bindings.putAll(match.rule().bindings);
        var fixed = new HashMap<Integer, Integer>();
        var ranged = new HashMap<Integer, LootCount.Spec>();
        var selectedLoot = new HashMap<String, Integer>();
        var rangedLoot = new HashMap<String, LootCount.Spec>();
        for (var match : matched) {
            var rule = match.rule();
            for (var declaration : rule.items) {
                for (String name : declaration.resource.expand(bindings)) {
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
                for (String expanded : declaration.resource.expand(bindings)) {
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
        }
        return new Selection(fixed, selectedLoot, ranged, rangedLoot);
    }
}
