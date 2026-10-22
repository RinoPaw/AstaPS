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
    void nativeMondstadtRefreshConditionsGateAdventureRank() {
        var blue = emu.grasscutter.utils.JsonUtils.decode(
                "{\"id\":2,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_EXP\","
                + "\"refreshCount\":1,\"refreshCondVec\":["
                + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\","
                + "\"param\":[8]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var gold = emu.grasscutter.utils.JsonUtils.decode(
                "{\"id\":1,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\","
                + "\"refreshCount\":1,\"refreshCondVec\":["
                + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\","
                + "\"param\":[12]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var player = new emu.grasscutter.game.player.Player();
        player.getProperties().put(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_LEVEL.getId(), 7);
        assertFalse(BlossomManager.eligible(blue, player));
        player.getProperties().put(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_LEVEL.getId(), 8);
        assertTrue(BlossomManager.eligible(blue, player));
        assertFalse(BlossomManager.eligible(gold, player));
        player.getProperties().put(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_LEVEL.getId(), 12);
        assertTrue(BlossomManager.eligible(gold, player));
    }

    @Test
    void identicalLocationSourcesRetainBothPossibleRefreshTypes() {
        var group = group(3, 133000001,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":2,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":50,\"y\":2,\"z\":3}}");
        var lists = new HashMap<GridBlockId, ArrayList<SpawnDataEntry>>();
        lists.put(new GridBlockId(3, 0, 0, 0), new ArrayList<>(group.getSpawns()));
        var selected = BlossomManager.scriptedCandidates(lists, 3);
        assertEquals(3, selected.size());
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 1));
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 2));
        assertTrue(selected.stream().anyMatch(e -> e.getConfigId() == 3));
    }

    private static SpawnGroupEntry group(int sceneId, int groupId, String... spawns) {
        var group = JsonUtils.decode(
                "{\"sceneId\":" + sceneId + ",\"groupId\":" + groupId
                        + ",\"spawns\":[" + String.join(",", spawns) + "]}", SpawnGroupEntry.class);
        group.getSpawns().forEach(spawn -> spawn.setGroup(group));
        return group;
    }
}
