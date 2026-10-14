package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Preview-only direct rewards for verified ordinary chests. */
public final class ChestRewardConfig {
    public enum Kind {
        COMMON("common"), EXQUISITE("exquisite"), PRECIOUS("precious"), LUXURIOUS("luxurious");
        private final String value;
        Kind(String value) { this.value = value; }
        public String selectorValue() { return value; }
    }

    /** Keep explicit zeroes for live payout; the preview list omits zero-count items. */
    public record Match(String selector, List<ItemParamData> rewards, Map<Integer, Integer> amounts) {
        public Match {
            rewards = List.copyOf(rewards);
            amounts = Map.copyOf(amounts);
        }
    }

    private final List<CssRewardRules.Rule> rules;
    private ChestRewardConfig(List<CssRewardRules.Rule> rules) { this.rules = rules; }

    public static ChestRewardConfig parse(String source) {
        return fromRules(CssRewardRules.parse(source));
    }

    static ChestRewardConfig fromRules(List<CssRewardRules.Rule> all) {
        var selected = CssRewardRules.ofType(all, "chest");
        CssRewardRules.requireDefaults(selected, "chest",
                "common", "exquisite", "precious", "luxurious");
        return new ChestRewardConfig(selected);
    }

    /** Specificity first, then source order; absent fields inherit and zero clears. */
    public Match resolve(Kind kind, int sceneId, int level1, int level2) {
        Objects.requireNonNull(kind, "kind");
        if (sceneId < 0 || level1 < 0 || level2 < 0) {
            throw new IllegalArgumentException("Scene and area IDs cannot be negative");
        }
        var properties = new HashMap<String,String>();
        properties.put("kind", kind.selectorValue());
        if (sceneId > 0) properties.put("scene", Integer.toString(sceneId));
        if (level1 > 0) properties.put("area1", Integer.toString(level1));
        if (level2 > 0) properties.put("area2", Integer.toString(level2));
        var target = CssRewardRules.target("chest", properties);
        var merged = new HashMap<Integer,Integer>();
        String lastSelector = null;
        for (var rule : rules) {
            if (rule.matches(target)) {
                merged.putAll(rule.amounts());
                lastSelector = rule.selector();
            }
        }
        if (lastSelector == null) throw new IllegalStateException("No chest default for " + kind);
        return new Match(lastSelector, DirectReward.materialize(merged), merged);
    }
}
