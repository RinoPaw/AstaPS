package emu.grasscutter.game.reward;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Loads the single operator-owned rewards.toml from the server working directory.
 * Native drop graphs and player claim records remain separate sources.
 */
public final class RewardConfig {
    private static final String TEMPLATE = "/rewards.toml";
    private static final int FORMAT_VERSION = 1;
    private static final Set<String> ROOT_FIELDS = Set.of("version", "transPoint");
    private static volatile RewardConfig active;

    private final TransPointRewardConfig transPoint;

    private RewardConfig(TransPointRewardConfig transPoint) {
        this.transPoint = transPoint;
    }

    public TransPointRewardConfig transPoint() {
        return this.transPoint;
    }

    public static RewardConfig current() {
        var config = active;
        if (config == null) throw new IllegalStateException("rewards.toml was not loaded");
        return config;
    }

    public static RewardConfig parse(String text) {
        TomlParseResult root = Toml.parse(text);
        if (root.hasErrors()) throw new IllegalArgumentException("Invalid TOML: " + root.errors());
        Object version = root.get("version");
        if (!(version instanceof Long n) || n != FORMAT_VERSION) {
            throw new IllegalArgumentException("Expected version = " + FORMAT_VERSION);
        }
        for (var key : root.keySet()) {
            if (!ROOT_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Unknown rewards.toml section: " + key);
            }
        }
        if (!(root.get("transPoint") instanceof TomlTable transport)) {
            throw new IllegalArgumentException("Missing transPoint reward tables");
        }
        return new RewardConfig(TransPointRewardConfig.parseTable(transport));
    }

    /** Generate a missing root-level file; never overwrite user-edited rules. */
    public static void loadOrCreate(Path path) {
        try {
            if (Files.notExists(path)) {
                try (InputStream stream = RewardConfig.class.getResourceAsStream(TEMPLATE)) {
                    if (stream == null) throw new IOException("Missing bundled template " + TEMPLATE);
                    Files.copy(stream, path);
                }
            }
            // Validate fully before publishing a new immutable snapshot.
            active = parse(Files.readString(path));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }
}
