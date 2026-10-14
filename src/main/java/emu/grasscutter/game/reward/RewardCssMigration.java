package emu.grasscutter.game.reward;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Upgrades older operator-owned CSS without replacing any existing rules.
 *
 * <p>Only missing base reward fields are inherited from the bundled template.
 * More-specific user selectors remain untouched, and explicit zeroes are kept.
 * Migration rules are inserted BEFORE the user's CSS so equal-specificity
 * user declarations win.
 */
final class RewardCssMigration {
    private RewardCssMigration() {}

    static String migrate(String operatorCss, String bundledCss) {
        var existing = RewardConfig.parse(operatorCss);
        var defaults = RewardConfig.parse(bundledCss);

        var additions = new ArrayList<String>();
        for (var kind : ChestRewardConfig.Kind.values()) {
            var current = existing.chest().resolve(kind, 0, 0, 0).amounts();
            var baseline = defaults.chest().resolve(kind, 0, 0, 0).amounts();
            var missing = new TreeMap<Integer, Integer>();
            for (Map.Entry<Integer, Integer> entry : baseline.entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    missing.put(entry.getKey(), entry.getValue());
                }
            }
            if (!missing.isEmpty()) {
                var rule = new StringBuilder("chest[kind=\"")
                        .append(kind.selectorValue()).append("\"] {\n");
                missing.forEach((id, amount) -> rule.append("    ")
                        .append(propertyName(id)).append(": ")
                        .append(amount).append(";\n"));
                additions.add(rule.append("}\n").toString());
            }
        }
        if (additions.isEmpty()) return operatorCss;

        String migrated = "/* Added by AstaPS to fill missing chest reward defaults. */\n"
                + "/* Your original rules follow unchanged; explicit values (including 0) win. */\n"
                + String.join("\n", additions)
                + "\n" + operatorCss;

        // Validate the final combination before any operator file is touched.
        var result = RewardConfig.parse(migrated);
        result.chest().requireCompleteBaseRewards();
        return migrated;
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
