package emu.grasscutter.game.reward;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One operator-owned rewards.css, parsed and validated as an immutable snapshot.
 * Both transport and fallback chest fixed payouts use the validated snapshot.
 */
public final class RewardConfig {
    private static final String TEMPLATE = "/rewards.css";
    private static volatile RewardConfig active;

    private final TransPointRewardConfig transPoint;
    private final ChestRewardConfig chest;

    private RewardConfig(TransPointRewardConfig transPoint, ChestRewardConfig chest) {
        this.transPoint = transPoint;
        this.chest = chest;
    }

    public TransPointRewardConfig transPoint() { return transPoint; }
    public ChestRewardConfig chest() { return chest; }

    public static RewardConfig current() {
        var config = active;
        if (config == null) throw new IllegalStateException("rewards.css has not been loaded");
        return config;
    }

    public static RewardConfig parse(String source) {
        List<CssRewardRules.Rule> all = CssRewardRules.parse(source);
        return new RewardConfig(
                TransPointRewardConfig.fromRules(all),
                ChestRewardConfig.fromRules(all));
    }

    /**
     * Generate only if missing. Never silently replace an existing operator policy.
     * If a legacy TOML file exists, require an explicit choice before switching.
     */
    public static void loadOrCreate(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            if (Files.notExists(path)) {
                Path old = path.resolveSibling("rewards.toml");
                if (Files.exists(old)) {
                    throw new IllegalStateException(
                            "Legacy " + old + " exists. Back it up and remove/rename it before "
                            + "generating " + path + "; convert custom rules manually.");
                }
                Path parent = path.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                try (InputStream source = RewardConfig.class.getResourceAsStream(TEMPLATE)) {
                    if (source == null) throw new IOException("Missing bundled " + TEMPLATE);
                    Files.copy(source, path);
                }
            }
            RewardConfig candidate = parse(Files.readString(path));
            candidate.chest().requireCompleteBaseRewards();
            active = candidate;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }
}
