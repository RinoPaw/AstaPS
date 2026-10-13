package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class RewardConfigTest {
    private static Map<Integer, Integer> items(java.util.List<ItemParamData> rewards) {
        return rewards.stream().collect(
                Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    @Test
    void bundledUnifiedTemplatePreservesNativeTransPointDefaults() throws IOException {
        try (var resource = RewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(resource);
            var config = RewardConfig.parse(
                    new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            var transPoint = config.transPoint();
            assertEquals(Map.of(102, 50, 201, 5),
                    items(transPoint.resolve(3, 8, TransPointRewardConfig.Kind.WAYPOINT)));
            assertEquals(Map.of(102, 10),
                    items(transPoint.resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
            assertEquals(Map.of(101692, 20, 201, 5),
                    items(transPoint.resolve(7, 2, TransPointRewardConfig.Kind.DOMAIN)));
        }
    }

    @Test
    void legacyMigrationKeepsSingleQuotedTablesAndComments() {
        String legacy = """
                # Custom original note
                version = 1
                ['kind=waypoint']
                adventureExp = 70
                ['kind=statue']
                primogems = 5
                ['kind=domain']
                adventureExp = 20
                ['3:6'] # tutorial
                adventureExp = 12
                """;
        String upgraded = RewardConfig.upgradeLegacy(legacy);
        assertTrue(upgraded.contains("# Custom original note"));
        assertTrue(upgraded.contains("[transPoint.'3:6'] # tutorial"));
        assertEquals(Map.of(102, 12),
                items(RewardConfig.parse(upgraded).transPoint()
                        .resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void migratesPreviousUnifiedFileAndRemovesEmptyConfigDirectory(@TempDir Path tempDir)
            throws IOException {
        Path prior = tempDir.resolve("config/rewards.toml");
        Files.createDirectories(prior.getParent());
        try (var source = RewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(source);
            Files.copy(source, prior);
        }
        Path target = tempDir.resolve("rewards.toml");
        RewardConfig.loadOrCreate(target);
        assertTrue(Files.isRegularFile(target));
        assertFalse(Files.exists(prior));
        assertFalse(Files.exists(tempDir.resolve("config")));
        assertEquals(Map.of(102, 10),
                items(RewardConfig.current().transPoint()
                        .resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void neverDeletesUnknownLegacyFiles(@TempDir Path tempDir) throws IOException {
        Path prior = tempDir.resolve("config/rewards.toml");
        Files.createDirectories(prior.getParent());
        Files.writeString(prior, bundledTemplate());
        Path unrelated = prior.getParent().resolve("other-setting.txt");
        Files.writeString(unrelated, "preserve this");
        RewardConfig.loadOrCreate(tempDir.resolve("rewards.toml"));
        assertFalse(Files.exists(prior));
        assertTrue(Files.isRegularFile(unrelated));
    }

    @Test
    void rootConfigTakesPriorityOverAnyLegacyFile(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("rewards.toml");
        Files.writeString(target, bundledTemplate().replace("adventureExp = 10", "adventureExp = 12"));
        Path legacy = tempDir.resolve("config/rewards.toml");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, bundledTemplate().replace("adventureExp = 10", "adventureExp = 99"));
        RewardConfig.loadOrCreate(target);
        assertEquals(Map.of(102, 12),
                items(RewardConfig.current().transPoint()
                        .resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
        assertTrue(Files.exists(legacy)); // Never delete a source that was not migrated.
    }

    private static String bundledTemplate() throws IOException {
        try (var source = RewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(source);
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void rejectsUnknownSectionsUntilRuntimeOwnersExist() {
        String base = """
                version = 1
                [transPoint."kind=waypoint"]
                primogems = 5
                [transPoint."kind=statue"]
                primogems = 5
                [transPoint."kind=domain"]
                primogems = 5
                """;
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(base + "[chest]\nsource = \"native\"\n"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(base.replace("version = 1", "version = 2")));
    }
}
