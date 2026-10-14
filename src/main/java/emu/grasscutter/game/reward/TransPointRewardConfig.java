package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.PointData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * First-unlock rewards for waypoints, statues and domain entrances, from the shared rewards.toml.
 *
 * <p>All matching tables are applied in written order; later fields override earlier fields,
 * including when the later selector is less specific. This does not change point eligibility.
 */
public final class TransPointRewardConfig {
    public enum Kind { WAYPOINT, STATUE, DOMAIN }

    private record Rule(
            Kind kind, int scene, int point, String selector,
            Map<Integer, Integer> overrides, int line, int column) {
        boolean matches(int sceneId, int pointId, Kind requestedKind) {
            if (kind != null) return kind == requestedKind;
            return scene == sceneId && (point == 0 || point == pointId);
        }
    }

    private static final Pattern LOCATION =
            Pattern.compile("^[1-9][0-9]*:([1-9][0-9]*|\\*)$");

    private final List<Rule> rules;

    private TransPointRewardConfig(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static TransPointRewardConfig current() {
        return RewardConfig.current().transPoint();
    }

    /** For pure unit tests; production loads the shared root-level rewards.toml once. */
    public static TransPointRewardConfig parse(String source) {
        return RewardConfig.parse(source).transPoint();
    }

    static TransPointRewardConfig parseTable(TomlParseResult root) {
        if (!(root.get("transPoint") instanceof TomlTable section)) {
            throw new IllegalArgumentException("Missing [transPoint] section");
        }

        var rules = new ArrayList<Rule>();
        var defaults = EnumSet.noneOf(Kind.class);
        for (var entry : section.entrySet()) {
            String selector = entry.getKey();
            if (!(entry.getValue() instanceof TomlTable table)) {
                throw new IllegalArgumentException(
                        "Expected reward table for [transPoint.\"" + selector + "\"]");
            }

            Kind kind = null;
            int scene = 0;
            int point = 0;
            if (selector.startsWith("kind=")) {
                kind = switch (selector) {
                    case "kind=waypoint" -> Kind.WAYPOINT;
                    case "kind=statue" -> Kind.STATUE;
                    case "kind=domain" -> Kind.DOMAIN;
                    default -> throw new IllegalArgumentException(
                            "Unknown transPoint selector: " + selector);
                };
                if (!defaults.add(kind)) {
                    throw new IllegalArgumentException(
                            "Duplicate transPoint kind rule: " + selector);
                }
            } else if (LOCATION.matcher(selector).matches()) {
                int separator = selector.indexOf(':');
                scene = positiveId(selector.substring(0, separator), selector);
                String second = selector.substring(separator + 1);
                point = "*".equals(second) ? 0 : positiveId(second, selector);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported transPoint selector: " + selector);
            }

            var position = root.inputPositionOf(List.of("transPoint", selector));
            if (position == null) {
                throw new IllegalArgumentException(
                        "Missing source position for [transPoint.\"" + selector + "\"]");
            }
            rules.add(new Rule(
                    kind, scene, point, selector,
                    DirectReward.parse("[transPoint.\"" + selector + "\"]", table),
                    position.line(), position.column()));
        }
        for (Kind kind : Kind.values()) {
            if (!defaults.contains(kind)) {
                throw new IllegalArgumentException(
                        "Missing [transPoint.\"kind="
                                + kind.name().toLowerCase(java.util.Locale.ROOT) + "\"]");
            }
        }
        rules.sort(Comparator.comparingInt(Rule::line).thenComparingInt(Rule::column));
        return new TransPointRewardConfig(rules);
    }

    private static int positiveId(String value, String selector) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "TransPoint selector ID out of 32-bit range: " + selector, e);
        }
    }

    /** Last matching fields win, even if a broad selector appears after a precise selector. */
    public List<ItemParamData> resolve(int sceneId, int pointId, Kind kind) {
        Objects.requireNonNull(kind, "kind");
        if (sceneId < 0 || pointId < 0) {
            throw new IllegalArgumentException("Scene and point IDs cannot be negative");
        }
        var direct = new TreeMap<Integer, Integer>();
        for (Rule rule : rules) {
            if (rule.matches(sceneId, pointId, kind)) {
                direct.putAll(rule.overrides());
            }
        }
        return DirectReward.materialize(direct);
    }

    /** Point subtype and native pre-unlock flags are eligibility, not reward configuration. */
    public static Kind kindOf(PointData point, boolean isStatue) {
        if (point == null || point.isUnlocked()) return null;
        // Recent statue resources may use an obfuscated $type rather than SceneTransPoint.
        if (isStatue && emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(point)) {
            return Kind.STATUE;
        }
        // Native DungeonEntry points are eligible on actual client activation.
        // rewardDataId in DungeonEntryExcel is not an eligibility switch: story
        // domains can have no native reward ID, while the operator owns the reward.
        if ("DungeonEntry".equals(point.getType())) {
            return "Never".equals(point.getMapVisibility()) ? null : Kind.DOMAIN;
        }
        if (!"SceneTransPoint".equals(point.getType())) return null;
        // PORTAL and unidentified TOWER subtypes are not ordinary waypoints.
        if (point.getPointType() != null) return null;
        if ("Never".equals(point.getMapVisibility())) return null;
        return Kind.WAYPOINT;
    }
}
