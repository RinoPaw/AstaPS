package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.GridBlockId;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BlossomScriptedSpawnTest {
    @Test
    void scriptedSpawnsSelectOnlyAuthoredLeyLineOutcropsInTheCurrentScene() {
        var mondstadt = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":2,\"pos\":{\"x\":4,\"y\":5,\"z\":6}}",
                "{\"gadgetId\":70210109,\"configId\":3,\"pos\":{\"x\":7,\"y\":8,\"z\":9}}");
        var elsewhere = group(4, 144000001,
                "{\"gadgetId\":70360056,\"configId\":4,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}");
        var sources = new HashMap<GridBlockId, ArrayList<SpawnDataEntry>>();
        sources.put(new GridBlockId(3, 0, 0, 0), new ArrayList<>(mondstadt.getSpawns()));
        sources.put(new GridBlockId(4, 0, 0, 0), new ArrayList<>(elsewhere.getSpawns()));

        var selected = BlossomManager.scriptedCandidates(sources, 3);
        assertEquals(List.of(70360057, 70360056),
                selected.stream().map(SpawnDataEntry::getGadgetId).toList());
        assertTrue(BlossomManager.scriptedCandidates(sources, 5).isEmpty());
    }

    @Test
    void identicalLocationSourcesSelectOneInteractiveOutcrop() {
        var group = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":2,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":50,\"y\":2,\"z\":3}}");
        var lists = new HashMap<GridBlockId, ArrayList<SpawnDataEntry>>();
        lists.put(new GridBlockId(3, 0, 0, 0), new ArrayList<>(group.getSpawns()));
        var selected = BlossomManager.scriptedCandidates(lists, 3);
        assertEquals(2, selected.size());
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 2));
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 3));
    }

    @Test
    void selectedNativeRefreshHasOnlyOneSourcePerCityAndType() {
        var group = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":2,\"pos\":{\"x\":20,\"y\":2,\"z\":30}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":40,\"y\":2,\"z\":50}}");
        var pool = group.getSpawns();
        assertEquals(1, BlossomManager.pickSources(pool, 1, 0, 0).size());
        assertEquals(1, BlossomManager.pickSources(pool, 1, 0, 1).size());
        assertEquals(1, BlossomManager.pickSources(pool, 1, 0, 3).size());
        assertEquals(pool.get(0), BlossomManager.pickSources(pool, 1, 0, 3).get(0));
        assertEquals(pool.get(1), BlossomManager.pickSources(pool, 1, 0, 1).get(0));
    }

    @Test
    void originalRefreshConditionsControlAdventureRankAndAreaUnlock() {
        var mondstadtWealth = JsonUtils.decode(
                "{\"cityId\":1,\"refreshCount\":1,\"clientShowType\":\"BLOSSOM_SHOWTYPE_CHALLENGE\","
                        + "\"refreshCondVec\":[{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[12]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        assertFalse(BlossomManager.refreshEligible(mondstadtWealth, 11, java.util.Set.of(), List.of(1, 2)));
        assertTrue(BlossomManager.refreshEligible(mondstadtWealth, 12, java.util.Set.of(), List.of(1, 2)));
        var inazumaWealth = JsonUtils.decode(
                "{\"cityId\":3,\"refreshCount\":1,\"clientShowType\":\"BLOSSOM_SHOWTYPE_CHALLENGE\","
                        + "\"refreshCondVec\":[{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[18]},"
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY\",\"param\":[3]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        assertFalse(BlossomManager.refreshEligible(inazumaWealth, 18, java.util.Set.of(), List.of(11, 12)));
        assertTrue(BlossomManager.refreshEligible(inazumaWealth, 18, java.util.Set.of(11), List.of(11, 12)));
    }

    @Test
    void cityOwnershipUsesNativeCityAreaIds() {
        var mondstadt = new emu.grasscutter.data.excels.CityData();
        mondstadt.setCityId(1);
        mondstadt.setAreaIdVec(List.of(1, 2, 3));
        var liyue = new emu.grasscutter.data.excels.CityData();
        liyue.setCityId(2);
        liyue.setAreaIdVec(List.of(5, 6));
        assertEquals(1, BlossomManager.cityForArea(2, List.of(mondstadt, liyue)));
        assertEquals(2, BlossomManager.cityForArea(5, List.of(mondstadt, liyue)));
        assertEquals(0, BlossomManager.cityForArea(0, List.of(mondstadt, liyue)));
        assertEquals(0, BlossomManager.cityForArea(999, List.of(mondstadt, liyue)));
    }

    private static SpawnGroupEntry group(int sceneId, int groupId, String... spawns) {
        var group = JsonUtils.decode(
                "{\"sceneId\":" + sceneId + ",\"groupId\":" + groupId
                        + ",\"spawns\":[" + String.join(",", spawns) + "]}", SpawnGroupEntry.class);
        group.getSpawns().forEach(spawn -> spawn.setGroup(group));
        return group;
    }
}
