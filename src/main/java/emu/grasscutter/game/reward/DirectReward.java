package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Shared, typed direct-reward fields. Each matching table overrides only the fields it names. */
final class DirectReward {
    private static final Set<String> FIELDS = Set.of("adventureExp", "primogems", "mora", "items");

    private DirectReward() {}

    static Map<Integer, Integer> parse(String context, TomlTable table) {
        for (String field : table.keySet()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown reward field '" + field + "' in " + context);
            }
        }
        var entries = new HashMap<Integer, Integer>();
        if (table.contains("adventureExp")) entries.put(102, amount(table, "adventureExp", context, false));
        if (table.contains("primogems")) entries.put(201, amount(table, "primogems", context, false));
        if (table.contains("mora")) entries.put(202, amount(table, "mora", context, false));

        Object raw = table.get("items");
        if (raw != null) {
            if (!(raw instanceof TomlArray items)) {
                throw new IllegalArgumentException(context + ".items must be an array");
            }
            for (int i = 0; i < items.size(); i++) {
                String source = context + ".items[" + i + "]";
                if (!(items.get(i) instanceof TomlTable item)
                        || !item.keySet().equals(Set.of("id", "count"))) {
                    throw new IllegalArgumentException(
                            source + " must have exactly integer 'id' and 'count' fields");
                }
                int id = amount(item, "id", source, true);
                int count = amount(item, "count", source, false);
                if (entries.putIfAbsent(id, count) != null) {
                    throw new IllegalArgumentException("Duplicate item ID " + id + " in " + context);
                }
            }
        }
        return Map.copyOf(entries);
    }

    static List<ItemParamData> materialize(Map<Integer, Integer> values) {
        var result = new ArrayList<ItemParamData>();
        for (var entry : new TreeMap<>(values).entrySet()) {
            if (entry.getValue() > 0) result.add(new ItemParamData(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    private static int amount(TomlTable table, String key, String context, boolean positive) {
        Object raw = table.get(key);
        if (!(raw instanceof Long value)
                || value > Integer.MAX_VALUE
                || (positive ? value <= 0 : value < 0)) {
            throw new IllegalArgumentException(
                    context + "." + key + " must be a "
                            + (positive ? "positive" : "non-negative") + " 32-bit integer");
        }
        return value.intValue();
    }
}
