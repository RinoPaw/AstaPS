package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TransPointRewardConfigTest {
    private static final Gson GSON = new Gson();
    private static final String RULES = """
            version = 1

            [transPoint."kind=waypoint"]
            adventureExp = 50
            primogems = 5

            [transPoint."kind=statue"]
            adventureExp = 50
            primogems = 5

            [transPoint."kind=domain"]
            adventureExp = 50
            primogems = 5

            [transPoint."3:6"]
            adventureExp = 10
            primogems = 0

            [transPoint."7:*"]
            adventureExp = 0
            primogems = 5
            items = [{ id = 101692, count = 20 }]

            [chest."kind=common"]
            adventureExp = 30
            primogems = 2

            [chest."kind=exquisite"]
            adventureExp = 30
            primogems = 5

            [chest."kind=precious"]
            adventureExp = 30
            primogems = 10

            [chest."kind=luxurious"]
            adventureExp = 30
            primogems = 10
            """;

    private static Map<Integer, Integer> items(java.util.List<ItemParamData> reward) {
        return reward.stream().collect(Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    @Test
    void writtenOverridesPreserveNativeTransportAwards() {
        var rules = TransPointRewardConfig.parse(RULES);
        assertEquals(Map.of(102, 10), items(rules.resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 50, 201, 5),
                items(rules.resolve(3, 8, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 50, 201, 5),
                items(rules.resolve(3, 3, TransPointRewardConfig.Kind.STATUE)));
        assertEquals(Map.of(101692, 20, 201, 5),
                items(rules.resolve(7, 2, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 50, 201, 5),
                items(rules.resolve(3, 48, TransPointRewardConfig.Kind.DOMAIN)));
    }

    @Test
    void exactPointOverridesSceneWildcard() {
        var text = RULES + """
                ["7:2"]
                adventureExp = 15
                """;
        var rules = TransPointRewardConfig.parse(text);
        assertEquals(Map.of(102, 15, 201, 5, 101692, 20),
                items(rules.resolve(7, 2, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(101692, 20, 201, 5),
                items(rules.resolve(7, 3, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void unsupportedSelectorsAndBrokenConfigurationFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransPointRewardConfig.parse(RULES + "[transPoint.\"region=mondstadt\"]\nprimogems = 3\n"));
        assertThrows(IllegalArgumentException.class,
                () -> TransPointRewardConfig.parse(RULES.replace("adventureExp = 10", "adventureExp = -10")));
        assertThrows(IllegalArgumentException.class,
                () -> TransPointRewardConfig.parse(RULES.replace("version = 1", "version = 2")));
        assertThrows(IllegalArgumentException.class,
                () -> TransPointRewardConfig.parse(RULES.replace("id = 101692", "id = 0")));
    }

    @Test
    void onlyOrdinaryNativeTransPointsAreWaypoints() {
        var ordinary = GSON.fromJson("{\"$type\":\"SceneTransPoint\",\"gadgetId\":70110002}", PointData.class);
        var portal = GSON.fromJson("{\"$type\":\"SceneTransPoint\",\"type\":\"PORTAL\",\"unlocked\":true}", PointData.class);
        var hidden = GSON.fromJson("{\"$type\":\"SceneTransPoint\",\"gadgetId\":70110002,\"mapVisibility\":\"Never\"}", PointData.class);
        var dungeon = GSON.fromJson("{\"$type\":\"DungeonEntry\"}", PointData.class);
        assertEquals(TransPointRewardConfig.Kind.WAYPOINT,
                TransPointRewardConfig.kindOf(ordinary, false));
        var statue = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"gadgetId\":70110001,\"maxSpringVolume\":5000}",
                PointData.class);
        assertEquals(TransPointRewardConfig.Kind.STATUE,
                TransPointRewardConfig.kindOf(statue, true));
        var obfuscatedStatue = GSON.fromJson(
                "{\"$type\":\"KDEHKECBDBO\",\"gadgetId\":70130009}", PointData.class);
        assertEquals(TransPointRewardConfig.Kind.STATUE,
                TransPointRewardConfig.kindOf(obfuscatedStatue, true));
        assertNull(TransPointRewardConfig.kindOf(portal, false));
        assertNull(TransPointRewardConfig.kindOf(hidden, false));
        assertEquals(TransPointRewardConfig.Kind.DOMAIN,
                TransPointRewardConfig.kindOf(dungeon, false));
        var preUnlockedDomain = GSON.fromJson(
                "{\"$type\":\"DungeonEntry\",\"unlocked\":true}", PointData.class);
        var hiddenDomain = GSON.fromJson(
                "{\"$type\":\"DungeonEntry\",\"mapVisibility\":\"Never\"}", PointData.class);
        assertNull(TransPointRewardConfig.kindOf(preUnlockedDomain, false));
        assertNull(TransPointRewardConfig.kindOf(hiddenDomain, false));
    }

    @Test
    void generatesOnlyOneRootPolicyFileAndPreservesOperatorEdits(@TempDir Path tempDir)
            throws Exception {
        Path config = tempDir.resolve("rewards.toml");
        RewardConfig.loadOrCreate(config);
        assertTrue(Files.exists(config));
        assertTrue(Files.readString(config).contains("[transPoint.\"kind=waypoint\"]"));
        assertTrue(Files.readString(config).contains("[chest.\"kind=common\"]"));

        Files.writeString(config, RULES);
        RewardConfig.loadOrCreate(config);
        assertEquals(RULES, Files.readString(config));
        assertEquals(Map.of(102, 10),
                items(TransPointRewardConfig.current().resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void laterBroadRuleCanOverrideEarlierExactPoint() {
        String text = RULES.replace(
                "[transPoint.\"7:*\"]",
                "[transPoint.\"7:2\"]\nadventureExp = 15\n\n[transPoint.\"7:*\"]");
        var config = TransPointRewardConfig.parse(text);
        assertEquals(Map.of(101692, 20, 201, 5),
                items(config.resolve(7, 2, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void transportFieldOverridesInheritAndCanClearItemCounts() {
        String text = RULES + """
                [transPoint."3:99"]
                primogems = 0
                items = [{ id = 101692, count = 2 }]
                """;
        var config = TransPointRewardConfig.parse(text);
        assertEquals(Map.of(102, 50, 101692, 2),
                items(config.resolve(3, 99, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void domainWithNoNativeRewardIdStillUsesServerOwnedFirstActivationReward() {
        // Scene 3 point 20 is a native story dungeon with no rewardDataId.
        var domain = GSON.fromJson(
                "{\"id\":1003,\"sceneId\":3,\"dungeonEntryId\":20}",
                DungeonEntryData.class);
        var point = GSON.fromJson(
                "{\"$type\":\"DungeonEntry\",\"gadgetId\":70120001}",
                PointData.class);
        assertEquals(0, domain.getRewardDataId());
        assertEquals(TransPointRewardConfig.Kind.DOMAIN,
                TransPointRewardConfig.kindOf(point, false));
        var rewards = TransPointRewardConfig.parse(RULES)
                .resolve(domain.getSceneId(), domain.getDungeonEntryId(),
                        TransPointRewardConfig.kindOf(point, false));
        assertEquals(Map.of(102, 50, 201, 5), items(rewards));
    }
}
