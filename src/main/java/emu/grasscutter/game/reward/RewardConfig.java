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

    /**
     * The only active configuration path is the caller's root-level rewards.toml.
     * Old files are imported once and removed only after the new file parses.
     * Never delete unknown content from a former config/ directory.
     */
    public static void loadOrCreate(Path path) {
        Path target = path.toAbsolutePath().normalize();
        try {
            Path parent = target.getParent();
            Path legacyDirectory = parent.resolve("config");
            Path previousUnified = legacyDirectory.resolve("rewards.toml");
            Path previousTransport = legacyDirectory.resolve("rewards")
                    .resolve("trans-point-rewards.toml");
            Path migratedFrom = null;
            if (Files.notExists(target)) {
                if (Files.isRegularFile(previousUnified)) {
                    String previous = Files.readString(previousUnified);
                    parse(previous);
                    Files.writeString(target, previous,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    migratedFrom = previousUnified;
                } else if (Files.isRegularFile(previousTransport)) {
                    String upgraded = upgradeLegacy(Files.readString(previousTransport));
                    Files.writeString(target, upgraded,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    migratedFrom = previousTransport;
                } else {
                    try (InputStream stream = RewardConfig.class.getResourceAsStream(TEMPLATE)) {
                        if (stream == null) throw new IOException("Missing bundled template " + TEMPLATE);
                        Files.copy(stream, target);
                    }
                }
            }
            // Parse before publishing state or deleting the source of a migration.
            RewardConfig parsed = parse(Files.readString(target));
            if (migratedFrom != null) {
                Files.delete(migratedFrom);
                // A previous rollout could have left both generations of the
                // old config. Delete the older copy only when it matches the
                // successfully migrated owner configuration byte-for-byte.
                if (migratedFrom.equals(previousUnified)
                        && Files.isRegularFile(previousTransport)) {
                    try {
                        if (upgradeLegacy(Files.readString(previousTransport))
                                .equals(Files.readString(target))) {
                            Files.delete(previousTransport);
                        }
                    } catch (IllegalArgumentException invalidOldConfig) {
                        // Preserve a divergent/broken legacy file for manual review.
                    }
                }
                removeIfEmpty(previousTransport.getParent());
                removeIfEmpty(legacyDirectory);
            }
            active = parsed;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load " + path + ": " + e.getMessage(), e);
        }
    }

    private static void removeIfEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        // Close the directory stream before deletion (required on Windows).
        boolean empty;
        try (var contents = Files.list(directory)) {
            empty = contents.findAny().isEmpty();
        }
        if (empty) Files.deleteIfExists(directory);
    }
}
