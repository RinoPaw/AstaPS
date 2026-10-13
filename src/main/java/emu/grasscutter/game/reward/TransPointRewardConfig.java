package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.PointData;
import org.tomlj.TomlTable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-owned first-unlock rewards from the transPoint section of rewards.toml. */
public final class TransPointRewardConfig {
    public enum Kind { WAYPOINT, STATUE, DOMAIN }

    private final Map<Kind, List<ItemParamData>> kinds;
    private final Map<Integer, List<ItemParamData>> scenes;
    private final Map<Long, List<ItemParamData>> points;

    private TransPointRewardConfig(
            Map<Kind, List<ItemParamData>> kinds,
            Map<Integer, List<ItemParamData>> scenes,
            Map<Long, List<ItemParamData>> points) {
        this.kinds = Map.copyOf(kinds);
        this.scenes = Map.copyOf(scenes);
        this.points = Map.copyOf(points);
    }

    /** Internal namespace parser, invoked by the unified root config. */
    static TransPointRewardConfig parseTable(TomlTable transPoints) {
        var kinds = new HashMap<Kind, List<ItemParamData>>();
        var scenes = new HashMap<Integer, List<ItemParamData>>();
        var points = new HashMap<Long, List<ItemParamData>>();
        for (var entry : transPoints.entrySet()) {
            String selector = entry.getKey();
            if (!(entry.getValue() instanceof TomlTable table)) {
                throw new IllegalArgumentException("Expected reward table for [" + selector + "]");
            }
            List<ItemParamData> reward = DirectReward.parse(selector, table);
            if (selector.startsWith("kind=")) {
                Kind kind = switch (selector.substring(5)) {
                    case "waypoint" -> Kind.WAYPOINT;
                    case "statue" -> Kind.STATUE;
                    case "domain" -> Kind.DOMAIN;
                    default -> throw new IllegalArgumentException("Unknown selector: " + selector);
                };
                kinds.put(kind, reward);
                continue;
            }
            if (!selector.matches("[1-9][0-9]*:([1-9][0-9]*|\\*)")) {
                throw new IllegalArgumentException("Unsupported selector: " + selector);
            }
            String[] pair = selector.split(":", 2);
            int sceneId;
            try {
                sceneId = Integer.parseInt(pair[0]);
                if ("*".equals(pair[1])) {
                    scenes.put(sceneId, reward);
                } else {
                    int pointId = Integer.parseInt(pair[1]);
                    points.put(key(sceneId, pointId), reward);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Selector ID is too large: " + selector, ex);
            }
        }
        for (Kind kind : Kind.values()) {
            if (!kinds.containsKey(kind)) {
                throw new IllegalArgumentException("Missing [transPoint.\"kind="
                        + kind.name().toLowerCase(java.util.Locale.ROOT) + "\"]");
            }
        }
        return new TransPointRewardConfig(kinds, scenes, points);
    }

    private static long key(int sceneId, int pointId) {
        return ((long) sceneId << 32) | (pointId & 0xffffffffL);
    }

    /** Exact point > scene > kind. A selected table replaces the entire reward. */
    public List<ItemParamData> resolve(int sceneId, int pointId, Kind kind) {
        var exact = points.get(key(sceneId, pointId));
        if (exact != null) return exact;
        var scene = scenes.get(sceneId);
        if (scene != null) return scene;
        return kinds.getOrDefault(kind, List.of());
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
