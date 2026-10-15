package emu.grasscutter.game.drop;

import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.excels.DropTableData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Historical source-table exporter. Callers must supply human-readable loot
 * table names and qualified item resource names. No numeric YAML identifiers.
 */
public final class NativeLootYamlExporter {
    public record NativeEntry(int id, String countRange, int weight) {}
    public record NativeTable(int randomType, List<NativeEntry> drops) {}
    private NativeLootYamlExporter() {}

    public static Map<Integer, NativeTable> fromResources(Map<Integer, DropTableData> resources) {
        var result = new HashMap<Integer, NativeTable>();
        resources.forEach((id, table) -> {
            var drops = new ArrayList<NativeEntry>();
            if (table.getDropVec() != null) {
                table.getDropVec().forEach(drop ->
                        drops.add(new NativeEntry(drop.getId(), drop.getCountRange(), drop.getWeight())));
            }
            result.put(id, new NativeTable(table.getRandomType(), List.copyOf(drops)));
        });
        return Map.copyOf(result);
    }

    /**
     * Names are provided by a verified source mapping. Unknown table or item
     * identities fail the export rather than producing synthetic ID aliases.
     */
    public static String export(
            Map<Integer, NativeTable> tables,
            Collection<Integer> roots,
            Map<Integer, String> tableNames,
            IntFunction<String> itemNames) {
        if (tableNames == null || itemNames == null) {
            throw new IllegalArgumentException("Missing resource name mappings");
        }
        var ordered = new ArrayList<Integer>();
        var visited = new HashSet<Integer>();
        var active = new HashSet<Integer>();
        for (int root : roots) visit(root, tables, visited, active, ordered);
        var yaml = new StringBuilder();
        var used = new HashSet<String>();

        for (int id : ordered) {
            String tableName = tableNames.get(id);
            if (tableName == null) throw new IllegalArgumentException("Unidentified loot table");
            new ResourceId("astaps", "loot", tableName);
            if (!used.add(tableName)) throw new IllegalArgumentException("Duplicate loot name: " + tableName);

            var table = tables.get(id);
            if (table.randomType != 0 && table.randomType != 1) {
                throw new IllegalArgumentException("Unsupported native random type");
            }
            var valid = table.drops.stream()
                    .filter(entry -> entry.id > 0 && entry.weight > 0).toList();
            yaml.append(tableName).append(": &").append(tableName);
            if (valid.isEmpty()) {
                yaml.append(" []\n\n");
                continue;
            }

            int divisor = 0;
            if (table.randomType == 0) {
                for (NativeEntry entry : valid) divisor = gcd(divisor, entry.weight);
            }
            yaml.append('\n');
            for (NativeEntry entry : valid) {
                LootCount.parse(entry.countRange);
                String target;
                if (tables.containsKey(entry.id)) {
                    target = tableNames.get(entry.id);
                    if (target == null) throw new IllegalArgumentException("Unidentified nested loot table");
                    new ResourceId("astaps", "loot", target);
                    target = "*" + target;
                } else {
                    target = itemNames.apply(entry.id);
                    if (target == null) throw new IllegalArgumentException("Unknown item in chest source");
                    ResourceId resource = ResourceId.parse(target);
                    if (!"genshin".equals(resource.namespace())
                            || !"item".equals(resource.type())
                            || resource.name().matches("id_[0-9]+")) {
                        throw new IllegalArgumentException("Expected semantic genshin.item name");
                    }
                }

                if (table.randomType == 0) {
                    appendEntry(yaml, "  ", target, entry.countRange,
                            entry.weight / divisor);
                } else if (entry.weight == 10000) {
                    appendEntry(yaml, "  ", target, entry.countRange, null);
                } else {
                    if (entry.weight > 10000) {
                        throw new IllegalArgumentException("Invalid independent roll probability");
                    }
                    String chance = BigDecimal.valueOf(entry.weight)
                            .movePointLeft(4).stripTrailingZeros().toPlainString();
                    if ("1".equals(entry.countRange)) {
                        appendEntry(yaml, "  ", target, chance, null);
                    } else {
                        yaml.append("  - reward:\n");
                        appendEntry(yaml, "    ", target, entry.countRange, null);
                        yaml.append("    count: \"").append(chance).append("\"\n");
                    }
                }
            }
            yaml.append('\n');
        }
        return yaml.toString();
    }

    private static void appendEntry(StringBuilder yaml, String indent,
            String target, String count, Integer weight) {
        yaml.append(indent).append("- {reward: ").append(target);
        if (!"1".equals(count)) {
            yaml.append(", count: \"").append(count).append('"');
        }
        if (weight != null) yaml.append(", weight: ").append(weight);
        yaml.append("}\n");
    }

    private static int gcd(int x, int y) {
        while (y != 0) {
            int remainder = x % y;
            x = y;
            y = remainder;
        }
        return x;
    }

    private static void visit(int id, Map<Integer, NativeTable> tables,
            Set<Integer> visited, Set<Integer> active, List<Integer> ordered) {
        if (!tables.containsKey(id)) throw new IllegalArgumentException("Unknown source root");
        if (visited.contains(id)) return;
        if (!active.add(id)) throw new IllegalArgumentException("Cyclic source table");
        for (NativeEntry entry : tables.get(id).drops) {
            if (entry.id > 0 && entry.weight > 0 && tables.containsKey(entry.id)) {
                visit(entry.id, tables, visited, active, ordered);
            }
        }
        active.remove(id);
        visited.add(id);
        ordered.add(id);
    }
}
