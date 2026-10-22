package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.BlossomGroupsExcelConfigData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.data.excels.BlossomSectionOrderExcelConfigData;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.GridBlockId;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import org.junit.jupiter.api.Test;

final class BlossomScriptedSpawnTest {
    @Test
    void importedSourcesAreDeduplicatedAndSceneScoped() {
        var mondstadt = group(3, 133002387,
                "{\"gadgetId\":70360056,\"configId\":1,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360057,\"configId\":2,\"pos\":{\"x\":1,\"y\":2,\"z\":3}}",
                "{\"gadgetId\":70360056,\"configId\":3,\"pos\":{\"x\":50,\"y\":2,\"z\":3}}");
        var other = group(4, 144000001,
                "{\"gadgetId\":70360056,\"configId\":4,\"pos\":{\"x\":3,\"y\":4,\"z\":5}}");
        var grids = new HashMap<GridBlockId, ArrayList<SpawnDataEntry>>();
        grids.put(new GridBlockId(3, 0, 0, 0), new ArrayList<>(mondstadt.getSpawns()));
        grids.put(new GridBlockId(4, 0, 0, 0), new ArrayList<>(other.getSpawns()));
        assertEquals(2, BlossomManager.scriptedCandidates(grids, 3).size());
        assertEquals(1, BlossomManager.scriptedCandidates(grids, 4).size());
    }

    @Test
    void cityAndNativeCampIdsDriveOneMarkerPerRefreshCategory() {
        var sources = testSources();
        var state = new BlossomRotationState();
        var early = select(7, 100, sources, state);
        assertTrue(early.isEmpty());
        var eight = select(8, 100, sources, state);
        assertEquals(1, eight.size());
        assertEquals(BlossomType.BLUE, eight.get(0).type());
        assertEquals(1, eight.get(0).cityId());
        // The imported source gadget may be misclassified; the refresh record owns the type.
        assertEquals(70360056, eight.get(0).spawn().getGadgetId());

        var twelve = select(12, 100, sources, state);
        assertEquals(2, twelve.size());
        assertEquals(Set.of(BlossomType.GOLD, BlossomType.BLUE),
                Set.of(twelve.get(0).type(), twelve.get(1).type()));
        assertEquals(2, twelve.stream().map(BlossomActiveSelection.Active::campId)
                .distinct().count());
        assertEquals(2, twelve.stream().map(x -> x.spawn().getPos()).distinct().count());
    }

    @Test
    void nativeGroupOwnershipKeepsOneOfEachFlowerInEachUnlockedCity() {
        var selections = select(18, 100, testSources(), new BlossomRotationState());
        assertEquals(4, selections.size());
        for (int cityId : List.of(1, 2)) {
            var city = selections.stream().filter(s -> s.cityId() == cityId).toList();
            assertEquals(2, city.size());
            assertEquals(Set.of(BlossomType.GOLD, BlossomType.BLUE),
                    Set.of(city.get(0).type(), city.get(1).type()));
        }
        assertEquals(4, selections.stream().map(x -> x.spawn().getPos())
                .distinct().count());
    }

    @Test
    void paidClaimAdvancesUsingAuthoredNextCampEdge() {
        var state = new BlossomRotationState();
        var sources = testSources();
        select(12, 100, sources, state);
        state.setCamp(200, 101002001);
        var selected = select(12, 100, sources, state);
        var blue = selected.stream().filter(x -> x.type() == BlossomType.BLUE)
                .findFirst().orElseThrow();
        assertEquals(101002001, blue.campId());
        BlossomActiveSelection.advance(blue, state, groupRows());
        assertEquals(101002002, state.getCamp(200));
        var next = select(12, 100, sources, state).stream()
                .filter(x -> x.type() == BlossomType.BLUE).findFirst().orElseThrow();
        assertEquals(101002002, next.campId());

        state.resetForDay(101);
        assertEquals(0, state.getCamp(200));
    }

    private static List<BlossomActiveSelection.Active> select(
            int rank, long day, List<SpawnDataEntry> sources, BlossomRotationState state) {
        return BlossomActiveSelection.select(rank, 70653, day, sources,
                groupRows(), sectionRows(), refreshRows(), state,
                r -> switch (r.getId()) {
                    case 1 -> rank >= 12;
                    case 2 -> rank >= 8;
                    case 3, 4 -> rank >= 18;
                    default -> false;
                });
    }

    private static List<SpawnDataEntry> testSources() {
        var first = group(3, 133002387,
                "{\"gadgetId\":70360056,\"configId\":387004,\"pos\":{\"x\":1,\"y\":3,\"z\":2}}");
        var second = group(3, 133002369,
                "{\"gadgetId\":70360056,\"configId\":369004,\"pos\":{\"x\":61,\"y\":3,\"z\":2}}");
        var third = group(3, 133001374,
                "{\"gadgetId\":70360056,\"configId\":374004,\"pos\":{\"x\":121,\"y\":3,\"z\":2}}");
        var liyue1 = group(3, 133102733,
                "{\"gadgetId\":70360056,\"configId\":733001,\"pos\":{\"x\":301,\"y\":3,\"z\":202}}");
        var liyue2 = group(3, 133102742,
                "{\"gadgetId\":70360056,\"configId\":742001,\"pos\":{\"x\":361,\"y\":3,\"z\":202}}");
        return List.of(first.getSpawns().get(0), second.getSpawns().get(0),
                third.getSpawns().get(0), liyue1.getSpawns().get(0),
                liyue2.getSpawns().get(0));
    }

    private static List<BlossomGroupsExcelConfigData> groupRows() {
        return List.of(
                json(BlossomGroupsExcelConfigData.class, """
                        {"id":101002001,"cityId":1,"sectionId":1002,"limit_level":8,
                         "refreshTypeVec":[1,3],"newGroupVec":[133002387],
                         "nextCampIdVec":[101002002]}
                        """),
                json(BlossomGroupsExcelConfigData.class, """
                        {"id":101002002,"cityId":1,"sectionId":1002,"limit_level":8,
                         "refreshTypeVec":[1,3],"newGroupVec":[133002369],
                         "nextCampIdVec":[101001001]}
                        """),
                json(BlossomGroupsExcelConfigData.class, """
                        {"id":101001001,"cityId":1,"sectionId":1001,"limit_level":12,
                         "refreshTypeVec":[1,3],"newGroupVec":[133001374],
                         "nextCampIdVec":[101002001]}
                        """),
                json(BlossomGroupsExcelConfigData.class, """
                        {"id":202001001,"cityId":2,"sectionId":2001,"limit_level":18,
                         "refreshTypeVec":[1,3],"newGroupVec":[133102733],
                         "nextCampIdVec":[202001002]}
                        """),
                json(BlossomGroupsExcelConfigData.class, """
                        {"id":202001002,"cityId":2,"sectionId":2001,"limit_level":18,
                         "refreshTypeVec":[1,3],"newGroupVec":[133102742],
                         "nextCampIdVec":[202001001]}
                        """));
    }

    private static List<BlossomSectionOrderExcelConfigData> sectionRows() {
        return List.of(
                json(BlossomSectionOrderExcelConfigData.class,
                        "{\"id\":1,\"cityId\":1,\"sectionId\":1001,\"order\":1}"),
                json(BlossomSectionOrderExcelConfigData.class,
                        "{\"id\":2,\"cityId\":1,\"sectionId\":1002,\"order\":2}"),
                json(BlossomSectionOrderExcelConfigData.class,
                        "{\"id\":3,\"cityId\":2,\"sectionId\":2001,\"order\":1}"));
    }

    private static List<BlossomRefreshExcelConfigData> refreshRows() {
        return List.of(
                json(BlossomRefreshExcelConfigData.class,
                        "{\"id\":1,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\",\"refreshCount\":1}"),
                json(BlossomRefreshExcelConfigData.class,
                        "{\"id\":2,\"cityId\":1,\"refreshType\":\"BLOSSOM_REFRESH_EXP\",\"refreshCount\":1}"),
                json(BlossomRefreshExcelConfigData.class,
                        "{\"id\":3,\"cityId\":2,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\",\"refreshCount\":1}"),
                json(BlossomRefreshExcelConfigData.class,
                        "{\"id\":4,\"cityId\":2,\"refreshType\":\"BLOSSOM_REFRESH_EXP\",\"refreshCount\":1}"));
    }

    private static <T> T json(Class<T> type, String value) {
        return JsonUtils.decode(value, type);
    }

    private static SpawnGroupEntry group(int sceneId, int groupId, String... entries) {
        var group = JsonUtils.decode("{\"sceneId\":" + sceneId + ",\"groupId\":" + groupId
                + ",\"spawns\":[" + String.join(",", entries) + "]}", SpawnGroupEntry.class);
        group.getSpawns().forEach(spawn -> spawn.setGroup(group));
        return group;
    }
}
