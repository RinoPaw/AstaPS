package emu.grasscutter.game.drop;

import emu.grasscutter.data.excels.DropTableData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Research/migration helper. Converts native DropTable roots to YAML loot lists,
 * preserving the independent versus exclusive rolls and nested references.
 *
 * <p>This does not automatically replace native authoritative data.
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
                table.getDropVec().forEach(drop -> drops.add(
                        new NativeEntry(drop.getId(), drop.getCountRange(), drop.getWeight())));
            }
            result.put(id, new NativeTable(table.getRandomType(), List.copyOf(drops)));
        });
        return Map.copyOf(result);
    }

    public static String export(Map<Integer, NativeTable> tables, Collection<Integer> roots) {
        var ordered = new ArrayList<Integer>();
        var visited = new HashSet<Integer>();
        var active = new HashSet<Integer>();
        for (int root : roots) visit(root, tables, visited, active, ordered);
        var yaml = new StringBuilder();
        for (int id : ordered) {
            var table = tables.get(id);
            if (table.randomType != 0 && table.randomType != 1) {
                throw new IllegalArgumentException("Unsupported native randomType for table " + id);
            }
            var valid = table.drops.stream()
                    .filter(entry -> entry.id > 0 && entry.weight > 0).toList();
            yaml.append("native_").append(id).append(": &n").append(id);
            if (valid.isEmpty()) {
                yaml.append(" []\n\n");
                continue;
            }
            yaml.append("\n");
            for (NativeEntry entry : valid) {
                LootCount.parse(entry.countRange);
                String target = tables.containsKey(entry.id)
                        ? "*n" + entry.id : Integer.toString(entry.id);
                String originalCount = entry.countRange;
                if (table.randomType == 0) {
                    yaml.append("  - {item: ").append(target).append(", count: \"")
                            .append(originalCount).append("\", weight: ")
                            .append(entry.weight).append("}\n");
                } else if (entry.weight == 10000) {
                    yaml.append("  - {item: ").append(target).append(", count: \"")
                            .append(originalCount).append("\"}\n");
                } else {
                    if (entry.weight > 10000) {
                        throw new IllegalArgumentException("Independent roll weight >10000 for table " + id);
                    }
                    String chance = BigDecimal.valueOf(entry.weight)
                            .movePointLeft(4).stripTrailingZeros().toPlainString();
                    if ("1".equals(originalCount)) {
                        yaml.append("  - {item: ").append(target).append(", count: \"")
                                .append(chance).append("\"}\n");
                    } else {
                        yaml.append("  - item:\n")
                                .append("      - {item: ").append(target).append(", count: \"")
                                .append(originalCount).append("\"}\n")
                                .append("    count: \"").append(chance).append("\"\n");
                    }
                }
            }
            yaml.append("\n");
        }
        return yaml.toString();
    }

    private static void visit(int id, Map<Integer, NativeTable> tables,
            Set<Integer> visited, Set<Integer> active, List<Integer> ordered) {
        if (!tables.containsKey(id)) throw new IllegalArgumentException("Unknown root " + id);
        if (visited.contains(id)) return;
        if (!active.add(id)) throw new IllegalArgumentException("Cyclic native table " + id);
        for (NativeEntry item : tables.get(id).drops) {
            if (item.id > 0 && item.weight > 0 && tables.containsKey(item.id)) {
                visit(item.id, tables, visited, active, ordered);
            }
        }
        active.remove(id);
        visited.add(id);
        ordered.add(id);
    }
}
