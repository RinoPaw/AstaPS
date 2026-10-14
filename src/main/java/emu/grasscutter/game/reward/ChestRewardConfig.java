package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Operator-owned fixed rewards for the four standard exploration chest qualities.
 *
 * <p>This class parses and selects rewards; it does not classify gadgets, roll random drops, or
 * grant items. The entire winning table replaces the fallback table. A missing reward field means
 * zero, not inheritance.
 */
public final class ChestRewardConfig {
    public enum Kind {
        COMMON("common"),
        EXQUISITE("exquisite"),
        PRECIOUS("precious"),
        LUXURIOUS("luxurious");

        private final String value;

        Kind(String value) {
            this.value = value;
        }

        public String selectorValue() {
            return value;
        }
    }

    /** Full selected rule, including the original selector for diagnostics. */
    public record Match(String selector, List<ItemParamData> rewards) {
        public Match {
            rewards = List.copyOf(rewards);
        }
    }

    private record AreaKey(Kind kind, int sceneId, int level1, int level2) {}

    private static final String TEMPLATE = "/rewards.toml";
    private static final Pattern SELECTOR =
            Pattern.compile(
                    "^kind=(common|exquisite|precious|luxurious)"
                            + "(?:,area=([1-9][0-9]*):([1-9][0-9]*)(?::([1-9][0-9]*))?)?$");
    private static final Set<String> REWARD_FIELDS =
            Set.of("adventureExp", "primogems", "mora", "items");

    private static volatile ChestRewardConfig active;

    private final Map<Kind, Match> defaults;
    private final Map<AreaKey, Match> regions;

    private ChestRewardConfig(Map<Kind, Match> defaults, Map<AreaKey, Match> regions) {
        this.defaults = Map.copyOf(defaults);
        this.regions = Map.copyOf(regions);
    }

    /**
     * Create the root-level operator file only if it is missing. Parse and validate the entire file
     * before publishing an immutable configuration. Invalid edits never reset it to defaults.
     */
    public static void loadOrCreate(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            if (Files.notExists(path)) {
                try (InputStream stream = ChestRewardConfig.class.getResourceAsStream(TEMPLATE)) {
                    if (stream == null) {
                        throw new IOException("Bundled " + TEMPLATE + " template is missing");
                    }
                    Files.copy(stream, path);
                }
            }
            var replacement = parse(Files.readString(path));
            active = replacement;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }

    public static ChestRewardConfig current() {
        var value = active;
        if (value == null) throw new IllegalStateException("rewards.toml has not been loaded");
        return value;
    }

    public static ChestRewardConfig parse(String source) {
        TomlParseResult root = Toml.parse(source);
        if (root.hasErrors()) {
            throw new IllegalArgumentException("Invalid rewards.toml syntax: " + root.errors());
        }
        if (!root.keySet().equals(Set.of("chest")) || !(root.get("chest") instanceof TomlTable chest)) {
            throw new IllegalArgumentException(
                    "rewards.toml must contain only the [chest] selector tables");
        }

        var defaults = new EnumMap<Kind, Match>(Kind.class);
        var regions = new HashMap<AreaKey, Match>();

        for (var entry : chest.entrySet()) {
            String selector = entry.getKey();
            Matcher parsed = SELECTOR.matcher(selector);
            if (!parsed.matches()) {
                throw new IllegalArgumentException(
                        "Unsupported chest selector [chest.\"" + selector
                                + "\"]; expected kind=<quality>[,area=<scene>:<level1>[:<level2>]]");
            }
            if (!(entry.getValue() instanceof TomlTable table)) {
                throw new IllegalArgumentException("Expected reward table for [chest.\"" + selector + "\"]");
            }

            Kind kind = Kind.valueOf(parsed.group(1).toUpperCase(java.util.Locale.ROOT));
            Match match = new Match(selector, parseDirectReward(selector, table));

            if (parsed.group(2) == null) {
                if (defaults.putIfAbsent(kind, match) != null) {
                    throw new IllegalArgumentException("Duplicate chest selector: " + selector);
                }
                continue;
            }

            int sceneId = positiveId(parsed.group(2), selector);
            int level1 = positiveId(parsed.group(3), selector);
            int level2 = parsed.group(4) == null ? 0 : positiveId(parsed.group(4), selector);
            if (regions.putIfAbsent(new AreaKey(kind, sceneId, level1, level2), match) != null) {
                throw new IllegalArgumentException("Duplicate chest selector: " + selector);
            }
        }

        for (Kind kind : Kind.values()) {
            if (!defaults.containsKey(kind)) {
                throw new IllegalArgumentException(
                        "Missing [chest.\"kind=" + kind.selectorValue() + "\"] in rewards.toml");
            }
        }
        return new ChestRewardConfig(defaults, regions);
    }

    private static int positiveId(String text, String selector) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Area ID is outside the supported 32-bit range in " + selector, e);
        }
    }

    /**
     * Exact level-2 area first; then level-1; finally kind default.
     * Zero level-2 indicates an unknown child and never matches a child-specific rule.
     */
    public Match resolve(Kind kind, int sceneId, int level1, int level2) {
        Objects.requireNonNull(kind, "kind");
        if (sceneId < 0 || level1 < 0 || level2 < 0) {
            throw new IllegalArgumentException("Scene and area IDs cannot be negative");
        }
        if (sceneId > 0 && level1 > 0) {
            if (level2 > 0) {
                Match child = regions.get(new AreaKey(kind, sceneId, level1, level2));
                if (child != null) return child;
            }
            Match parent = regions.get(new AreaKey(kind, sceneId, level1, 0));
            if (parent != null) return parent;
        }
        return defaults.get(kind);
    }

    private static List<ItemParamData> parseDirectReward(String selector, TomlTable table) {
        String context = "[chest.\"" + selector + "\"]";
        for (String field : table.keySet()) {
            if (!REWARD_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Unknown reward field '" + field + "' in " + context);
            }
        }

        var items = new ArrayList<ItemParamData>();
        var seen = new HashSet<Integer>();
        direct(items, seen, 102, amount(table, "adventureExp", context, false));
        direct(items, seen, 201, amount(table, "primogems", context, false));
        direct(items, seen, 202, amount(table, "mora", context, false));

        Object raw = table.get("items");
        if (raw != null) {
            if (!(raw instanceof TomlArray array)) {
                throw new IllegalArgumentException(context + ".items must be an array");
            }
            for (int i = 0; i < array.size(); ++i) {
                String entry = context + ".items[" + i + "]";
                if (!(array.get(i) instanceof TomlTable item)
                        || !item.keySet().equals(Set.of("id", "count"))) {
                    throw new IllegalArgumentException(
                            entry + " must have exactly integer 'id' and 'count' fields");
                }
                int id = amount(item, "id", entry, true);
                int count = amount(item, "count", entry, true);
                if (!seen.add(id)) {
                    throw new IllegalArgumentException(
                            "Duplicate item ID " + id + " in " + context);
                }
                items.add(new ItemParamData(id, count));
            }
        }
        return List.copyOf(items);
    }

    private static int amount(TomlTable table, String key, String context, boolean positive) {
        Object raw = table.get(key);
        if (raw == null && !positive) return 0;
        if (!(raw instanceof Long value)
                || value > Integer.MAX_VALUE
                || (positive ? value <= 0 : value < 0)) {
            throw new IllegalArgumentException(
                    context + "." + key + " must be a "
                            + (positive ? "positive" : "non-negative") + " 32-bit integer");
        }
        return value.intValue();
    }

    private static void direct(List<ItemParamData> out, Set<Integer> seen, int id, int count) {
        if (count > 0) {
            seen.add(id);
            out.add(new ItemParamData(id, count));
        }
    }
}
