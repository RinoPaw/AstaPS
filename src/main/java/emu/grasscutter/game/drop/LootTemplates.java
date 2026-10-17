package emu.grasscutter.game.drop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expand parameterized loot tables at load time, with safe integer expressions. */
final class LootTemplates {
    private static final Pattern DEFINITION =
            Pattern.compile("([a-z][a-z0-9_]*)<([a-z][a-z0-9_]*)>");
    private static final Pattern INVOCATION =
            Pattern.compile("([a-z][a-z0-9_]*)<([0-9]+)>");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^{}]*)}");
    private static final Pattern INTEGER_EXPRESSION =
            Pattern.compile("([a-z][a-z0-9_]*|[0-9]+)((?:[+-][0-9]+)*)");
    private static final Pattern OPERATION = Pattern.compile("([+-])([0-9]+)");
    private static final int MAX_DEPTH = 64;
    private static final int MAX_INSTANCES = 1_000;

    private record Definition(String parameter, Map<?, ?> content) {}

    private final String namespace;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, List<?>> instances = new HashMap<>();
    private final Set<String> expanding = new HashSet<>();

    private LootTemplates(String namespace) {
        this.namespace = namespace;
    }

    static Map<?, ?> expand(Map<?, ?> document, String namespace) {
        var templates = new LootTemplates(namespace);
        var result = new LinkedHashMap<Object, Object>();
        for (var entry : document.entrySet()) {
            if (entry.getKey() instanceof String key) {
                Matcher definition = DEFINITION.matcher(key);
                if (definition.matches()) {
                    if (!(entry.getValue() instanceof Map<?, ?> content)) {
                        throw new IllegalArgumentException("Loot template must be a mapping: " + key);
                    }
                    if (templates.definitions.putIfAbsent(definition.group(1),
                            new Definition(definition.group(2), content)) != null) {
                        throw new IllegalArgumentException("Duplicate loot template: " + key);
                    }
                    continue;
                }
            }
            result.put(entry.getKey(), entry.getValue());
        }
        for (String name : templates.definitions.keySet()) {
            if (result.containsKey(name)) {
                throw new IllegalArgumentException("Loot template shadows table: " + name);
            }
        }
        var shared = new IdentityHashMap<Object, Object>();
        result.replaceAll((key, value) -> templates.substitute(value, Map.of(), 0, shared));
        return result;
    }

    private Object substitute(Object raw, Map<String, Integer> parameters, int depth,
                              IdentityHashMap<Object, Object> shared) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Loot template nesting exceeds " + MAX_DEPTH);
        }
        if (raw instanceof List<?> list) {
            Object existing = shared.get(raw);
            if (existing != null) return existing;
            var output = new ArrayList<Object>(list.size());
            shared.put(raw, output);
            for (Object value : list) {
                output.add(substitute(value, parameters, depth + 1, shared));
            }
            return output;
        }
        if (raw instanceof Map<?, ?> map) {
            Object existing = shared.get(raw);
            if (existing != null) return existing;
            var output = new LinkedHashMap<Object, Object>();
            shared.put(raw, output);
            for (var entry : map.entrySet()) {
                Object value = substitute(entry.getValue(), parameters, depth + 1, shared);
                if ("reward".equals(entry.getKey()) && value instanceof String reward) {
                    value = expandReward(reward, depth + 1);
                }
                output.put(entry.getKey(), value);
            }
            return output;
        }
        if (raw instanceof String value && value.contains("${")) {
            Matcher matcher = EXPRESSION.matcher(value);
            StringBuffer expanded = new StringBuffer();
            while (matcher.find()) {
                int number = evaluate(matcher.group(1), parameters);
                matcher.appendReplacement(expanded, Matcher.quoteReplacement(Integer.toString(number)));
            }
            matcher.appendTail(expanded);
            String result = expanded.toString();
            if (result.contains("${")) {
                throw new IllegalArgumentException("Unclosed or invalid loot expression: " + value);
            }
            if (value.trim().matches("\\$\\{[^{}]+}")) {
                return Integer.parseInt(result);
            }
            return result;
        }
        return raw;
    }

    private Object expandReward(String reward, int depth) {
        String prefix = namespace + ".loot.";
        String local = reward.startsWith(prefix) ? reward.substring(prefix.length()) : reward;
        Matcher invocation = INVOCATION.matcher(local);
        if (!invocation.matches()) return reward;
        Definition definition = definitions.get(invocation.group(1));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown loot template: " + reward);
        }
        return instantiate(invocation.group(1), invocation.group(2), definition, depth);
    }

    private List<?> instantiate(String name, String argument, Definition definition, int depth) {
        String key = name + "<" + argument + ">";
        List<?> cached = instances.get(key);
        if (cached != null) return cached;
        if (instances.size() >= MAX_INSTANCES || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Too many or too deeply nested loot template expansions");
        }
        if (!expanding.add(key)) {
            throw new IllegalArgumentException("Cyclic loot template invocation: " + key);
        }
        try {
            int value;
            try {
                value = Integer.parseInt(argument);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid loot template argument: " + key, ex);
            }
            Map<?, ?> body = definition.content();
            for (Object field : body.keySet()) {
                if (!"common".equals(field) && !"variants".equals(field)) {
                    throw new IllegalArgumentException("Unknown loot template field: " + field);
                }
            }
            if (!(body.get("common") instanceof List<?> common)
                    || !(body.get("variants") instanceof Map<?, ?> variants)) {
                throw new IllegalArgumentException("Loot template requires common list and variants map: " + name);
            }
            Object extra = null;
            for (var entry : variants.entrySet()) {
                if (argument.equals(String.valueOf(entry.getKey()))) {
                    extra = entry.getValue();
                    break;
                }
            }
            if (!(extra instanceof List<?> selected)) {
                throw new IllegalArgumentException("Unsupported loot template argument: " + key);
            }
            var combined = new ArrayList<Object>(common.size() + selected.size());
            combined.addAll(common);
            combined.addAll(selected);
            if (combined.isEmpty()) {
                throw new IllegalArgumentException("Empty loot template expansion: " + key);
            }
            List<?> expanded = (List<?>) substitute(combined,
                    Map.of(definition.parameter(), value), depth + 1,
                    new IdentityHashMap<>());
            instances.put(key, expanded);
            return expanded;
        } finally {
            expanding.remove(key);
        }
    }

    private static int evaluate(String expression, Map<String, Integer> parameters) {
        String normalized = expression.replaceAll("\\s+", "");
        Matcher parsed = INTEGER_EXPRESSION.matcher(normalized);
        if (!parsed.matches()) {
            throw new IllegalArgumentException("Unsupported loot expression: ${" + expression + "}");
        }
        String base = parsed.group(1);
        int result;
        try {
            if (base.matches("[0-9]+")) {
                result = Integer.parseInt(base);
            } else {
                Integer bound = parameters.get(base);
                if (bound == null) {
                    throw new IllegalArgumentException("Unknown loot template parameter: " + base);
                }
                result = bound;
            }
            Matcher operations = OPERATION.matcher(parsed.group(2));
            while (operations.find()) {
                int operand = Integer.parseInt(operations.group(2));
                result = "+".equals(operations.group(1))
                        ? Math.addExact(result, operand) : Math.subtractExact(result, operand);
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Loot expression overflows: ${" + expression + "}", ex);
        }
        return result;
    }
}
