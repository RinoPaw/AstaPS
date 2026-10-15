package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.drop.YamlLootTables;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class TransPointRewardsTest {
    private static final Gson GSON = new Gson();

    private static String read(String resource) throws Exception {
        try (var stream = TransPointRewardsTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<Integer, Integer> roll(
            CssRewards css, YamlLootTables loot, String kind, int scene, int point) {
        return RewardRoller.roll(css.resolve("transpoint", Map.of(
                "kind", kind,
                "scene", Integer.toString(scene),
                "point", Integer.toString(point))), loot, new Random(1));
    }

    @Test
    void bundledPointRulesPreserveAllFormerTomlPayouts() throws Exception {
        var ids = new ItemIdRegistry(Map.ofEntries(
                Map.entry(102, "Adventure EXP"),
                Map.entry(201, "Primogem"),
                Map.entry(202, "Mora"),
                Map.entry(104001, "Wanderer's Advice"),
                Map.entry(104002, "Adventurer's Experience"),
                Map.entry(104003, "Hero's Wit"),
                Map.entry(104011, "Enhancement Ore"),
                Map.entry(104012, "Fine Enhancement Ore"),
                Map.entry(101692, "Three Realms Item")));
        var loot = YamlLootTables.parse(read("/rewards/loot.yaml"), ids::resolve);
        var css = CssRewards.parse(read("/rewards/rewards.css"), ids, loot);
        var standard = Map.of(102, 50, 201, 5);
        assertEquals(standard, roll(css, loot, "waypoint", 3, 8));
        assertEquals(standard, roll(css, loot, "statue", 3, 3));
        assertEquals(standard, roll(css, loot, "domain", 3, 48));
        assertEquals(Map.of(102, 10), roll(css, loot, "waypoint", 3, 6));
        assertEquals(Map.of(201, 5, 101692, 20), roll(css, loot, "waypoint", 7, 2));
        assertEquals(Map.of(), roll(css, loot, "unknown", 3, 8));
    }

    @Test
    void eligibilityStillExcludesUnrelatedAndAlreadyUnlockedPoints() {
        var point = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"gadgetId\":70110002}", PointData.class);
        var statue = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"maxSpringVolume\":5000}", PointData.class);
        var hidden = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"mapVisibility\":\"Never\"}", PointData.class);
        var portal = GSON.fromJson(
                "{\"$type\":\"SceneTransPoint\",\"type\":\"PORTAL\"}", PointData.class);
        var domain = GSON.fromJson("{\"$type\":\"DungeonEntry\"}", PointData.class);
        assertEquals(TransPointRewardEligibility.Kind.WAYPOINT,
                TransPointRewardEligibility.kindOf(point, false));
        assertEquals(TransPointRewardEligibility.Kind.STATUE,
                TransPointRewardEligibility.kindOf(statue, true));
        assertEquals(TransPointRewardEligibility.Kind.DOMAIN,
                TransPointRewardEligibility.kindOf(domain, false));
        assertNull(TransPointRewardEligibility.kindOf(hidden, false));
        assertNull(TransPointRewardEligibility.kindOf(portal, false));
        assertNull(TransPointRewardEligibility.kindOf(GSON.fromJson(
                "{\"$type\":\"DungeonEntry\",\"unlocked\":true}", PointData.class), false));
    }

    @Test
    void aStoryDomainWithoutNativeRewardIdRemainsEligible() {
        var dungeon = GSON.fromJson(
                "{\"id\":1003,\"sceneId\":3,\"dungeonEntryId\":20}",
                DungeonEntryData.class);
        var point = GSON.fromJson("{\"$type\":\"DungeonEntry\"}", PointData.class);
        assertEquals(0, dungeon.getRewardDataId());
        assertEquals(TransPointRewardEligibility.Kind.DOMAIN,
                TransPointRewardEligibility.kindOf(point, false));
    }
}
