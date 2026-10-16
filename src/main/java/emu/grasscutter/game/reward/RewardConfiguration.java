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

    RewardConfiguration(YamlLootTables loot, CssRewards css) {
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
        if ("blossom".equals(targetType)) {
            requireBlossomReward(selected, properties);
        }
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

    /** Refuse unconfigured or partially configured ley-line rewards. */
    static void requireBlossomReward(
            CssRewards.Selection selected, Map<String, String> properties) {
        String kind = properties.get("kind");
        boolean experience = selected.items().getOrDefault(102, 0) > 0;
        boolean companionship = selected.items().getOrDefault(105, 0) > 0;
        boolean wealth = selected.items().getOrDefault(202, 0) > 0;
        boolean books = java.util.Set.of(104001, 104002, 104003).stream()
                .anyMatch(id -> selected.items().getOrDefault(id, 0) > 0
                        || selected.rangedItems().containsKey(id));
        boolean usesLoot = !selected.loot().isEmpty() || !selected.rangedLoot().isEmpty();
        if (!experience || !companionship || usesLoot
                || !("wealth".equals(kind) && wealth && !books
                    || "revelation".equals(kind) && books && !wealth)) {
            throw new IllegalStateException("No complete blossom reward configured for " + properties);
        }
    }

    /** One independent roll per 20 resin; condensed resin executes two rolls. */
    public Map<Integer, Integer> rollBlossom(
            String kind, int worldLevel, boolean condensed, RandomGenerator random) {
        if (!("wealth".equals(kind) || "revelation".equals(kind))
                || worldLevel < 0 || worldLevel > 9) {
            throw new IllegalArgumentException(
                    "Unknown blossom kind/world level: " + kind + "/" + worldLevel);
        }
        var attrs = Map.of("kind", kind, "world_level", Integer.toString(worldLevel));
        var total = new java.util.TreeMap<Integer, Integer>();
        for (int i = 0; i < (condensed ? 2 : 1); i++) {
            roll("blossom", attrs, random).forEach(
                    (id, count) -> total.merge(id, count, Math::addExact));
        }
        return Map.copyOf(total);
    }

    /** Roll only a source-backed world Boss configured in rewards.css and loot.yaml.
     * An unknown Boss tag has no YAML authority yet and retains its native path.
     */
    public Map<Integer, Integer> rollWorldBoss(
            String tag, int nativeBossLevel, RandomGenerator random) {
        var attributes = Map.of(
                "type", "world", "tag", tag, "level", Integer.toString(nativeBossLevel));
        var selection = css.resolve("boss", attributes);
        if (selection.loot().isEmpty() && selection.rangedLoot().isEmpty()) {
            return null;
        }
        return RewardRoller.roll(selection, loot, random);
    }

    /** Trounce rewards must resolve through the same CSS/YAML authority as world Bosses. */
    public Map<Integer, Integer> rollWeeklyBoss(
            String tag, int nativeBossLevel, RandomGenerator random) {
        if (tag == null || tag.isBlank() || nativeBossLevel <= 0) {
            throw new IllegalArgumentException("Missing weekly Boss source identity");
        }
        var attributes = Map.of(
                "type", "weekly", "tag", tag, "level", Integer.toString(nativeBossLevel));
        var selection = css.resolve("boss", attributes);
        if (selection.loot().isEmpty() && selection.rangedLoot().isEmpty()) {
            throw new IllegalStateException(
                    "No configured weekly Boss reward for " + tag + " level " + nativeBossLevel);
        }
        return RewardRoller.roll(selection, loot, random);
    }

    /** Qualified table IDs can be executed without CSS selection. */
    public Map<Integer, Integer> rollLoot(String resourceId, RandomGenerator random) {
        return RewardRoller.resolveRegionalItems(loot.roll(resourceId, random), RewardContext.NONE);
    }
}
