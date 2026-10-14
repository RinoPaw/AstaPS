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

    /** Generate defaults if missing, otherwise load without rewriting user CSS. */
    public static void loadOrCreate(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            if (Files.notExists(path)) {
                Path parent = path.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                try (InputStream source = RewardConfig.class.getResourceAsStream(TEMPLATE)) {
                    if (source == null) throw new IOException("Missing bundled " + TEMPLATE);
                    Files.copy(source, path);
                }
            }
            String operatorCss = Files.readString(path);
            // Like config.json: keep the operator file unchanged and apply missing
            // bundled defaults only to the active in-memory configuration.
            String effectiveCss = RewardCssDefaults.overlayMissing(operatorCss, readBundledTemplate());
            RewardConfig candidate = parse(effectiveCss);
            candidate.chest().requireCompleteBaseRewards();
            active = candidate;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }

    private static String readBundledTemplate() throws IOException {
        try (InputStream source = RewardConfig.class.getResourceAsStream(TEMPLATE)) {
            if (source == null) throw new IOException("Missing bundled " + TEMPLATE);
            return new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

}
