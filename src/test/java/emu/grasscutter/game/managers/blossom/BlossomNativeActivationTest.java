package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.List;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class BlossomNativeActivationTest {
    static SpawnDataEntry candidate(int groupId, int configId) {
        var g = JsonUtils.decode("{\"sceneId\":3,\"groupId\":" + groupId
                + ",\"spawns\":[{\"gadgetId\":70360056,\"configId\":" + configId
                + ",\"pos\":{\"x\":" + configId + ",\"z\":12}}]}", SpawnGroupEntry.class);
        var entry = g.getSpawns().getFirst();
        entry.setGroup(g);
        return entry;
    }

    @Test void refreshChangesAtFourAM() {
        assertEquals("2026-10-21", BlossomManager.refreshDay(LocalDateTime.parse("2026-10-22T03:59:59")));
        assertEquals("2026-10-22", BlossomManager.refreshDay(LocalDateTime.parse("2026-10-22T04:00:00")));
    }

    @Test void refreshCountAndClaimRotationUseSameOrderedSources() {
        var points = List.of(candidate(133000001, 1),candidate(133000002, 2),candidate(133000003, 3));
        var first = BlossomManager.selectSources(points,1,10001,"1:GOLD","2026-10-22",0);
        var next = BlossomManager.selectSources(points,1,10001,"1:GOLD","2026-10-22",1);
        assertEquals(1, first.size());
        assertEquals(1, next.size());
        assertNotEquals(first, next);
        assertEquals(first, BlossomManager.selectSources(points,1,10001,"1:GOLD","2026-10-22",3));
    }

    @Test void polygonsRejectPointsOutsideDefinedRegions() {
        double[] x={0,10,10,0}, z={0,0,10,10};
        assertTrue(BlossomAreaIndex.contains(x,z,5,5));
        assertFalse(BlossomAreaIndex.contains(x,z,-4,5));
        assertFalse(BlossomAreaIndex.contains(x,z,5,14));
    }
}
