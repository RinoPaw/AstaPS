package emu.grasscutter.game.reward;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static void loadOrCreate(Path directory) {
        Path lootPath = directory.resolve("loot.yaml");
        Path cssPath = directory.resolve("rewards.css");
        try {
            Files.createDirectories(directory);
            copyTemplateIfMissing(lootPath, "/rewards/loot.yaml");
            copyTemplateIfMissing(cssPath, "/rewards/rewards.css");
            var registry = ItemIdRegistry.fromGameResources();
            String yaml = Files.readString(lootPath);
            String stylesheet = Files.readString(cssPath);
            rejectLegacyChestTemplates(yaml, stylesheet);
            var loot = YamlLootTables.parse(yaml, registry::resolve);
            var css = CssRewards.parse(stylesheet, registry, loot);
            active = new RewardConfiguration(loot, css);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load reward configuration from " + directory, e);
        }
    }

    // Previously generated configurations remain on disk after upgrades.
    // Prevent old selector data from silently continuing to grant old rewards.
    static void rejectLegacyChestTemplates(String yaml, String css) {
        if (yaml.contains("fallback_common:")
                || yaml.contains("native_51010000:")
                || css.contains("--loot: fallback_common")
                || css.contains("--loot: native_")) {
            throw new IllegalArgumentException(
                    "Obsolete chest reward configuration in config/rewards."
                            + " Back up and regenerate loot.yaml and rewards.css together.");
        }
    }

    private static void copyTemplateIfMissing(Path destination, String template) throws IOException {
        if (Files.exists(destination)) return;
        try (InputStream source = RewardConfiguration.class.getResourceAsStream(template)) {
            if (source == null) throw new IOException("Missing bundled resource " + template);
            Files.copy(source, destination);
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
        return RewardRoller.roll(css.resolve(targetType, properties), loot, random, context);
    }

    /** Qualified table IDs can be executed without CSS selection. */
    public Map<Integer, Integer> rollLoot(String resourceId, RandomGenerator random) {
        return RewardRoller.resolveRegionalItems(loot.roll(resourceId, random), RewardContext.NONE);
    }
}
