package emu.grasscutter.game.reward;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Single operator-owned rewards.toml for all direct-reward subsystems.
 *
 * <p>Both transPoint and chest rules are validated before publication. Each subsystem only sees
 * the immutable, fully validated snapshot; no second reward file is read or generated.
 */
public final class RewardConfig {
    private static final String TEMPLATE = "/rewards.toml";
    private static final Set<String> ROOT_FIELDS = Set.of("version", "transPoint", "chest");
    private static volatile RewardConfig active;

    private final TransPointRewardConfig transPoint;
    private final ChestRewardConfig chest;

    private RewardConfig(TransPointRewardConfig transPoint, ChestRewardConfig chest) {
        this.transPoint = transPoint;
        this.chest = chest;
    }

    public TransPointRewardConfig transPoint() {
        return transPoint;
    }

    public ChestRewardConfig chest() {
        return chest;
    }

    public static RewardConfig current() {
        var value = active;
        if (value == null) throw new IllegalStateException("rewards.toml has not been loaded");
        return value;
    }

    public static RewardConfig parse(String source) {
        TomlParseResult root = Toml.parse(source);
        if (root.hasErrors()) {
            throw new IllegalArgumentException("Invalid rewards.toml syntax: " + root.errors());
        }
        if (!(root.get("version") instanceof Long version) || version != 1) {
            throw new IllegalArgumentException("Expected rewards.toml version = 1");
        }
        if (!root.keySet().equals(ROOT_FIELDS)) {
            throw new IllegalArgumentException(
                    "rewards.toml must contain only version = 1, [transPoint] and [chest] tables");
        }

        // Validate both sections before the caller can publish the new snapshot.
        return new RewardConfig(
                TransPointRewardConfig.parseTable(root),
                ChestRewardConfig.parseTable(root));
    }

    /** Generate missing root-level policy exactly once. Existing operator files are never reset. */
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
            RewardConfig candidate = parse(Files.readString(path));
            active = candidate;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }
}
