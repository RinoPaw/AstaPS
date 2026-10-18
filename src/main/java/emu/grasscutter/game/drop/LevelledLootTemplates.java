package emu.grasscutter.game.drop;

import emu.grasscutter.data.ResourceId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Bind item names and select level ranges without changing primitive loot semantics. */
final class LevelledLootTemplates {
    private static final Pattern PARAMETER = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z][a-z0-9_]*)}");
    private static final Pattern RANGE = Pattern.compile("([1-9][0-9]*)(?:~([1-9][0-9]*)?)?");
    private static final int MAX_TABLES = 12_000;

    record Variant(int min, int max, String table) {}
    record Expanded(Map<String, Object> tables, Map<String, List<Variant>> bindings) {}
    private record Level(int min, int max, List<?> entries) {}
    private record Template(List<String> parameters, List<Level> levels) {}

    private LevelledLootTemplates() {}

    static Expanded expand(Map<?, ?> document, String namespace) {
        var templates = new LinkedHashMap<String, Template>();
        Object definitions = document.get("templates");
        if (definitions != null) {
            if (!(definitions instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("Loot templates must be a mapping");
            }
            for (var definition : values.entrySet()) {
                if (!(definition.getKey() instanceof String name) || !PARAMETER.matcher(name).matches()
                        || !(definition.getValue() instanceof Map<?, ?> fields)
                        || fields.size() != 2 || !fields.containsKey("parameters")
                        || !(fields.get("parameters") instanceof List<?> rawParameters)
                        || !(fields.get("levels") instanceof Map<?, ?> rawLevels)) {
                    throw new IllegalArgumentException("Expected template {parameters, levels}: " + definition.getKey());
                }
                var parameters = new ArrayList<String>();
                for (Object parameter : rawParameters) {
                    if (!(parameter instanceof String key) || !PARAMETER.matcher(key).matches()
                            || parameters.contains(key)) {
                        throw new IllegalArgumentException("Invalid or duplicate template parameter: " + parameter);
                    }
                    parameters.add(key);
                }
                var levels = new ArrayList<Level>();
                for (var row : rawLevels.entrySet()) {
                    var matcher = RANGE.matcher(String.valueOf(row.getKey()));
                    if (!matcher.matches() || !(row.getValue() instanceof List<?> entries)) {
                        throw new IllegalArgumentException("Expected template level range and reward list: " + row.getKey());
                    }
                    int min = Integer.parseInt(matcher.group(1));
                    int max = matcher.group(2) != null ? Integer.parseInt(matcher.group(2))
                            : matcher.group().contains("~") ? Integer.MAX_VALUE : min;
                    if (max < min) throw new IllegalArgumentException("Reversed template level range: " + row.getKey());
                    levels.add(new Level(min, max, entries));
                }
                levels.sort(Comparator.comparingInt(Level::min));
                int previous = 0;
                for (var level : levels) {
                    if (level.min <= previous) throw new IllegalArgumentException("Overlapping template levels: " + name);
                    previous = level.max;
                }
                if (levels.isEmpty()) throw new IllegalArgumentException("Empty loot template: " + name);
                templates.put(name, new Template(List.copyOf(parameters), List.copyOf(levels)));
            }
        }

        var tables = new LinkedHashMap<String, Object>();
        var bindings = new LinkedHashMap<String, List<Variant>>();
        for (var entry : document.entrySet()) {
            if ("templates".equals(entry.getKey())) continue;
            if (!(entry.getKey() instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Loot table name must be a nonempty string");
            }
            if (!(entry.getValue() instanceof Map<?, ?> binding)) {
                if (tables.putIfAbsent(name, entry.getValue()) != null) {
                    throw new IllegalArgumentException("Duplicate loot table: " + name);
                }
                continue;
            }
            if (binding.size() != 2 || !(binding.get("template") instanceof String templateName)
                    || !(binding.get("parameters") instanceof Map<?, ?> parameters)) {
                throw new IllegalArgumentException("Expected loot binding {template, parameters}: " + name);
            }
            var template = templates.get(templateName);
            if (template == null) throw new IllegalArgumentException("Unknown loot template: " + templateName);
            if (!new HashSet<>(template.parameters).equals(parameters.keySet())) {
                throw new IllegalArgumentException("Loot template parameters do not match: " + name);
            }
            var arguments = new LinkedHashMap<String, String>();
            for (String key : template.parameters) {
                if (!(parameters.get(key) instanceof String value)) {
                    throw new IllegalArgumentException("Expected named inventory resource for " + name + "." + key);
                }
                var resource = ResourceId.parse(value);
                if (resource.size() < 3 || !"genshin".equals(resource.segment(0))) {
                    throw new IllegalArgumentException("Expected qualified item or family: " + value);
                }
                arguments.put(key, value);
            }
            var variants = new ArrayList<Variant>();
            var shared = new IdentityHashMap<Object, Object>();
            for (var level : template.levels) {
                String local = name + ".level_" + level.min;
                if (document.containsKey(local) || tables.containsKey(local)) {
                    throw new IllegalArgumentException("Generated loot table collides with configured name: " + local);
                }
                tables.put(local, substitute(level.entries, arguments, shared, 0));
                variants.add(new Variant(level.min, level.max, ResourceId.parse(namespace + ".loot." + local).toString()));
                if (tables.size() > MAX_TABLES) throw new IllegalArgumentException("Too many expanded loot tables");
            }
            bindings.put(ResourceId.parse(namespace + ".loot." + name).toString(), List.copyOf(variants));
        }
        return new Expanded(tables, bindings);
    }

    private static Object substitute(Object raw, Map<String, String> parameters,
            IdentityHashMap<Object, Object> shared, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Loot template nesting exceeds 64 levels");
        if (raw instanceof List<?> list) {
            if (shared.containsKey(raw)) return shared.get(raw);
            var result = new ArrayList<Object>();
            shared.put(raw, result);
            for (Object value : list) result.add(substitute(value, parameters, shared, depth + 1));
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            if (shared.containsKey(raw)) return shared.get(raw);
            var result = new LinkedHashMap<Object, Object>();
            shared.put(raw, result);
            map.forEach((key, value) -> result.put(key, substitute(value, parameters, shared, depth + 1)));
            return result;
        }
        if (raw instanceof String text && text.contains("${")) {
            var matcher = PLACEHOLDER.matcher(text);
            var result = new StringBuffer();
            while (matcher.find()) {
                String replacement = parameters.get(matcher.group(1));
                if (replacement == null) throw new IllegalArgumentException("Unknown loot item parameter: " + matcher.group(1));
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);
            if (result.indexOf("${") >= 0) throw new IllegalArgumentException("Invalid loot item parameter: " + text);
            return result.toString();
        }
        return raw;
    }
}
