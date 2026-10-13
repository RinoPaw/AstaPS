package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RewardConfigTest {
    private static Map<Integer, Integer> items(java.util.List<ItemParamData> rewards) {
        return rewards.stream().collect(
                Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    private static String bundledTemplate() throws IOException {
        try (var source = RewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(source);
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void bundledUnifiedTemplatePreservesTransPointDefaults() throws IOException {
        var transPoint = RewardConfig.parse(bundledTemplate()).transPoint();
        assertEquals(Map.of(102, 50, 201, 5),
                items(transPoint.resolve(3, 8, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 10),
                items(transPoint.resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(101692, 20, 201, 5),
                items(transPoint.resolve(7, 2, TransPointRewardConfig.Kind.DOMAIN)));
    }

    @Test
    void createsOnlyRootLevelConfig(@TempDir Path tempDir) throws IOException {
        Path config = tempDir.resolve("rewards.toml");
        RewardConfig.loadOrCreate(config);
        assertEquals(bundledTemplate(), Files.readString(config));
        assertFalse(Files.exists(tempDir.resolve("config")));
    }

    @Test
    void keepsEditedRootConfigOnRestart(@TempDir Path tempDir) throws IOException {
        Path config = tempDir.resolve("rewards.toml");
        String customized = bundledTemplate().replace("adventureExp = 10", "adventureExp = 12");
        Files.writeString(config, customized);
        RewardConfig.loadOrCreate(config);
        assertEquals(customized, Files.readString(config));
        assertEquals(Map.of(102, 12),
                items(RewardConfig.current().transPoint()
                        .resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void invalidRootConfigFailsWithoutBeingOverwritten(@TempDir Path tempDir) throws IOException {
        Path config = tempDir.resolve("rewards.toml");
        String invalid = "version = 2\n";
        Files.writeString(config, invalid);
        assertThrows(IllegalStateException.class, () -> RewardConfig.loadOrCreate(config));
        assertEquals(invalid, Files.readString(config));
    }

    @Test
    void rejectsUnknownSectionsUntilTheirRuntimeOwnerExists() throws IOException {
        String base = bundledTemplate();
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(base + "[chest]\nsource = \"native\"\n"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(base.replace("version = 1", "version = 2")));
    }
}
