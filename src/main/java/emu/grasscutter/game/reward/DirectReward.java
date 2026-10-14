package emu.grasscutter.game.reward;

import com.helger.css.decl.CSSDeclaration;
import emu.grasscutter.data.common.ItemParamData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Typed reward declarations. Zero explicitly clears a previously defined amount. */
final class DirectReward {
    /** Resolved to the region's sigil item ID at delivery. */
    static final int REGIONAL_SIGILS = -1;

    private DirectReward() {}

    static Map<Integer,Integer> parse(List<CSSDeclaration> declarations, String context) {
        var values = new HashMap<Integer,Integer>();
        for (var declaration : declarations) {
            String field = declaration.getProperty();
            if (declaration.isImportant()) {
                throw new IllegalArgumentException("!important is not supported in " + context);
            }
            int id;
            if (field.equalsIgnoreCase("adventureExp") || field.equals("adventure-exp")) {
                id = 102;
            } else if (field.equalsIgnoreCase("primogems")) {
                id = 201;
            } else if (field.equalsIgnoreCase("mora")) {
                id = 202;
            } else if (field.equalsIgnoreCase("sigils")) {
                id = REGIONAL_SIGILS;
            } else if (field.startsWith("item-") && field.substring(5).matches("[1-9][0-9]*")) {
                try {
                    id = Integer.parseInt(field.substring(5));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid item ID in " + context + ": " + field, e);
                }
            } else {
                throw new IllegalArgumentException("Unknown reward property '" + field + "' in " + context);
            }
            String raw = declaration.getExpressionAsCSSString().trim();
            if (!raw.matches("[0-9]+")) {
                throw new IllegalArgumentException(
                        context + " " + field + " must be a non-negative integer, not '" + raw + "'");
            }
            int count;
            try {
                count = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Reward amount out of range in " + context, e);
            }
            if (values.putIfAbsent(id, count) != null) {
                throw new IllegalArgumentException("Duplicate reward item " + id + " in " + context);
            }
        }
        return Map.copyOf(values);
    }

    static List<ItemParamData> materialize(Map<Integer,Integer> values) {
        var items = new ArrayList<ItemParamData>();
        new TreeMap<>(values).forEach((id, count) -> {
            if (id > 0 && count > 0) items.add(new ItemParamData(id, count));
        });
        return List.copyOf(items);
    }
}
