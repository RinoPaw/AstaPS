package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import java.io.IOException;
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
