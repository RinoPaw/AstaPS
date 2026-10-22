package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.BlossomGroupsExcelConfigData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;

final class BlossomSelectionTest {
    private static final String SCOIN = "BLOSSOM_REFRESH_SCOIN";
    private static final String EXP = "BLOSSOM_REFRESH_EXP";

    private static BlossomRefreshExcelConfigData row(int id, int city, String type, int rank) {
        return JsonUtils.decode(
                "{\"id\":" + id + ",\"cityId\":" + city + ",\"refreshType\":\""
                        + type + "\",\"refreshCount\":1,\"refreshTime\":\"04:00:00\","
                        + "\"refreshCondVec\":[{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\","
                        + "\"param\":[" + rank + "]}]}",
                BlossomRefreshExcelConfigData.class);
    }

    private static SpawnDataEntry source(int city, int gadgetId, int config, int x) {
        SpawnGroupEntry group = JsonUtils.decode(
                "{\"sceneId\":3,\"groupId\":" + city
                        + ",\"spawns\":[{\"gadgetId\":" + gadgetId + ",\"configId\":"
                        + config + ",\"pos\":{\"x\":" + x + ",\"y\":2,\"z\":3}}]}",
                SpawnGroupEntry.class);
        SpawnDataEntry source = group.getSpawns().get(0);
        source.setGroup(group);
        return source;
    }

    private static List<BlossomSelection.Active> choose(
            List<SpawnDataEntry> sources, List<BlossomRefreshExcelConfigData> rows,
            int rank, long day, Map<Integer, Integer> rotations) {
        return BlossomSelection.select(sources, rows,
                source -> source.getGroup().getGroupId(),
                row -> row.getRefreshCondVec()[0].getParam().get(0) <= rank,
                row -> day, rotations, Map.of(), 70653, List.of());
    }

    @Test
    void candidateCityNeedsAgreementBetweenNativeGroupAndNativeArea() {
        var nativeGroup = JsonUtils.decode(
                "{\"id\":101002003,\"cityId\":1,\"newGroupVec\":[133002369]}",
                BlossomGroupsExcelConfigData.class);
        assertEquals(1, BlossomSelection.cityForGroup(133002369, 1, List.of(nativeGroup)));
        assertEquals(0, BlossomSelection.cityForGroup(133002369, 2, List.of(nativeGroup)));
        assertEquals(0, BlossomSelection.cityForGroup(133099999, 1, List.of(nativeGroup)));
        assertEquals(0, BlossomSelection.cityForGroup(133002369, 0, List.of(nativeGroup)));
    }

    @Test
    void sourcePoolUsesNativePerCityCountsAndProgression() {
        var sources = List.of(
                source(1, 70360056, 11, 10), source(1, 70360056, 12, 30),
                source(1, 70360057, 13, 50), source(1, 70360057, 14, 70),
                source(2, 70360056, 21, 90), source(2, 70360057, 22, 110),
                source(0, 70360056, 31, 130));
        var rows = List.of(row(1, 1, SCOIN, 12), row(2, 1, EXP, 8),
                row(3, 2, SCOIN, 18), row(4, 2, EXP, 18));
        assertTrue(choose(sources, rows, 7, 100, Map.of()).isEmpty());
        assertEquals(List.of(2), choose(sources, rows, 8, 100, Map.of())
                .stream().map(BlossomSelection.Active::refreshId).toList());
        assertEquals(Set.of(1, 2), new HashSet<>(choose(sources, rows, 12, 100, Map.of())
                .stream().map(BlossomSelection.Active::refreshId).toList()));
        assertEquals(Set.of(1, 2, 3, 4), new HashSet<>(choose(sources, rows, 18, 100, Map.of())
                .stream().map(BlossomSelection.Active::refreshId).toList()));
    }

    @Test
    void collisionsDoNotProduceTwoActiveOutcrops() {
        var sources = List.of(source(1, 70360057, 1, 10),
                source(1, 70360056, 2, 10), source(1, 70360056, 3, 90));
        var rows = List.of(row(1, 1, EXP, 8), row(2, 1, SCOIN, 12));
        var selected = choose(sources, rows, 18, 100, Map.of());
        assertEquals(2, selected.size());
        assertEquals(Set.of(10f, 90f), new HashSet<>(selected.stream()
                .map(r -> r.spawn().getPos().getX()).toList()));
    }

    @Test
    void nextAuthoredGroupOverridesDailyRandomChoice() {
        var sources = List.of(source(1, 70360056, 11, 10),
                source(1, 70360056, 12, 20), source(1, 70360056, 13, 30));
        var rows = List.of(row(1, 1, SCOIN, 12));
        var chosen = BlossomSelection.select(sources, rows, s -> 1,
                r -> true, r -> 100, Map.of(), Map.of(1, 1), 70653, List.of());
        assertEquals(1, chosen.size());
        assertEquals(1, chosen.get(0).spawn().getGroup().getGroupId());
        // All three fixture sources share group 1; the selected group must be stable.
        assertTrue(sources.contains(chosen.get(0).spawn()));
    }

    @Test
    void claimedOutcropRotatesAndDoesNotReappearInSameRefresh() {
        var sources = List.of(source(1, 70360056, 1, 10),
                source(1, 70360056, 2, 20), source(1, 70360056, 3, 30));
        var rows = List.of(row(1, 1, SCOIN, 12));
        var chosen = choose(sources, rows, 12, 100, Map.of()).get(0).spawn();
        var moved = BlossomSelection.select(sources, rows,
                s -> 1, r -> true, r -> 100, Map.of(1, 1), Map.of(), 70653, List.of(chosen));
        assertEquals(1, moved.size());
        assertNotSame(chosen, moved.get(0).spawn());
        assertEquals(1, choose(sources, rows, 12, 101, Map.of()).size());
    }
}
