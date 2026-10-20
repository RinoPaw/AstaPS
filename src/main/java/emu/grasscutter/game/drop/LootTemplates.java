package emu.grasscutter.game.drop;

import emu.grasscutter.data.ResourceId;
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

/** Expand named item parameters; retain existing safe integer-template expressions. */
final class LootTemplates {
    private static final Pattern DEFINITION =
            Pattern.compile("([a-z][a-z0-9_-]*)\\s*<\\s*([a-z][a-z0-9_]*(?:\\s*,\\s*[a-z][a-z0-9_]*)*)\\s*>");
    private static final Pattern INVOCATION =
            Pattern.compile("([a-z][a-z0-9_-]*)\\s*<\\s*((?:[a-z][a-z0-9_]*|[0-9]+)(?:\\s*,\\s*(?:[a-z][a-z0-9_]*|[0-9]+))*)\\s*>");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^{}]*)}|<([a-z][a-z0-9_]*)>");
    private static final Pattern INTEGER_EXPRESSION =
            Pattern.compile("([a-z][a-z0-9_]*|[0-9]+)((?:[+-][0-9]+)*)");
    private static final Pattern OPERATION = Pattern.compile("([+-])([0-9]+)");
    private static final int MAX_DEPTH = 64;
    private static final int MAX_INSTANCES = 1_000;

    private record Definition(List<String> parameters, List<?> entries) {}
    record Prepared(LootTemplates templates, Map<?, ?> tables) {}

    private final String namespace;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, List<?>> instances = new HashMap<>();
    private final Set<String> expanding = new HashSet<>();

    private LootTemplates(String namespace) {
        this.namespace = namespace;
    }

    static Prepared prepare(Map<?, ?> document, String namespace) {
        var templates = new LootTemplates(namespace);
        var result = new LinkedHashMap<Object, Object>();
        for (var entry : document.entrySet()) {
            if (entry.getKey() instanceof String key) {
                Matcher definition = DEFINITION.matcher(key);
                if (definition.matches()) {
                    if (!(entry.getValue() instanceof List<?> entries)) {
                        throw new IllegalArgumentException("Loot template must be a YAML list: " + key);
                    }
                    var parameters = List.of(definition.group(2).split("\\s*,\\s*"));
                    if (new HashSet<>(parameters).size() != parameters.size()) {
                        throw new IllegalArgumentException("Duplicate loot template parameter: " + key);
                    }
                    if (templates.definitions.putIfAbsent(definition.group(1),
                            new Definition(parameters, entries)) != null) {
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
        return new Prepared(templates, result);
    }

    private Object substitute(Object raw, Map<String, Object> parameters, int depth,
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
                if (("reward".equals(entry.getKey()) || "type".equals(entry.getKey()))
                        && value instanceof String reward) {
                    value = expandReward(reward, depth + 1);
                }
                output.put(entry.getKey(), value);
            }
            return output;
        }
        if (raw instanceof String value && (value.contains("${") || value.contains("<"))) {
            Matcher matcher = EXPRESSION.matcher(value);
            StringBuffer expanded = new StringBuffer();
            while (matcher.find()) {
                boolean legacy = matcher.group(1) != null;
                String expression = legacy ? matcher.group(1).trim() : matcher.group(2);
                // Literal generic arguments are not parameter references.
                if (!legacy && !parameters.containsKey(expression)) continue;
                Object replacement = parameters.containsKey(expression)
                        ? parameters.get(expression) : evaluate(expression, parameters);
                matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement.toString()));
            }
            matcher.appendTail(expanded);
            String result = expanded.toString();
            if (result.contains("${")) {
                throw new IllegalArgumentException("Unclosed or invalid loot expression: " + value);
            }
            if (value.trim().matches("\\$\\{[^{}]+}|<[a-z][a-z0-9_]*>")) {
                return result.matches("[0-9]+") ? Integer.parseInt(result) : result;
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

    /** Canonical resource spelling, including the user's angle-bracket arguments. */
    String qualify(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Missing loot table reference");
        }
        reference = reference.trim();
        String prefix = namespace + ".loot.";
        String local = reference.startsWith(prefix) ? reference.substring(prefix.length()) : reference;
        Matcher invocation = INVOCATION.matcher(local);
        if (invocation.matches()) {
            ResourceId.parse(prefix + invocation.group(1));
            return prefix + invocation.group(1) + "<" + invocation.group(2).replaceAll("\\s+", "") + ">";
        }
        return ResourceId.parse(prefix + local).toString();
    }

    synchronized List<?> instantiateReference(String reference) {
        Object expanded = expandReward(reference, 0);
        if (!(expanded instanceof List<?> entries)) {
            throw new IllegalArgumentException("Unknown loot table: " + reference);
        }
        return entries;
    }

    private List<?> instantiate(String name, String arguments, Definition definition, int depth) {
        String[] values = arguments.replaceAll("\\s+", "").split(",", -1);
        if (values.length != definition.parameters.size()) {
            throw new IllegalArgumentException("Wrong number of loot template arguments: " + name);
        }
        String key = name + "<" + String.join(",", values) + ">";
        List<?> cached = instances.get(key);
        if (cached != null) return cached;
        if (instances.size() >= MAX_INSTANCES || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Too many or too deeply nested loot template expansions");
        }
        if (!expanding.add(key)) throw new IllegalArgumentException("Cyclic loot template invocation: " + key);
        try {
            var parameters = new LinkedHashMap<String, Object>();
            for (int i = 0; i < values.length; i++) {
                Object value;
                try {
                    value = values[i].matches("[0-9]+") ? Integer.valueOf(values[i]) : values[i];
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid loot template argument: " + key, ex);
                }
                parameters.put(definition.parameters.get(i), value);
            }
            if (definition.entries.isEmpty()) throw new IllegalArgumentException("Empty loot template: " + name);
            List<?> expanded = (List<?>) substitute(definition.entries, parameters, depth + 1,
                    new IdentityHashMap<>());
            instances.put(key, expanded);
            return expanded;
        } finally {
            expanding.remove(key);
        }
    }

    private static int evaluate(String expression, Map<String, Object> parameters) {
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
                Object bound = parameters.get(base);
                if (!(bound instanceof Integer number)) {
                    throw new IllegalArgumentException("Expected integer loot template parameter: " + base);
                }
                result = number;
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
