package emu.grasscutter.game.reward;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
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
            String operatorCss = Files.readString(path);
            String migratedCss = RewardCssMigration.migrate(operatorCss, readBundledTemplate());
            RewardConfig candidate = parse(migratedCss);
            candidate.chest().requireCompleteBaseRewards();
            if (!migratedCss.equals(operatorCss)) {
                installMigration(path, migratedCss);
            }
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

    /**
     * Save a byte-for-byte backup before replacing the user's CSS.
     * Prepare and validate the new snapshot before writing any files.
     * A temporary sibling file prevents partially written CSS on interruption.
     */
    private static void installMigration(Path path, String updatedCss) throws IOException {
        Path file = path.toAbsolutePath();
        Path dir = file.getParent();
        String name = file.getFileName().toString();
        Path staged = Files.createTempFile(dir, "." + name + ".migration-", ".tmp");
        try {
            Files.writeString(staged, updatedCss);
            Path backup = Files.createTempFile(dir, name + ".backup-", ".css");
            try {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.deleteIfExists(backup);
                throw e;
            }
            try {
                Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
            }
            emu.grasscutter.Grasscutter.getLogger().info(
                    "Migrated {} with missing chest defaults; original backed up to {}",
                    file, backup);
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
