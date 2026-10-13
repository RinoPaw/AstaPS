package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlTable;

/**
 * Direct, fixed contents of an identified exploration chest.
 * This class does not identify gadgets, select native drop roots or roll item pools.
 */
public final class ChestRewardConfig {
    public enum Kind {
        COMMON, EXQUISITE, PRECIOUS, LUXURIOUS, REMARKABLE
    }

    private final Map<Kind, List<ItemParamData>> rewards;

    private ChestRewardConfig(Map<Kind, List<ItemParamData>> rewards) {
        this.rewards = Map.copyOf(rewards);
    }

    static ChestRewardConfig parseTable(TomlTable table) {
        var results = new EnumMap<Kind, List<ItemParamData>>(Kind.class);
        for (var entry : table.entrySet()) {
            String selector = entry.getKey();
            if (!selector.startsWith("kind=")) {
                throw new IllegalArgumentException("Unsupported chest reward selector: " + selector);
            }
            Kind kind;
            try {
                kind = Kind.valueOf(selector.substring(5).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown chest kind: " + selector, ex);
            }
            if (!(entry.getValue() instanceof TomlTable reward)) {
                throw new IllegalArgumentException("Expected reward table for [chest.\"" + selector + "\"]");
            }
            results.put(kind, DirectReward.parse("chest." + selector, reward));
        }
        for (Kind kind : Kind.values()) {
            if (!results.containsKey(kind)) {
                throw new IllegalArgumentException("Missing [chest.\"kind="
                        + kind.name().toLowerCase(Locale.ROOT) + "\"]");
            }
        }
        return new ChestRewardConfig(results);
    }

    public List<ItemParamData> resolve(Kind kind) {
        return rewards.get(kind);
    }
}
