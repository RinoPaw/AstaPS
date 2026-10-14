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
            transPoint[kind="waypoint"] {
                adventureExp: 50;
                primogems: 5;
            }
            transPoint[kind="statue"] {
                adventureExp: 50;
                primogems: 5;
            }
            transPoint[kind="domain"] {
                adventureExp: 50;
                primogems: 5;
            }
            transPoint[scene="3"][point="6"] {
                adventureExp: 10;
                primogems: 0;
            }
            transPoint[scene="7"] {
                adventureExp: 0;
                primogems: 5;
                item-101692: 20;
            }
            """;

    private static Map<Integer,Integer> items(java.util.List<ItemParamData> reward) {
        return reward.stream().collect(Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    @Test
    void builtInTransportCasesRetainExactAmounts() {
        var config = TransPointRewardConfig.parse(RULES);
        assertEquals(Map.of(102, 10), items(config.resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 50, 201, 5), items(config.resolve(3, 8, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 50, 201, 5), items(config.resolve(3, 3, TransPointRewardConfig.Kind.STATUE)));
        assertEquals(Map.of(101692, 20, 201, 5), items(config.resolve(7, 2, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(102, 50, 201, 5), items(config.resolve(3, 48, TransPointRewardConfig.Kind.DOMAIN)));
    }

    @Test
    void exactPointWinsOverLateBroadRuleUnderCssSpecificity() {
        var config = TransPointRewardConfig.parse(RULES + """
                transPoint[scene="7"][point="2"] {
                    adventureExp: 15;
                }
                transPoint[scene="7"] {
                    adventureExp: 0;
                }
                """);
        assertEquals(Map.of(101692, 20, 201, 5, 102, 15),
                items(config.resolve(7, 2, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(101692, 20, 201, 5),
                items(config.resolve(7, 3, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void wrongSelectorOrInvalidRewardCannotLaunch() {
        for (String invalid : java.util.List.of(
                "transPoint[region=\"mondstadt\"] { primogems: 3; }",
                "transPoint[point=\"6\"] { primogems: 3; }",
                "transPoint[scene=\"0\"] { primogems: 3; }",
                "transPoint[kind=\"bad\"] { primogems: 3; }",
                "transPoint[scene=\"2147483648\"] { primogems: 3; }",
                "transPoint { primogems: -2; }",
                "transPoint { item-0: 2; }")) {
            assertThrows(IllegalArgumentException.class,
                    () -> TransPointRewardConfig.parse(RULES + invalid), invalid);
        }
    }

    @Test
    void configuredItemOverridesHaveFullCssInheritance() {
        var config = TransPointRewardConfig.parse(RULES + """
                transPoint[scene="3"][point="99"] {
                    primogems: 0;
                    item-101692: 2;
                }
                """);
        assertEquals(Map.of(102, 50, 101692, 2),
                items(config.resolve(3, 99, TransPointRewardConfig.Kind.WAYPOINT)));
    }

    @Test
    void configCreationUsesRootCss(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("rewards.css");
        RewardConfig.loadOrCreate(file);
        String css = Files.readString(file);
        assertTrue(css.contains("transPoint[kind=\"waypoint\"]"));
        assertTrue(css.contains("chest[kind=\"common\"]"));
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
