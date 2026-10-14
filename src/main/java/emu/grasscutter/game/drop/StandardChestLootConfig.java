package emu.grasscutter.game.drop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.data.common.DropItemData;
import emu.grasscutter.data.excels.DropTableData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Operator-owned snapshot of the actual loaded ordinary-chest drop graph.
 *
 * <p>The first launch exports four chest qualities, including regional/level
 * roots and every reachable random subtable. Subsequent launches load that
 * file, not the bundled random pools. Special chests and boss rewards still
 * use the original DropSystem tables. rewards.css separately owns fixed
 * experience/Primogem amounts and explicit item overrides.
 */
final class StandardChestLootConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern STANDARD_TAG = Pattern.compile(
            "^(?:雪山)?(?:解谜|战斗)(低级|中级|高级|超级)"
                    + "(?:蒙德|璃月|稻妻|须弥|枫丹|纳塔|至冬|挪德卡莱|诺德克莱|群岛)$");
    private static final List<String> KINDS =
            List.of("common", "exquisite", "precious", "luxurious");

    record Source(String tag, int minLevel, int dropId) {}
    record Drop(int itemId, String countRange, int weight) {}
    record Table(int id, int randomType, boolean fallToGround, List<Drop> dropVec) {}
    record Quality(List<Source> sources, List<Table> tables) {}
    record Document(Map<String, Quality> chests) {}
    record ItemDrop(int itemId, int count) {}
    record Roll(List<ItemDrop> drops, boolean fallToGround) {
        Roll { drops = List.copyOf(drops); }

        Map<Integer, Integer> amounts() {
            var summed = new LinkedHashMap<Integer, Integer>();
            drops.forEach(drop -> summed.merge(drop.itemId(), drop.count(), Math::addExact));
            return Map.copyOf(summed);
        }
    }

    private final Map<String, Map<String, List<Source>>> sources;
    private final Map<String, Map<Integer, Table>> tables;

    StandardChestLootConfig(Document doc) {
        if (doc == null || doc.chests() == null || !doc.chests().keySet().equals(Set.copyOf(KINDS))) {
            throw new IllegalArgumentException("chest-loot.json requires exactly four chest qualities");
        }
        var sourceMap = new HashMap<String, Map<String, List<Source>>>();
        var tableMap = new HashMap<String, Map<Integer, Table>>();
        for (String kind : KINDS) {
            Quality quality = doc.chests().get(kind);
            if (quality == null || quality.sources() == null || quality.tables() == null) {
                throw new IllegalArgumentException("Missing chest source/table lists for " + kind);
            }
            var byTag = new HashMap<String, List<Source>>();
            var seenSources = new HashSet<String>();
            for (Source source : quality.sources()) {
                if (source == null || source.tag() == null || source.minLevel() < 1
                        || source.dropId() < 1 || !kind.equals(kindOf(source.tag()))
                        || !seenSources.add(source.tag() + "/" + source.minLevel())) {
                    throw new IllegalArgumentException("Invalid or duplicate " + kind + " chest source: " + source);
                }
                byTag.computeIfAbsent(source.tag(), k -> new ArrayList<>()).add(source);
            }
            byTag.replaceAll((k, v) -> v.stream()
                    .sorted(Comparator.comparingInt(Source::minLevel)).toList());
            var byId = new HashMap<Integer, Table>();
            for (Table table : quality.tables()) {
                if (table == null || table.id() < 1 || (table.randomType() != 0 && table.randomType() != 1)
                        || table.dropVec() == null || byId.putIfAbsent(table.id(), table) != null) {
                    throw new IllegalArgumentException("Invalid or duplicate " + kind + " drop table: " + table);
                }
                long totalWeight = 0;
                for (Drop drop : table.dropVec()) {
                    if (drop == null || drop.itemId() < 0 || drop.weight() < 0) {
                        throw new IllegalArgumentException("Invalid drop in " + kind + " table " + table.id());
                    }
                    // A zero-ID entry never rolls; native data may omit its count range.
                    if (drop.itemId() != 0) {
                        checkCountRange(drop.countRange(), kind + " table " + table.id());
                    }
                    if (drop.itemId() != 0) totalWeight += drop.weight();
                }
                if (totalWeight > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Drop weight overflow in " + table.id());
                }
            }
            sourceMap.put(kind, Map.copyOf(byTag));
            tableMap.put(kind, Map.copyOf(byId));
        }
        sources = Map.copyOf(sourceMap);
        tables = Map.copyOf(tableMap);
    }

    static StandardChestLootConfig loadOrCreate(
            Path path, Map<String, List<BaseDropData>> nativeSources,
            Map<Integer, DropTableData> nativeTables) throws IOException {
        Objects.requireNonNull(path);
        if (Files.notExists(path)) {
            Document defaults = export(nativeSources, nativeTables);
            boolean hasTables = defaults.chests().values().stream()
                    .anyMatch(quality -> !quality.tables().isEmpty());
            if (!hasTables) {
                throw new IllegalStateException(
                        "Cannot generate chest-loot.json: ordinary native chest tables are unavailable");
            }
            var parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, toJson(defaults) + "\n", StandardCharsets.UTF_8);
        }
        try {
            return new StandardChestLootConfig(
                    fromJson(Files.readString(path, StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Gson 2.9 (the version pinned by AstaPS) cannot deserialize Java records.
     * Read/write our explicit JSON schema rather than relying on record reflection.
     */
    static String toJson(Document document) {
        var root = new JsonObject();
        var kinds = new JsonObject();
        for (String kind : KINDS) {
            var quality = document.chests().get(kind);
            var qualityJson = new JsonObject();
            var sourceJson = new JsonArray();
            for (Source source : quality.sources()) {
                var entry = new JsonObject();
                entry.addProperty("tag", source.tag());
                entry.addProperty("minLevel", source.minLevel());
                entry.addProperty("dropId", source.dropId());
                sourceJson.add(entry);
            }
            qualityJson.add("sources", sourceJson);
            var tableJson = new JsonArray();
            for (Table table : quality.tables()) {
                var entry = new JsonObject();
                entry.addProperty("id", table.id());
                entry.addProperty("randomType", table.randomType());
                entry.addProperty("fallToGround", table.fallToGround());
                var dropJson = new JsonArray();
                for (Drop drop : table.dropVec()) {
                    var item = new JsonObject();
                    item.addProperty("itemId", drop.itemId());
                    if (drop.countRange() != null) {
                        item.addProperty("countRange", drop.countRange());
                    }
                    item.addProperty("weight", drop.weight());
                    dropJson.add(item);
                }
                entry.add("dropVec", dropJson);
                tableJson.add(entry);
            }
            qualityJson.add("tables", tableJson);
            kinds.add(kind, qualityJson);
        }
        root.add("chests", kinds);
        return JSON.toJson(root);
    }

    static Document fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject kinds = root.getAsJsonObject("chests");
        if (kinds == null || !kinds.keySet().equals(Set.copyOf(KINDS))) {
            throw new IllegalArgumentException("Expected four chest kinds under chests");
        }
        var result = new LinkedHashMap<String, Quality>();
        for (String kind : KINDS) {
            JsonObject quality = kinds.getAsJsonObject(kind);
            if (quality == null) throw new IllegalArgumentException("Missing " + kind + " quality");
            var sources = new ArrayList<Source>();
            for (JsonElement e : quality.getAsJsonArray("sources")) {
                var source = e.getAsJsonObject();
                sources.add(new Source(
                        source.get("tag").getAsString(), source.get("minLevel").getAsInt(),
                        source.get("dropId").getAsInt()));
            }
            var tables = new ArrayList<Table>();
            for (JsonElement e : quality.getAsJsonArray("tables")) {
                var table = e.getAsJsonObject();
                var drops = new ArrayList<Drop>();
                for (JsonElement element : table.getAsJsonArray("dropVec")) {
                    var drop = element.getAsJsonObject();
                    JsonElement countRange = drop.get("countRange");
                    drops.add(new Drop(drop.get("itemId").getAsInt(),
                            countRange == null || countRange.isJsonNull()
                                    ? null : countRange.getAsString(),
                            drop.get("weight").getAsInt()));
                }
                tables.add(new Table(
                        table.get("id").getAsInt(), table.get("randomType").getAsInt(),
                        table.has("fallToGround") && table.get("fallToGround").getAsBoolean(),
                        drops));
            }
            result.put(kind, new Quality(sources, tables));
        }
        return new Document(result);
    }

    /** The exported numbers describe the server's loaded tables, not audited 7.1 client odds. */
    static Document export(
            Map<String, List<BaseDropData>> nativeSources,
            Map<Integer, DropTableData> nativeTables) {
        var result = new LinkedHashMap<String, Quality>();
        for (String kind : KINDS) {
            var sources = new ArrayList<Source>();
            var tables = new TreeMap<Integer, Table>();
            nativeSources.forEach((tag, entries) -> {
                if (!kind.equals(kindOf(tag))) return;
                for (BaseDropData entry : entries) {
                    if (entry.getMinLevel() < 1 || entry.getDropId() < 1) continue;
                    sources.add(new Source(tag, entry.getMinLevel(), entry.getDropId()));
                    collect(entry.getDropId(), nativeTables, tables);
                }
            });
            sources.sort(Comparator.comparing(Source::tag).thenComparingInt(Source::minLevel));
            result.put(kind, new Quality(List.copyOf(sources), List.copyOf(tables.values())));
        }
        return new Document(result);
    }

    private static void collect(int id, Map<Integer, DropTableData> nativeTables,
                                Map<Integer, Table> result) {
        if (result.containsKey(id)) return;
        var nativeTable = nativeTables.get(id);
        if (nativeTable == null) return;
        var drops = new ArrayList<Drop>();
        if (nativeTable.getDropVec() != null) {
            for (DropItemData item : nativeTable.getDropVec()) {
                if (item != null) drops.add(new Drop(
                        item.getId(), item.getCountRange(), item.getWeight()));
            }
        }
        result.put(id, new Table(id, nativeTable.getRandomType(),
                nativeTable.isFallToGround(), List.copyOf(drops)));
        for (Drop item : drops) {
            if (nativeTables.containsKey(item.itemId())) {
                collect(item.itemId(), nativeTables, result);
            }
        }
    }

    private static String kindOf(String tag) {
        if (tag == null) return null;
        var match = STANDARD_TAG.matcher(tag);
        if (!match.matches()) return null;
        return switch (match.group(1)) {
            case "低级" -> "common";
            case "中级" -> "exquisite";
            case "高级" -> "precious";
            case "超级" -> "luxurious";
            default -> null;
        };
    }

    Roll roll(String tag, int level, Random random) {
        String kind = kindOf(tag);
        if (kind == null) return null;
        List<Source> choices = sources.get(kind).get(tag);
        if (choices == null) return null;
        Source selected = null;
        // Same minLevel threshold rule as DropSystem.queryDropData.
        for (Source candidate : choices) {
            if (level >= candidate.minLevel()
                    && (selected == null || candidate.minLevel() > selected.minLevel())) {
                selected = candidate;
            }
        }
        if (selected == null) return null;
        var lookup = tables.get(kind);
        Table root = lookup.get(selected.dropId());
        // An explicitly empty/zero-weight configured pool is valid; it must
        // not trigger pool=missing compatibility rewards.
        if (root == null) return null;
        var drops = new ArrayList<ItemDrop>();
        rollNode(root, 1, lookup, random, drops, new HashSet<>());
        return new Roll(drops, root.fallToGround());
    }

    private static void rollNode(Table table, int times, Map<Integer, Table> lookup,
                                 Random random, List<ItemDrop> result, Set<Integer> stack) {
        if (!stack.add(table.id())) {
            throw new IllegalArgumentException("Cyclic chest drop table " + table.id());
        }
        try {
            for (int n = 0; n < times; n++) {
                if (table.randomType() == 0) {
                    int weightSum = table.dropVec().stream()
                            .filter(e -> e.itemId() != 0).mapToInt(Drop::weight).sum();
                    if (weightSum == 0) continue;
                    int ticket = random.nextInt(weightSum);
                    for (Drop entry : table.dropVec()) {
                        if (entry.itemId() == 0) continue;
                        ticket -= entry.weight();
                        if (ticket < 0) {
                            deliver(entry, lookup, random, result, stack);
                            break;
                        }
                    }
                } else {
                    for (Drop entry : table.dropVec()) {
                        if (entry.itemId() != 0 && random.nextInt(10000) < entry.weight()) {
                            deliver(entry, lookup, random, result, stack);
                        }
                    }
                }
            }
        } finally {
            stack.remove(table.id());
        }
    }

    private static void deliver(Drop drop, Map<Integer, Table> lookup, Random random,
                                List<ItemDrop> result, Set<Integer> stack) {
        int count = amount(drop.countRange(), random);
        if (count <= 0) return;
        var child = lookup.get(drop.itemId());
        if (child != null) {
            rollNode(child, count, lookup, random, result, stack);
            return;
        }
        result.add(new ItemDrop(drop.itemId(), count));
    }

    private static void checkCountRange(String range, String context) {
        if (range == null || !range.matches("(?:[0-9]+(?:;[0-9]+)?|[0-9]+\\.[0-9]+)")) {
            throw new IllegalArgumentException("Invalid countRange in " + context + ": " + range);
        }
        if (range.contains(";")) {
            String[] parts = range.split(";");
            int min = Integer.parseInt(parts[0]), max = Integer.parseInt(parts[1]);
            if (max < min || max == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid countRange in " + context + ": " + range);
            }
        } else if (range.contains(".")) {
            double value = Double.parseDouble(range);
            if (!Double.isFinite(value) || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid countRange in " + context + ": " + range);
            }
        } else {
            Integer.parseInt(range);
        }
    }

    private static int amount(String range, Random random) {
        if (range.contains(";")) {
            String[] parts = range.split(";");
            int min = Integer.parseInt(parts[0]);
            return random.nextInt(min, Integer.parseInt(parts[1]) + 1);
        }
        if (range.contains(".")) {
            double expected = Double.parseDouble(range);
            int base = (int) expected;
            return random.nextDouble() < expected - base ? base + 1 : base;
        }
        return Integer.parseInt(range);
    }
}
