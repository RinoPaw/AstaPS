package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import emu.grasscutter.game.player.SnezhnayaFrigid;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldAreaIndexTest {
    /** Area 55 is an L shape, so a bounding-box test alone would wrongly claim its notch. */
    private static final String FIXTURE =
            """
            {"OBKNAJCIHEA": [
              {"LKBLPFHOAFB": {"BBLLBIOLMHP": 55, "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                {"_x": 0, "_z": 0}, {"_x": 100, "_z": 0}, {"_x": 100, "_z": 40},
                {"_x": 40, "_z": 40}, {"_x": 40, "_z": 100}, {"_x": 0, "_z": 100}]}}},
              {"LKBLPFHOAFB": {"BBLLBIOLMHP": 7, "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                {"_x": 200, "_z": 200}, {"_x": 300, "_z": 200}, {"_x": 300, "_z": 300}]}}}
            ]}
            """;

    private static WorldAreaIndex fixture() {
        return WorldAreaIndex.parse(JsonParser.parseString(FIXTURE));
    }

    @Test
    void findsTheAreaContainingAPoint() {
        var index = fixture();
        assertFalse(index.isEmpty());
        assertEquals(55, index.level1AreaAt(20, 80));
        assertEquals(55, index.level1AreaAt(80, 20));
        assertEquals(7, index.level1AreaAt(290, 210));
    }

    @Test
    void pointsOutsideEveryOutlineResolveToZero() {
        var index = fixture();
        // Inside area 55's bounding box but in the notch of the L.
        assertEquals(0, index.level1AreaAt(80, 80));
        // Inside area 7's bounding box but across its diagonal.
        assertEquals(0, index.level1AreaAt(210, 290));
        assertEquals(0, index.level1AreaAt(-5, -5));
    }

    @Test
    void degenerateOrMissingDataGivesAnEmptyIndex() {
        assertTrue(WorldAreaIndex.parse(JsonParser.parseString("{}")).isEmpty());
        assertTrue(WorldAreaIndex.parse(JsonParser.parseString("[]")).isEmpty());
        // A two-point outline cannot enclose anything and is skipped.
        assertTrue(
                WorldAreaIndex.parse(
                                JsonParser.parseString(
                                        "{\"OBKNAJCIHEA\": [{\"LKBLPFHOAFB\": {\"BBLLBIOLMHP\": 9,"
                                                + " \"JLFEAGHGCLP\": {\"BFLLDPOAJGD\": [{\"_x\": 0, \"_z\": 0},"
                                                + " {\"_x\": 1, \"_z\": 1}]}}}]}"))
                        .isEmpty());
    }

    private static WorldAreaIndex nestedFixture() {
        return WorldAreaIndex.parse(
                JsonParser.parseString(
                        """
                        {"OBKNAJCIHEA": [
                          {"LKBLPFHOAFB": {"BBLLBIOLMHP": 55, "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                            {"_x": 0, "_z": 0}, {"_x": 100, "_z": 0}, {"_x": 100, "_z": 100},
                            {"_x": 0, "_z": 100}]}},
                           "OPHHFKKPLDI": [
                            {"BBLLBIOLMHP": 55, "EOIFEEDBAFJ": 5501,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 10, "_z": 10}, {"_x": 30, "_z": 10},
                               {"_x": 30, "_z": 30}, {"_x": 10, "_z": 30}],
                               "MHLOLALFHHF": {"_y": 0},
                               "NFFDCMLAAFO": {"_y": 10}}},
                            {"BBLLBIOLMHP": 55, "EOIFEEDBAFJ": 5502,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 10, "_z": 10}, {"_x": 30, "_z": 10},
                               {"_x": 30, "_z": 30}, {"_x": 10, "_z": 30}],
                               "MHLOLALFHHF": {"_y": 11},
                               "NFFDCMLAAFO": {"_y": 20}}},
                            {"BBLLBIOLMHP": 55, "EOIFEEDBAFJ": 5503,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 50, "_z": 10}, {"_x": 70, "_z": 10},
                               {"_x": 70, "_z": 30}, {"_x": 50, "_z": 30}]}},
                            {"BBLLBIOLMHP": 55, "EOIFEEDBAFJ": 5503,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 80, "_z": 10}, {"_x": 90, "_z": 10},
                               {"_x": 90, "_z": 30}, {"_x": 80, "_z": 30}]}},
                            {"BBLLBIOLMHP": 9, "EOIFEEDBAFJ": 9999,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 40, "_z": 40}, {"_x": 50, "_z": 40},
                               {"_x": 50, "_z": 50}, {"_x": 40, "_z": 50}]}},
                            {"BBLLBIOLMHP": "invalid", "EOIFEEDBAFJ": 9997,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 40, "_z": 40}, {"_x": 50, "_z": 40},
                               {"_x": 50, "_z": 50}, {"_x": 40, "_z": 50}]}},
                            {"BBLLBIOLMHP": 55, "EOIFEEDBAFJ": 9998,
                             "JLFEAGHGCLP": {"BFLLDPOAJGD": [
                               {"_x": 40, "_z": 40}, {"_x": 50, "_z": 40}]}}
                           ]}
                        ]}
                        """));
    }

    @Test
    void level2RespectsHeightWhenPolygonsOverlapInXZ() {
        var index = nestedFixture();
        assertEquals(new WorldAreaIndex.AreaIds(55, 5501), index.areaAt(20, 5, 20));
        assertEquals(new WorldAreaIndex.AreaIds(55, 5502), index.areaAt(20, 15, 20));
        assertEquals(new WorldAreaIndex.AreaIds(55, 0), index.areaAt(20, -5, 20));
        assertEquals(new WorldAreaIndex.AreaIds(55, 0), index.areaAt(20, 25, 20));
        assertEquals(5502, index.level2AreaAt(20, 15, 20));
        assertEquals(55, index.level1AreaAt(20, 20));
    }

    @Test
    void separatePolygonsWithSameLevel2IdAreRetained() {
        var index = nestedFixture();
        assertEquals(5503, index.level2AreaAt(60, 120, 20));
        assertEquals(5503, index.level2AreaAt(85, -120, 20));
        assertEquals(0, index.level2AreaAt(75, 0, 20));
    }

    @Test
    void missingAndInvalidSubareasDoNotBecomeGuessedAreas() {
        var index = nestedFixture();
        assertEquals(new WorldAreaIndex.AreaIds(55, 0), index.areaAt(45, 0, 45));
        assertEquals(new WorldAreaIndex.AreaIds(0, 0), index.areaAt(150, 0, 20));
        assertFalse(index.isEmpty());
    }

    @Test
    void realScene3SupportsBothAreaLevels() {
        var file = Path.of("resources/BinOutput/Scene/WorldArea/scene3_worldArea.json");
        assumeTrue(Files.exists(file), "needs the extracted 7.1 world area resource");
        var index = WorldAreaIndex.forScene(3);
        // Ground-level Mondstadt polygons, confirmed against 7.1 world-area source.
        assertEquals(new WorldAreaIndex.AreaIds(1, 107), index.areaAt(2253.4f, 0, -1648.3f));
        // Area 1506 is a high-elevation region in level-1 area 10.
        assertEquals(new WorldAreaIndex.AreaIds(10, 1506), index.areaAt(1043.4f, 556.8f, -842.4f));
        assertNotEquals(1506, index.level2AreaAt(1043.4f, 0, -842.4f));
    }

    @Test
    void snezhnayaIsOnlyTheOverworld() {
        var inSnezhnaya = new Position(8500, 300, 7000);
        assertFalse(SnezhnayaFrigid.contains(5, inSnezhnaya));
        assertFalse(SnezhnayaFrigid.contains(3, null));
        // Mondstadt, far from areas 55..59.
        assertFalse(SnezhnayaFrigid.contains(3, new Position(-500, 200, 1000)));
    }

    @Test
    void realOutlinesPutAKresnikMineInSnezhnaya() {
        var file = Path.of("resources/BinOutput/Scene/WorldArea/scene3_worldArea.json");
        assumeTrue(Files.exists(file), "needs the extracted 7.1 resource data");
        // A point well inside area 56, where most of Snezhnaya's 7.0 gadgets stand.
        assertTrue(SnezhnayaFrigid.contains(3, new Position(9300, 300, 7300)));
    }
}
