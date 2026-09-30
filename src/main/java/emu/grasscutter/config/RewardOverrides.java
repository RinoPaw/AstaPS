package emu.grasscutter.config;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional user-facing reward overrides.
 *
 * <p>The base game remains the authority. Missing fields inherit the original reward behavior.
 * This file only describes deliberate server-owner changes.
 */
public final class RewardOverrides {
    private static final Path FILE = Path.of("reward-overrides.json");

    // Grasscutter's original trans-point values, matching the base implementation.
    private static final UnlockReward VANILLA_WAYPOINT = new UnlockReward(5, 10, 0, 0, 0);
    private static final UnlockReward VANILLA_STATUE = new UnlockReward(5, 50, 0, 0, 0);

    private static volatile Root current = new Root();
    private static volatile long lastModified = Long.MIN_VALUE;

    private RewardOverrides() {}

    public static UnlockReward resolveUnlock(boolean statue) {
        Root root = current();
        UnlockReward base = statue ? VANILLA_STATUE : VANILLA_WAYPOINT;
        UnlockOverride override = statue ? root.statue : root.waypoint;
        if (override == null) return base;

        return new UnlockReward(
                value(override.primogems, base.primogems),
                value(override.adventureExp, base.adventureExp),
                value(override.fragileResin, base.fragileResin),
                value(override.heroWit, base.heroWit),
                value(override.mysticEnhancementOre, base.mysticEnhancementOre));
    }

    /**
     * Returns a full replacement for one chest tier, or {@code null} to use the original drop table.
     *
     * <p>Chest rewards vary by region/drop tag in the original game, so there is intentionally no
     * Java-side synthetic default for them.
     */
    public static ChestReward chestReplacement(String tier) {
        Root root = current();
        if (root.chests == null || tier == null) return null;
        return switch (tier) {
            case "COMMON" -> root.chests.common;
            case "EXQUISITE" -> root.chests.exquisite;
            case "PRECIOUS" -> root.chests.precious;
            case "LUXURIOUS" -> root.chests.luxurious;
            default -> null;
        };
    }

    private static int value(Integer override, int vanilla) {
        return Math.max(0, override == null ? vanilla : override);
    }

    private static Root current() {
        try {
            if (!Files.exists(FILE)) {
                synchronized (RewardOverrides.class) {
                    if (!Files.exists(FILE)) {
                        Files.writeString(FILE, TEMPLATE, StandardCharsets.UTF_8);
                        Grasscutter.getLogger()
                                .info(
                                        "Created {}. Leave values null to keep original-game rewards.",
                                        FILE);
                    }
                }
            }

            long modified = Files.getLastModifiedTime(FILE).toMillis();
            if (modified != lastModified) {
                synchronized (RewardOverrides.class) {
                    if (modified != lastModified) {
                        Root loaded = JsonUtils.loadToClass(FILE, Root.class);
                        current = loaded == null ? new Root() : loaded;
                        lastModified = modified;
                        Grasscutter.getLogger().info("Loaded reward overrides from {}", FILE);
                    }
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Failed to load {}. Keeping original-game reward defaults.", FILE, e);
        }
        return current;
    }

    public static final class Root {
        public UnlockOverride waypoint;
        public UnlockOverride statue;
        public ChestOverrides chests;
    }

    /** Null fields inherit the original waypoint/statue value. */
    public static final class UnlockOverride {
        public Integer primogems;
        public Integer adventureExp;
        public Integer fragileResin;
        public Integer heroWit;
        public Integer mysticEnhancementOre;
    }

    public static final class ChestOverrides {
        public ChestReward common;
        public ChestReward exquisite;
        public ChestReward precious;
        public ChestReward luxurious;
    }

    /**
     * A chest tier object is a complete replacement for that tier. Omitted fields deserialize as 0.
     * Leave the whole tier {@code null} to use the original region/drop-table reward.
     */
    public static final class ChestReward {
        public int primogems;
        public int adventureExp;
        public int sigil;
        public int mora;
        public int fineEnhancementOre;
        public int wanderersAdvice;
        public int adventurersExperience;
        public int herosWit;
    }

    public record UnlockReward(
            int primogems,
            int adventureExp,
            int fragileResin,
            int heroWit,
            int mysticEnhancementOre) {}

    private static final String TEMPLATE =
            """
            {
              "_comment": "Only fill values you want to override. null keeps the original game behavior.",
              "waypoint": {
                "primogems": null,
                "adventureExp": null,
                "fragileResin": null,
                "heroWit": null,
                "mysticEnhancementOre": null
              },
              "statue": {
                "primogems": null,
                "adventureExp": null,
                "fragileResin": null,
                "heroWit": null,
                "mysticEnhancementOre": null
              },
              "chests": {
                "_comment": "A null tier uses the original ChestDrop/DropTable data. An object fully replaces that tier.",
                "common": null,
                "exquisite": null,
                "precious": null,
                "luxurious": null
              }
            }
            """;
}
