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
    void nativeBlossomChestTypesUseSeparateRewardGadgets() {
        // BlossomChestExcelConfigData: SCOIN uses 70210109 and EXP uses 70210110.
        assertEquals(70210109, BlossomType.GOLD.getRewardChestGadgetId());
        assertEquals(70210110, BlossomType.BLUE.getRewardChestGadgetId());
        assertEquals(1, BlossomType.GOLD.getBlossomChestId());
        assertEquals(2, BlossomType.BLUE.getBlossomChestId());
    }

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
    void progressionUnlocksEachTypeAtItsExpectedAdventureRank() {
        assertFalse(BlossomManager.unlockedAtRank(BlossomType.BLUE, 7));
        assertTrue(BlossomManager.unlockedAtRank(BlossomType.BLUE, 8));
        assertFalse(BlossomManager.unlockedAtRank(BlossomType.GOLD, 11));
        assertTrue(BlossomManager.unlockedAtRank(BlossomType.GOLD, 12));
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
        // Both types remain candidates; selection chooses one per type and city.
        assertEquals(3, selected.size());
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 2));
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 3));
    }


    @Test
    void nativeRefreshCountAndAreaOwnershipSelectOneOfEachTypePerCity() {
        var sources = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":2,\"pos\":{\"x\":11,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":3,\"pos\":{\"x\":22,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":4,\"pos\":{\"x\":33,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":5,\"pos\":{\"x\":44,\"y\":2,\"z\":3}}");
        var mondstadt = JsonUtils.decode(
                "{\"cityId\":1,\"areaIdVec\":[1]}", emu.grasscutter.data.excels.CityData.class);
        var liyue = JsonUtils.decode(
                "{\"cityId\":2,\"areaIdVec\":[5]}", emu.grasscutter.data.excels.CityData.class);
        var gold = JsonUtils.decode(
                "{\"id\":1,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\","
                        + "\"refreshCount\":1,\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[12]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var blue = JsonUtils.decode(
                "{\"id\":2,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_EXP\","
                        + "\"refreshCount\":1,\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[8]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var candidates = BlossomManager.scriptedCandidates(
                java.util.Map.of(new GridBlockId(3, 0, 0, 0),
                        new ArrayList<>(sources.getSpawns())), 3);
        var cfg = List.of(gold, blue);
        var cities = List.of(mondstadt, liyue);
        var onlyGold = BlossomManager.selectActiveSpawns(candidates, 12, List.of(1),
                cities, cfg, spawn -> 1);
        assertEquals(2, onlyGold.size());
        assertEquals(1, onlyGold.stream().filter(
                entry -> entry.getGadgetId() == BlossomType.GOLD.getGadgetId()).count());
        assertEquals(1, onlyGold.stream().filter(
                entry -> entry.getGadgetId() == BlossomType.BLUE.getGadgetId()).count());
        assertEquals(1, BlossomManager.selectActiveSpawns(candidates, 8, List.of(1),
                cities, cfg, spawn -> 1).size());
        assertTrue(BlossomManager.selectActiveSpawns(candidates, 7, List.of(1),
                cities, cfg, spawn -> 1).isEmpty());
        assertTrue(BlossomManager.selectActiveSpawns(candidates, 12, List.of(1),
                cities, cfg, spawn -> 0).isEmpty());
    }

    @Test
    void selectedPositionsDoNotOverlapAcrossTypesAndConsumedCanRotate() {
        var sources = group(3, 133000001,
                "{\"gadgetId\":70360057,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":2,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":50,\"y\":2,\"z\":3}}");
        var city = JsonUtils.decode("{\"cityId\":1,\"areaIdVec\":[1]}",
                emu.grasscutter.data.excels.CityData.class);
        var gold = JsonUtils.decode("{\"id\":1,\"cityId\":1,"
                        + "\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\",\"refreshCount\":1,"
                        + "\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[12]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var blue = JsonUtils.decode("{\"id\":2,\"cityId\":1,"
                        + "\"refreshType\":\"BLOSSOM_REFRESH_EXP\",\"refreshCount\":1,"
                        + "\"refreshCondVec\":[{\"type\":"
                        + "\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[8]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var pool = BlossomManager.scriptedCandidates(java.util.Map.of(
                new GridBlockId(3, 0, 0, 0), new ArrayList<>(sources.getSpawns())), 3);
        var selected = BlossomManager.selectActiveSpawns(pool, 12, List.of(1),
                List.of(city), List.of(gold, blue), entry -> 1);
        assertEquals(List.of(1, 3),
                selected.stream().map(SpawnDataEntry::getConfigId).toList());
        var afterClaim = BlossomManager.selectActiveSpawns(
                pool.stream().filter(entry -> entry.getConfigId() != 1).toList(),
                12, List.of(1), List.of(city), List.of(gold, blue), entry -> 1);
        assertEquals(List.of(2),
                afterClaim.stream().map(SpawnDataEntry::getConfigId).toList());
    }

    private static SpawnGroupEntry group(int sceneId, int groupId, String... spawns) {
        var group = JsonUtils.decode(
                "{\"sceneId\":" + sceneId + ",\"groupId\":" + groupId
                        + ",\"spawns\":[" + String.join(",", spawns) + "]}", SpawnGroupEntry.class);
        group.getSpawns().forEach(spawn -> spawn.setGroup(group));
        return group;
    }
}
