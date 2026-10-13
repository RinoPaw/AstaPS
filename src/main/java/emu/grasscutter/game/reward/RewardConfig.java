package emu.grasscutter.game.reward;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Owns the unified operator reward config. Only namespaces with live runtime
 * consumers are accepted. Future chest/domain policies belong here, but the
 * original drop graphs and persisted player claims are separate inputs.
 */
public final class RewardConfig {
    private static final String TEMPLATE = "/rewards.toml";
    private static final int FORMAT_VERSION = 1;
    private static final Set<String> ROOT_FIELDS = Set.of("version", "transPoint");
    private static final Pattern LEGACY_SELECTORS =
            Pattern.compile("(?m)^([ \\t]*)\\[[ \\t]*(\"[^\"]+\"|'[^']+')[ \\t]*\\]");
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

    /** Preserve all legacy comments and amounts while moving selector tables under transPoint. */
    static String upgradeLegacy(String legacy) {
        String migrated = LEGACY_SELECTORS.matcher(legacy).replaceAll("$1[transPoint.$2]");
        parse(migrated);
        return migrated;
    }

    public static void loadOrCreate(Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Path legacy = path.resolveSibling("rewards").resolve("trans-point-rewards.toml");
                if (Files.isRegularFile(legacy)) {
                    String migrated = upgradeLegacy(Files.readString(legacy));
                    // Keep the old owner-edited file as a backup. Never overwrite a
                    // new unified config, nor silently substitute bundled defaults.
                    Files.writeString(path, migrated,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } else {
                    try (InputStream stream = RewardConfig.class.getResourceAsStream(TEMPLATE)) {
                        if (stream == null) throw new IOException("Missing bundled template " + TEMPLATE);
                        Files.copy(stream, path);
                    }
                }
            }
            // Parse completely before publishing the new immutable snapshot.
            active = parse(Files.readString(path));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }
}
