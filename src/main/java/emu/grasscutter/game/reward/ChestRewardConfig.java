package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Operator-owned fixed rewards for the four standard exploration chest qualities.
 *
 * <p>Rules are applied in the order in which they appear in rewards.toml. Later matching entries
 * override earlier values for the same item ID. Unspecified reward fields are inherited, whereas
 * explicitly setting an amount to zero removes that item from the final reward.
 *
 * <p>This class does not identify gadgets, roll random drops or grant items.
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

    /** Final merged reward and last matched selector for diagnostics. */
    public record Match(String selector, List<ItemParamData> rewards, Set<Integer> ownedItemIds) {
        public Match {
            rewards = List.copyOf(rewards);
            ownedItemIds = Set.copyOf(ownedItemIds);
        }
    }

    private record Rule(
            Kind kind, int sceneId, int level1, int level2, String selector,
            Map<Integer, Integer> overrides, int line, int column) {
        boolean matches(Kind requestedKind, int scene, int parent, int child) {
            if (kind != requestedKind) return false;
            if (sceneId == 0) return true;
            if (sceneId != scene || level1 != parent) return false;
            return level2 == 0 || (child > 0 && level2 == child);
        }
    }

    private static final Pattern SELECTOR =
            Pattern.compile(
                    "^kind=(common|exquisite|precious|luxurious)"
                            + "(?:,area=([1-9][0-9]*):([1-9][0-9]*)(?::([1-9][0-9]*))?)?$");

    private final List<Rule> rules;

    private ChestRewardConfig(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static ChestRewardConfig parse(String source) {
        TomlParseResult root = Toml.parse(source);
        if (root.hasErrors()) {
            throw new IllegalArgumentException("Invalid rewards.toml syntax: " + root.errors());
        }
        if (!root.keySet().equals(Set.of("chest"))) {
            throw new IllegalArgumentException("Standalone chest rules require only [chest] tables");
        }
        return parseTable(root);
    }

    /** The shared rewards.toml loader supplies the already parsed document. */
    static ChestRewardConfig parseTable(TomlParseResult root) {
        if (!(root.get("chest") instanceof TomlTable chest)) {
            throw new IllegalArgumentException("Missing [chest] section");
        }

        var seenDefaults = EnumSet.noneOf(Kind.class);
        var rules = new ArrayList<Rule>();

        for (var entry : chest.entrySet()) {
            String selector = entry.getKey();
            Matcher parsed = SELECTOR.matcher(selector);
            if (!parsed.matches()) {
                throw new IllegalArgumentException(
                        "Unsupported chest selector [chest.\"" + selector
                                + "\"]; expected kind=<quality>[,area=<scene>:<level1>[:<level2>]]");
            }
            if (!(entry.getValue() instanceof TomlTable table)) {
                throw new IllegalArgumentException(
                        "Expected reward table for [chest.\"" + selector + "\"]");
            }

            Kind kind = Kind.valueOf(parsed.group(1).toUpperCase(java.util.Locale.ROOT));
            int scene = parsed.group(2) == null ? 0 : positiveId(parsed.group(2), selector);
            int parent = parsed.group(3) == null ? 0 : positiveId(parsed.group(3), selector);
            int child = parsed.group(4) == null ? 0 : positiveId(parsed.group(4), selector);
            if (scene == 0 && !seenDefaults.add(kind)) {
                throw new IllegalArgumentException("Duplicate chest quality default: " + selector);
            }

            // TomlTable.entrySet() is not specified to iterate in source order. Read the actual
            // location recorded by tomlj and sort by that, so the last written rule really wins.
            var position = root.inputPositionOf(List.of("chest", selector));
            if (position == null) {
                throw new IllegalArgumentException(
                        "Missing source position for [chest.\"" + selector + "\"]");
            }
            rules.add(new Rule(
                    kind, scene, parent, child, selector, DirectReward.parse("[chest.\"" + selector + "\"]", table),
                    position.line(), position.column()));
        }

        for (Kind kind : Kind.values()) {
            if (!seenDefaults.contains(kind)) {
                throw new IllegalArgumentException(
                        "Missing [chest.\"kind=" + kind.selectorValue() + "\"] in rewards.toml");
            }
        }
        rules.sort(Comparator.comparingInt(Rule::line).thenComparingInt(Rule::column));
        return new ChestRewardConfig(rules);
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
     * Apply all matching selectors in written order. A later general selector may deliberately
     * override an earlier specific selector; specificity is never an implicit priority.
     */
    public Match resolve(Kind kind, int sceneId, int level1, int level2) {
        Objects.requireNonNull(kind, "kind");
        if (sceneId < 0 || level1 < 0 || level2 < 0) {
            throw new IllegalArgumentException("Scene and area IDs cannot be negative");
        }
        var reward = new TreeMap<Integer, Integer>();
        String lastSelector = null;
        for (Rule rule : rules) {
            if (!rule.matches(kind, sceneId, level1, level2)) continue;
            reward.putAll(rule.overrides());
            lastSelector = rule.selector();
        }
        if (lastSelector == null) {
            throw new IllegalStateException("No default rule for chest kind " + kind);
        }
        // Keep explicit zero overrides in the authority set. A cleared item must not
        // reappear via a native drop table during a future combined grant.
        return new Match(lastSelector, DirectReward.materialize(reward), reward.keySet());
    }

}
