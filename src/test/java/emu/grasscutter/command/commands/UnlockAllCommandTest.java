package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import emu.grasscutter.game.player.Player;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

public final class UnlockAllCommandTest {
    private static final Gson GSON = new Gson();

    private static PointData point(String json) {
        return GSON.fromJson(json, PointData.class);
    }

    @Test
    void unlocksOnlyResourceAreasAndPreservesQuestLocksUnlessForced() {
        var oldScenes = new HashMap<>(GameData.getScenePointsPerScene());
        var oldPoints = new HashMap<>(GameData.getScenePointEntryMap());
        var oldAreas = new HashMap<>(GameData.getWorldAreaDataMap());
        try {
            GameData.getScenePointsPerScene().clear();
            GameData.getScenePointEntryMap().clear();
            GameData.getWorldAreaDataMap().clear();
            GameData.getScenePointsPerScene().put(3, List.of(7, 8, 9));
            for (var json :
                    List.of(
                            "{\"id\":7,\"areaId\":1,\"maxSpringVolume\":100}",
                            "{\"id\":8,\"$type\":\"SceneTransPoint\",\"forbidSimpleUnlock\":true}",
                            "{\"id\":9,\"$type\":\"SceneTransPoint\"}")) {
                var data = point(json);
                GameData.getScenePointEntryMap()
                        .put((3 << 16) + data.getId(), new ScenePointEntry(3, data));
            }
            for (var json :
                    List.of(
                            "{\"sceneID\":3,\"areaID1\":1,\"areaID2\":3}",
                            "{\"sceneID\":4,\"areaID1\":25,\"areaID2\":26}")) {
                var area = GSON.fromJson(json, WorldAreaData.class);
                GameData.getWorldAreaDataMap().put(area.getId(), area);
            }
            var player = new Player();
            player.getForceLockedScenePoints(3).add(9);
            assertEquals(1, UnlockAllCommand.unlockMap(player, false).totalPoints());
            assertEquals(Set.of(7), player.getUnlockedScenePoints(3));
            assertEquals(Set.of(1, 3), player.getUnlockedSceneAreas(3));
            assertTrue(player.isScenePointForceLocked(3, 9));
            UnlockAllCommand.unlockMap(player, true);
            assertEquals(Set.of(7, 8, 9), player.getUnlockedScenePoints(3));
            assertFalse(player.isScenePointForceLocked(3, 9));
        } finally {
            GameData.getScenePointsPerScene().clear();
            GameData.getScenePointsPerScene().putAll(oldScenes);
            GameData.getScenePointEntryMap().clear();
            GameData.getScenePointEntryMap().putAll(oldPoints);
            GameData.getWorldAreaDataMap().clear();
            GameData.getWorldAreaDataMap().putAll(oldAreas);
        }
    }

    @Test
    void identifiesWaypointStatueAndOtherPointTypes() {
        assertEquals(
                UnlockAllCommand.PointCategory.WAYPOINT,
                UnlockAllCommand.classifyPoint(point("{\"$type\":\"TransPointNormal\"}")));
        assertEquals(
                UnlockAllCommand.PointCategory.WAYPOINT,
                UnlockAllCommand.classifyPoint(point("{\"$type\":\"SceneTransPoint\"}")));
        assertEquals(
                UnlockAllCommand.PointCategory.STATUE,
                UnlockAllCommand.classifyPoint(point("{\"maxSpringVolume\":100}")));
        assertEquals(
                UnlockAllCommand.PointCategory.STATUE,
                UnlockAllCommand.classifyPoint(point("{\"gadgetId\":70130009}")));
        assertEquals(
                UnlockAllCommand.PointCategory.OTHER,
                UnlockAllCommand.classifyPoint(point("{\"$type\":\"DungeonEntry\"}")));
        assertEquals(UnlockAllCommand.PointCategory.OTHER, UnlockAllCommand.classifyPoint(null));
    }

    @Test
    void countsOnlyNewlyUnlockedPointsAndAreas() {
        var result = new UnlockAllCommand.MapUnlockResult(4, 2, 3, 30);
        assertEquals(9, result.totalPoints());
        assertEquals(4, result.waypoints());
        assertEquals(2, result.statues());
        assertEquals(3, result.otherPoints());
        assertEquals(30, result.areaFlags());
    }

    @Test
    void preservesMapAndForceSyntax() {
        var command = new UnlockAllCommand().createCommandLine(null, null);
        assertDoesNotThrow(() -> command.parseArgs("map"));
        assertDoesNotThrow(
                () ->
                        new UnlockAllCommand()
                                .createCommandLine(null, null)
                                .parseArgs("map", "--force"));
        assertThrows(
                CommandLine.ParameterException.class,
                () ->
                        new UnlockAllCommand()
                                .createCommandLine(null, null)
                                .parseArgs("map", "--force", "extra"));
    }
}
