package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class WaypointsCommandTest {
    @Test
    @DisplayName("7.1 scene teleport points are recognized as waypoints")
    public void sceneTransPointIsAccepted() {
        assertTrue(WaypointsCommand.isWaypointType("SceneTransPoint"));
    }

    @Test
    @DisplayName("legacy normal teleport points remain recognized")
    public void transPointNormalIsAccepted() {
        assertTrue(WaypointsCommand.isWaypointType("TransPointNormal"));
    }

    @Test
    @DisplayName("generic teleport points remain recognized")
    public void transPointIsAccepted() {
        assertTrue(WaypointsCommand.isWaypointType("TransPoint"));
    }

    @Test
    @DisplayName("virtual teleport points are not simple-unlocked")
    public void virtualTransPointIsRejected() {
        assertFalse(WaypointsCommand.isWaypointType("VirtualTransPoint"));
    }

    @Test
    @DisplayName("unrelated and missing point types are rejected")
    public void unrelatedTypesAreRejected() {
        assertFalse(WaypointsCommand.isWaypointType("DungeonEntry"));
        assertFalse(WaypointsCommand.isWaypointType(null));
    }
}