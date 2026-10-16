package emu.grasscutter.game.reward;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.game.drop.ArtifactResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Loaded after the game item resources; CSS owns chest and scene-point rewards. */
public final class RewardConfiguration {
    private static volatile RewardConfiguration active;

    private final YamlLootTables loot;
    private final CssRewards css;

    private RewardConfiguration(YamlLootTables loot, CssRewards css) {
        this.loot = loot;
        this.css = css;
    }

    /** Load the two authoritative classpath resources; no config/rewards directory. */
    public static void load() {
        try {
            var registry = ItemIdRegistry.fromGameResources();
            var loot = YamlLootTables.parse(readBundled("loot.yaml"), registry::resolve,
                    ArtifactResolver.fromGameResources());
            var css = CssRewards.parse(readBundled("rewards.css"), registry, loot);
            active = new RewardConfiguration(loot, css);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load bundled reward configuration", e);
        }
    }

    static String readBundled(String filename) throws IOException {
        try (InputStream stream = RewardConfiguration.class.getResourceAsStream("/rewards/" + filename)) {
            if (stream == null) throw new IOException("Missing bundled reward resource " + filename);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static RewardConfiguration current() {
        var value = active;
        if (value == null) throw new IllegalStateException("Reward configuration is not loaded");
        return value;
    }

    public Map<Integer, Integer> roll(
            String targetType, Map<String, String> properties, RandomGenerator random) {
        return roll(targetType, properties, random, RewardContext.NONE);
    }

    public Map<Integer, Integer> roll(
            String targetType, Map<String, String> properties, RandomGenerator random,
            RewardContext context) {
        var selected = css.resolve(targetType, properties);
        requireChestLoot(targetType, selected, properties);
        return RewardRoller.roll(selected, loot, random, context);
    }

    static void requireChestLoot(
            String targetType, CssRewards.Selection selected, Map<String, String> properties) {
        if ("chest".equals(targetType)
                && selected.loot().values().stream().noneMatch(count -> count > 0)
                && selected.rangedLoot().isEmpty()) {
            throw new IllegalStateException(
                    "No chest loot table matched (missing or invalid type/grade/level): " + properties);
        }
    }

    /** Qualified table IDs can be executed without CSS selection. */
    public Map<Integer, Integer> rollLoot(String resourceId, RandomGenerator random) {
        return RewardRoller.resolveRegionalItems(loot.roll(resourceId, random), RewardContext.NONE);
    }
}
