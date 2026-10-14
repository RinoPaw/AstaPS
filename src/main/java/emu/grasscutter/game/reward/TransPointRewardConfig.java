package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.PointData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** First-unlock reward rules for waypoint, statue and domain from rewards.css. */
public final class TransPointRewardConfig {
    public enum Kind { WAYPOINT, STATUE, DOMAIN }

    private final List<CssRewardRules.Rule> rules;
    private TransPointRewardConfig(List<CssRewardRules.Rule> rules) { this.rules = rules; }

    public static TransPointRewardConfig current() {
        return RewardConfig.current().transPoint();
    }

    public static TransPointRewardConfig parse(String source) {
        return fromRules(CssRewardRules.parse(source));
    }

    static TransPointRewardConfig fromRules(List<CssRewardRules.Rule> all) {
        var selected = CssRewardRules.ofType(all, "transPoint");
        CssRewardRules.requireDefaults(selected, "transPoint",
                "waypoint", "statue", "domain");
        return new TransPointRewardConfig(selected);
    }

    /** Only authored selectors can modify reward fields; eligibility stays native. */
    public List<ItemParamData> resolve(int sceneId, int pointId, Kind kind) {
        Objects.requireNonNull(kind, "kind");
        if (sceneId < 0 || pointId < 0) {
            throw new IllegalArgumentException("Scene and point IDs cannot be negative");
        }
        var attributes = new HashMap<String,String>();
        attributes.put("kind", kind.name().toLowerCase(java.util.Locale.ROOT));
        if (sceneId > 0) attributes.put("scene", Integer.toString(sceneId));
        if (pointId > 0) attributes.put("point", Integer.toString(pointId));
        var element = CssRewardRules.target("transPoint", attributes);
        var values = new HashMap<Integer,Integer>();
        for (var rule : rules) {
            if (rule.matches(element)) values.putAll(rule.amounts());
        }
        return DirectReward.materialize(values);
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
