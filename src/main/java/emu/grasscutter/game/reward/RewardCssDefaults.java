package emu.grasscutter.game.reward;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Supplies missing fields from bundled CSS in memory without modifying the user's file.
 *
 * <p>Missing base and pool=missing reward fields inherit bundled defaults in memory.
 * More-specific user selectors remain untouched, and explicit zeroes are kept.
 * Compatibility defaults are inserted BEFORE the user's CSS, so explicit
 * user declarations always win on equal specificity.
 */
final class RewardCssDefaults {
    private RewardCssDefaults() {}

    static String overlayMissing(String operatorCss, String bundledCss) {
        var existing = RewardConfig.parse(operatorCss);
        var defaults = RewardConfig.parse(bundledCss);

        var additions = new ArrayList<String>();
        for (var kind : ChestRewardConfig.Kind.values()) {
            // Standard base fields and missing-native compatibility fields are
            // separate CSS rules. Only inherit absent fields; never clobber an
            // operator's explicit values (including zero).
            for (boolean missingPool : new boolean[] {false, true}) {
                var current = existing.chest().resolve(kind, 0, 0, 0, missingPool).amounts();
                var baseline = defaults.chest().resolve(kind, 0, 0, 0, missingPool).amounts();
                var missing = new TreeMap<Integer, Integer>();
                for (Map.Entry<Integer, Integer> entry : baseline.entrySet()) {
                    if (!current.containsKey(entry.getKey())) {
                        missing.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!missing.isEmpty()) {
                    var rule = new StringBuilder("chest[kind=\"")
                            .append(kind.selectorValue()).append("\"]");
                    if (missingPool) rule.append("[pool=\"missing\"]");
                    rule.append(" {\n");
                    missing.forEach((id, amount) -> rule.append("    ")
                            .append(propertyName(id)).append(": ")
                            .append(amount).append(";\n"));
                    additions.add(rule.append("}\n").toString());
                }
            }
        }
        if (additions.isEmpty()) return operatorCss;

        String effectiveCss = "/* Runtime-only defaults; operator CSS remains unchanged. */\n"
                + String.join("\n", additions)
                + "\n" + operatorCss;

        var result = RewardConfig.parse(effectiveCss);
        result.chest().requireCompleteBaseRewards();
        return effectiveCss;
    }

    private static String propertyName(int id) {
        return switch (id) {
            case DirectReward.REGIONAL_SIGILS -> "sigils";
            case 102 -> "adventureExp";
            case 201 -> "primogems";
            case 202 -> "mora";
            default -> "item-" + id;
        };
    }
}
