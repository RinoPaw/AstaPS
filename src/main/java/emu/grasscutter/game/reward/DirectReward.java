package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** A single direct reward: only amounts and explicit items, never routing or drop rules. */
final class DirectReward {
    private static final Set<String> KEYS = Set.of("adventureExp", "primogems", "mora", "items");

    private DirectReward() {}

    static List<ItemParamData> parse(String selector, TomlTable table) {
        for (String field : table.keySet()) {
            if (!KEYS.contains(field)) {
                throw new IllegalArgumentException("Unknown reward field " + field + " in " + selector);
            }
        }
        var result = new ArrayList<ItemParamData>();
        add(result, 102, number(table, "adventureExp", selector, false));
        add(result, 201, number(table, "primogems", selector, false));
        add(result, 202, number(table, "mora", selector, false));
        Object rawItems = table.get("items");
        if (rawItems != null) {
            if (!(rawItems instanceof TomlArray items)) {
                throw new IllegalArgumentException(selector + ".items must be an array");
            }
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof TomlTable item)) {
                    throw new IllegalArgumentException(selector + ".items[" + i + "] must be a table");
                }
                if (!item.keySet().equals(Set.of("id", "count"))) {
                    throw new IllegalArgumentException(selector + ".items[" + i + "] needs id and count");
                }
                int id = number(item, "id", selector, true);
                int count = number(item, "count", selector, true);
                result.add(new ItemParamData(id, count));
            }
        }
        return List.copyOf(result);
    }

    private static int number(TomlTable table, String field, String name, boolean positive) {
        Object value = table.get(field);
        if (value == null && !positive) return 0;
        if (!(value instanceof Long n) || n > Integer.MAX_VALUE
                || (positive ? n <= 0 : n < 0)) {
            throw new IllegalArgumentException(name + "." + field + " must be a "
                    + (positive ? "positive" : "non-negative") + " integer");
        }
        return n.intValue();
    }

    private static void add(List<ItemParamData> items, int id, int count) {
        if (count > 0) items.add(new ItemParamData(id, count));
    }
}
