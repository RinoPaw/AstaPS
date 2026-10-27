package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.game.managers.blossom.BlossomManager.BlossomType;

import emu.grasscutter.utils.JsonUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BlossomScriptedSpawnTest {
    private static emu.grasscutter.data.excels.BlossomCampData camp(
            int id, int city, List<Integer> types, int... next) {
        var edges = java.util.Arrays.stream(next).mapToObj(Integer::toString)
                .collect(java.util.stream.Collectors.joining(","));
        return JsonUtils.decode(
                "{\"id\":" + id + ",\"cityId\":" + city
                        + ",\"refreshTypeVec\":" + types
                        + ",\"newGroupVec\":[123],\"nextCampIdVec\":[" + edges + "]}",
                emu.grasscutter.data.excels.BlossomCampData.class);
    }

    /** Execute legacy graph assertions using the actual node graph. */
    private static int findAvailableCamp(
            java.util.Collection<Integer> starts,
            Map<Integer, emu.grasscutter.data.excels.BlossomCampData> camps,
            int cityId, int nativeType, Set<Integer> occupied,
            java.util.function.Predicate<emu.grasscutter.data.excels.BlossomCampData> usable) {
        var graph = new BlossomGraph.CityGraph(cityId, camps.values());
        for (int id : occupied) {
            var node = graph.nodes.get(id);
            if (node != null)
                node.blossom = new BlossomManager.Blossom(BlossomType.WEALTH, 0);
        }
        var startsInCity = starts.stream().map(graph.nodes::get)
                .filter(java.util.Objects::nonNull).toList();
        var result = graph.findAvailableCamp(startsInCity, node -> {
            var data = camps.get(node.campId);
            return data.getRefreshTypeVec() != null
                    && data.getRefreshTypeVec().contains(nativeType)
                    && data.getNewGroupVec() != null && !data.getNewGroupVec().isEmpty()
                    && usable.test(data);
        });
        return result == null ? 0 : result.campId;
    }

    @Test
    void cityGraphLinksBranchesCyclesAndSeparatesCountries() {
        var rows = List.of(
                camp(1, 1, List.of(1, 3), 2, 3, 99),
                camp(2, 1, List.of(1, 3), 1),
                camp(3, 1, List.of(1, 3), 1),
                camp(99, 2, List.of(1, 3), 1));
        var graph = new BlossomGraph.CityGraph(1, rows);
        assertEquals(3, graph.nodes.size());
        assertEquals(List.of(2, 3), graph.nodes.get(1).next.stream().map(n -> n.campId).toList());
        assertEquals(List.of(1), graph.nodes.get(2).next.stream().map(n -> n.campId).toList());

        graph.nodes.get(1).blossom = new BlossomManager.Blossom(BlossomType.WEALTH, 0);
        var available = graph.findAvailableCamp(
                List.of(graph.nodes.get(1)), node -> true);
        assertEquals(2, available.campId);
    }

    @Test
    void eachCountryOwnsBothFlowerCursorsAndItsCampOccupancy() {
        var graph = new HashMap<Integer, emu.grasscutter.data.excels.BlossomCampData>();
        for (int city = 1; city <= 8; city++) {
            int a = city * 1000 + 1;
            int b = city * 1000 + 2;
            int c = city * 1000 + 3;
            graph.put(a, camp(a, city, List.of(1, 3), b));
            graph.put(b, camp(b, city, List.of(1, 3), c));
            graph.put(c, camp(c, city, List.of(1, 3), a));
        }

        var occupiedByCity = new HashMap<Integer, Set<Integer>>();
        for (int city = 1; city <= 8; city++) {
            var occupied = occupiedByCity.computeIfAbsent(city, unused -> new HashSet<>());
            int start = city * 1000 + 1;
            int gold = findAvailableCamp(
                    List.of(start), graph, city, 1, occupied, unused -> true);
            assertEquals(start, gold);
            occupied.add(gold);
            int blue = findAvailableCamp(
                    List.of(start), graph, city, 3, occupied, unused -> true);
            assertEquals(start + 1, blue);
            occupied.add(blue);
        }
        assertEquals(8, occupiedByCity.size());
        occupiedByCity.values().forEach(occupied -> assertEquals(2, occupied.size()));

        // Claiming the fourth country's gold flower must not move any other country's cursor.
        int next = findAvailableCamp(
                List.of(4002), graph, 4, 1, Set.of(4002), unused -> true);
        assertEquals(4003, next);
        assertEquals(Set.of(1001, 1002), occupiedByCity.get(1));
        assertEquals(Set.of(5001, 5002), occupiedByCity.get(5));
    }

    @Test
    void dailyInitialCandidatesUseTheSmallestCampIdInEachSection() {
        var camps = List.of(
                sectionCamp(303002003, 3002, List.of(1, 3)),
                sectionCamp(303001005, 3001, List.of(1, 3)),
                sectionCamp(303001001, 3001, List.of(1, 3)),
                sectionCamp(303002001, 3002, List.of(1, 3)),
                sectionCamp(303003001, 3003, List.of(3)),
                sectionCamp(303001000, 3001, List.of(4)));
        var graph = new BlossomGraph.CityGraph(3, camps);
        assertEquals(Set.of(303001001, 303002001), graph.sectionHeads(BlossomType.WEALTH).stream()
                .map(node -> node.campId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(303001001, 303002001, 303003001),
                graph.sectionHeads(BlossomType.REVELATION).stream()
                        .map(node -> node.campId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(new BlossomGraph.CityGraph(3, List.of()).sectionHeads(BlossomType.WEALTH).isEmpty());
    }

    private static emu.grasscutter.data.excels.BlossomCampData sectionCamp(
            int id, int section, List<Integer> types) {
        return JsonUtils.decode(
                "{\"id\":" + id + ",\"cityId\":3,\"sectionId\":" + section
                        + ",\"refreshTypeVec\":" + types + ",\"newGroupVec\":[123]}",
                emu.grasscutter.data.excels.BlossomCampData.class);
    }

    @Test
    void mapPreviewsUseEachCountryOwnRefreshRow() {
        var mondstadt = JsonUtils.decode(
                "{\"id\":1,\"cityId\":1,\"dropVec\":[{\"preview_reward\":4101}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var liyue = JsonUtils.decode(
                "{\"id\":3,\"cityId\":2,\"dropVec\":[{\"preview_reward\":4201}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        assertEquals(4101, BlossomManager.getPreviewReward(mondstadt, 0));
        assertEquals(4201, BlossomManager.getPreviewReward(liyue, 0));

        var nineTiers = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> "{\"preview_reward\":" + (5000 + i) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        var capped = JsonUtils.decode(
                "{\"id\":26,\"cityId\":4,\"dropVec\":[" + nineTiers + "]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        assertEquals(5008, BlossomManager.getPreviewReward(capped, 9));
    }

    @Test
    void adventureRankControlsTheKindsWithinEachCountry() {
        var refresh = JsonUtils.decode(
                "{\"id\":20,\"cityId\":3,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\","
                    + "\"refreshCondVec\":["
                    + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[18]},"
                    + "{\"type\":\"BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY\",\"param\":[3]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        assertFalse(BlossomManager.refreshEligible(refresh, 17));
        assertTrue(BlossomManager.refreshEligible(refresh, 18));
        assertTrue(BlossomManager.refreshEligible(refresh, 20));
    }

    @Test
    void adventureRankRangeCanRetireAFlowerKind() {
        var refresh = JsonUtils.decode(
                "{\"id\":44,\"cityId\":7,\"refreshType\":\"BLOSSOM_REFRESH_CRYSTAL\","
                        + "\"refreshCondVec\":["
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[2]},"
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_LESS_THAN\",\"param\":[30]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        assertFalse(BlossomManager.refreshEligible(refresh, 1));
        assertTrue(BlossomManager.refreshEligible(refresh, 2));
        assertTrue(BlossomManager.refreshEligible(refresh, 29));
        assertFalse(BlossomManager.refreshEligible(refresh, 30));
    }

    @Test
    void importedMondstadtRefreshRanksAndEmptyConditionsFailClosed() {
        // Imported AstaPS-Resource 7.1 refresh rows: EXP=AR8; SCOIN=AR12.
        // Native tables pad refreshCondVec with empty objects, which are not rules.
        var blue = JsonUtils.decode(
                "{\"id\":2,\"cityId\":1,\"refreshCondVec\":["
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[8]},"
                        + "{},{}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var gold = JsonUtils.decode(
                "{\"id\":1,\"cityId\":1,\"refreshCondVec\":["
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[12]},"
                        + "{},{}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var empty = JsonUtils.decode(
                "{\"id\":2,\"cityId\":1,\"refreshCondVec\":[{},{},{}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);

        assertFalse(BlossomManager.refreshEligible(blue, 7));
        assertTrue(BlossomManager.refreshEligible(blue, 8));
        assertFalse(BlossomManager.refreshEligible(gold, 11));
        assertTrue(BlossomManager.refreshEligible(gold, 12));
        assertFalse(BlossomManager.refreshEligible(empty, 60));
    }

    @Test
    void campGraphCannotTraverseASecondCountry() {
        var graph = Map.of(
                101, camp(101, 1, List.of(1, 3), 201),
                201, camp(201, 2, List.of(1, 3), 102),
                102, camp(102, 1, List.of(1, 3), 101));
        assertEquals(0, findAvailableCamp(
                List.of(101), graph, 1, 1, Set.of(101), unused -> true));
        assertEquals(201, findAvailableCamp(
                List.of(201), graph, 2, 1, Set.of(), unused -> true));
    }

    @Test
    void missingLuaInOneCountryDoesNotHideFlowersInAnother() {
        var graph = Map.of(
                101, camp(101, 1, List.of(1, 3), 102),
                102, camp(102, 1, List.of(1, 3), 101),
                301, camp(301, 3, List.of(1, 3), 302),
                302, camp(302, 3, List.of(1, 3), 301));
        assertEquals(101, findAvailableCamp(
                List.of(101), graph, 1, 1, Set.of(), node -> node.getCityId() == 1));
        assertEquals(102, findAvailableCamp(
                List.of(101), graph, 1, 3, Set.of(101), node -> node.getCityId() == 1));
        assertEquals(0, findAvailableCamp(
                List.of(301), graph, 3, 1, Set.of(), node -> node.getCityId() == 1));
    }

    @Test
    void sharedGraphSkipsAnOccupiedCampForEitherFlowerType() {
        var a = camp(1, 1, List.of(1, 3), 2);
        var b = camp(2, 1, List.of(1, 3), 3);
        var c = camp(3, 1, List.of(1, 3), 1);
        var graph = Map.of(1, a, 2, b, 3, c);
        assertEquals(3, findAvailableCamp(
                List.of(2), graph, 1, 1, Set.of(2), node -> true));
        assertEquals(3, findAvailableCamp(
                List.of(2), graph, 1, 3, Set.of(2), node -> true));
        assertEquals(0, findAvailableCamp(
                List.of(2), graph, 1, 1, Set.of(1, 2, 3), node -> true));
    }

    @Test
    void singleFlowerMayReturnToItsOnlyAuthoredCamp() {
        var a = camp(1, 1, List.of(1), 1);
        assertEquals(1, findAvailableCamp(
                List.of(1), Map.of(1, a), 1, 1, Set.of(), node -> true));
    }

    @Test
    void missingLuaDoesNotAuthorizeUnconnectedFallbackCamps() {
        var a = camp(1, 1, List.of(1, 3), 2);
        var b = camp(2, 1, List.of(1, 3), 3);
        var c = camp(3, 1, List.of(1, 3), 1);
        var other = camp(4, 1, List.of(1, 3), 4);
        var graph = Map.of(1, a, 2, b, 3, c, 4, other);
        assertEquals(3, findAvailableCamp(
                List.of(1), graph, 1, 1, Set.of(), node -> node.getId() == 3));
        assertEquals(0, findAvailableCamp(
                List.of(1), graph, 1, 1, Set.of(), node -> node.getId() == 4));
    }

    @Test
    void blossomIdentityUsesOnlyItsGadgetId() {
        assertSame(BlossomType.forRefresh("BLOSSOM_REFRESH_SCOIN"), BlossomType.valueOf(70360056));
        assertSame(BlossomType.forRefresh("BLOSSOM_REFRESH_EXP"), BlossomType.valueOf(70360057));
        assertNull(BlossomType.valueOf(70210106));
        assertNull(BlossomType.valueOf(70220103));
    }

    @Test
    void nativeCampKeepsTheAuthoredLuaGadgetWithoutDuplicatingSpawnData() {
        var gadget = new emu.grasscutter.scripts.data.SceneGadget();
        gadget.config_id = 374004;
        gadget.gadget_id = BlossomType.forRefresh("BLOSSOM_REFRESH_SCOIN").getGadgetId();
        var node = new BlossomGraph.CampNode(101001001);
        node.blossom = new BlossomManager.Blossom(BlossomType.WEALTH, 0);
        var camp = node;
        assertEquals(101001001, camp.campId);
        assertEquals(374004, gadget.config_id);
        assertSame(camp, new BlossomManager.BlossomActivity(null, camp).source());
    }

    @Test
    void originalMonsterProgressFinishesOnlyOnce() {
        var node = new BlossomGraph.CampNode(1);
        node.blossom = new BlossomManager.Blossom(BlossomType.WEALTH, 0);
        var activity = new BlossomManager.BlossomActivity(null, node);
        activity.beginNative(2);
        assertFalse(activity.addNativeProgress());
        assertTrue(activity.addNativeProgress());
        assertFalse(activity.addNativeProgress());
    }

    @Test
    void originalBlossomCampIdsAndLinksAreSourceBacked() {
        var mondstadtWealth = JsonUtils.decode(
                "{\"id\":101001001,\"cityId\":1,\"limit_level\":12,"
                        + "\"sectionId\":1001,\"refreshTypeVec\":[1,3],"
                        + "\"newGroupVec\":[133001374],\"nextCampIdVec\":[101001002]}",
                emu.grasscutter.data.excels.BlossomCampData.class);
        var mondstadtRevelation = JsonUtils.decode(
                "{\"id\":101002003,\"cityId\":1,\"limit_level\":8,"
                        + "\"sectionId\":1002,\"refreshTypeVec\":[1,3],"
                        + "\"newGroupVec\":[133002369],\"nextCampIdVec\":[101002004]}",
                emu.grasscutter.data.excels.BlossomCampData.class);
        assertEquals(133001374, mondstadtWealth.getNewGroupVec().get(0));
        assertEquals(101001002, mondstadtWealth.getNextCampIdVec().get(0));
        assertEquals(133002369, mondstadtRevelation.getNewGroupVec().get(0));
        assertEquals(101002004, mondstadtRevelation.getNextCampIdVec().get(0));
    }

    @Test
    void unlockedAreasFilterIndividualCampsForAnyNativeType() {
        var graph = Map.of(
                101, camp(101, 21, List.of(17), 102),
                102, camp(102, 21, List.of(17), 103),
                103, camp(103, 21, List.of(17), 101));
        var areaByCamp = Map.of(101, 4, 102, 5, 103, 6);
        var unlocked = Set.of(5, 6);
        assertEquals(102, findAvailableCamp(
                List.of(101), graph, 21, 17, Set.of(),
                candidate -> unlocked.contains(areaByCamp.get(candidate.getId()))));
        assertEquals(103, findAvailableCamp(
                List.of(101), graph, 21, 17, Set.of(102),
                candidate -> unlocked.contains(areaByCamp.get(candidate.getId()))));
    }

    @Test
    void nativeRunIdentitySurvivesRepeatedSelectionAndChangesOnlyAfterRotation() {
        var host = new emu.grasscutter.game.player.Player() {
            @Override public void save() {}
        };
        assertEquals(0, host.getBlossomCampId(11, 0));
        host.setBlossomCampId(11, 0, 101001001);
        assertEquals(1, host.getBlossomIncarnation(11, 0));
        host.setBlossomRewardReady(11, 0);
        assertTrue(host.isBlossomRewardReady(11, 0));
        host.setBlossomCampId(11, 0, 101001001);
        assertEquals(1, host.getBlossomIncarnation(11, 0));
        assertTrue(host.isBlossomRewardReady(11, 0));
        host.setBlossomCampId(11, 0, 101001002);
        assertEquals(2, host.getBlossomIncarnation(11, 0));
        assertFalse(host.isBlossomRewardReady(11, 0));
        host.setBlossomConsumed(11, 0);
        assertTrue(host.isBlossomConsumed(11, 0));
        var run = "123:11:2";
        assertEquals(0, host.getBlossomClaimAttempt(run));
        host.advanceBlossomClaimAttempt(run);
        assertEquals(1, host.getBlossomClaimAttempt(run));
    }

    @Test
    void oneRefreshCanKeepMultipleIndependentCampSlots() {
        var host = new emu.grasscutter.game.player.Player() {
            @Override public void save() {}
        };
        for (int slot = 0; slot < 4; slot++) {
            host.setBlossomCampId(22, slot, 100 + slot);
            assertEquals(100 + slot, host.getBlossomCampId(22, slot));
            assertEquals(1, host.getBlossomIncarnation(22, slot));
        }
        host.setBlossomRewardReady(22, 1);
        host.advanceBlossomCampId(22, 2, 103);
        host.setBlossomConsumed(22, 3);
        assertTrue(host.isBlossomRewardReady(22, 1));
        assertFalse(host.isBlossomRewardReady(22, 2));
        assertEquals(2, host.getBlossomIncarnation(22, 2));
        assertTrue(host.isBlossomConsumed(22, 3));
        assertEquals(1, host.getBlossomIncarnation(22, 0));
        host.resetConsumedBlossomsForNewDay();
        assertFalse(host.isBlossomConsumed(22, 3));
        assertEquals(0, host.getBlossomCampId(22, 3));
        assertEquals(2, host.getBlossomIncarnation(22, 3));
        host.setBlossomCampId(22, 3, 104);
        assertEquals(2, host.getBlossomIncarnation(22, 3));
        assertTrue(host.isBlossomRewardReady(22, 1));
    }

    @Test
    void returningToTheSameAuthoredCampStartsAFreshClaimEpoch() {
        var host = new emu.grasscutter.game.player.Player() {
            @Override public void save() {}
        };
        host.setBlossomCampId(11, 0, 101001001);
        host.setBlossomRewardReady(11, 0);
        assertEquals(1, host.getBlossomIncarnation(11, 0));

        host.advanceBlossomCampId(11, 0, 101001001);
        assertEquals(101001001, host.getBlossomCampId(11, 0));
        assertEquals(2, host.getBlossomIncarnation(11, 0));
        assertFalse(host.isBlossomRewardReady(11, 0));
        assertFalse(host.isBlossomConsumed(11, 0));

        // Re-selecting the same run must not create a second receipt identity.
        host.setBlossomCampId(11, 0, 101001001);
        assertEquals(2, host.getBlossomIncarnation(11, 0));
    }

    @Test
    void dailyResetRevivesOnlyExhaustedRunsAndKeepsUnclaimedRewards() {
        var host = new emu.grasscutter.game.player.Player() {
            @Override public void save() {}
        };
        host.setBlossomCampId(11, 0, 101001001);
        host.setBlossomCampId(12, 0, 101002003);
        host.setBlossomRewardReady(11, 0);
        host.setBlossomConsumed(11, 0);
        host.setBlossomRewardReady(12, 0);

        assertTrue(host.resetConsumedBlossomsForNewDay());
        assertFalse(host.isBlossomConsumed(11, 0));
        assertFalse(host.isBlossomRewardReady(11, 0));
        assertEquals(2, host.getBlossomIncarnation(11, 0));
        assertEquals(0, host.getBlossomCampId(11, 0));
        host.setBlossomCampId(11, 0, 101002001);
        assertEquals(101002001, host.getBlossomCampId(11, 0));
        assertEquals(2, host.getBlossomIncarnation(11, 0));

        // An earned but unclaimed reward still refers to the original epoch.
        assertTrue(host.isBlossomRewardReady(12, 0));
        assertEquals(1, host.getBlossomIncarnation(12, 0));
        assertFalse(host.resetConsumedBlossomsForNewDay());
        assertEquals(2, host.getBlossomIncarnation(11, 0));
    }

    @Test
    void luaRefreshTypesFollowAuthoredGadgetSelection() {
        assertEquals(1, BlossomManager.luaRefreshType(BlossomType.forRefresh("BLOSSOM_REFRESH_SCOIN")));
        assertEquals(3, BlossomManager.luaRefreshType(BlossomType.forRefresh("BLOSSOM_REFRESH_EXP")));
        assertNull(BlossomManager.luaRefreshType(null));
    }

    @Test
    void legacySpawnGridNeverProvidesASecondLeyLine() {
        var legacy = JsonUtils.decode(
                "{\"gadgetId\":70360057}", emu.grasscutter.game.world.SpawnDataEntry.class);
        var ordinary = JsonUtils.decode(
                "{\"gadgetId\":70330001}", emu.grasscutter.game.world.SpawnDataEntry.class);
        var manager = new BlossomManager(null);
        assertFalse(manager.isActiveSpawn(legacy));
        assertTrue(manager.isActiveSpawn(ordinary));
    }

    @Test
    void blossomClaimsRequireCompletedGadgetInteractions() {
        var start = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .newBuilder()
                .setOpType(emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType
                        .InterOpType_INTER_OP_START)
                .setGadgetEntityId(1001)
                .build();
        var finish = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .newBuilder()
                .setOpType(emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType
                        .InterOpType_INTER_OP_FINISH)
                .setGadgetEntityId(1001)
                .build();

        assertFalse(BlossomManager.isClaimFinish(null));
        assertFalse(BlossomManager.isClaimFinish(
                emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                        .getDefaultInstance()));
        assertFalse(BlossomManager.isClaimFinish(start));
        assertTrue(BlossomManager.isClaimFinish(finish));
    }

    @Test
    void global71OriginalClient20And40ResinPacketsSelectSeparateAmounts() throws Exception {
        // Captured GadgetInteractReq FINISH packets from the Global 7.1 client.
        var req20 = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .parseFrom(java.util.HexFormat.of().parseHex("20bea4bd21380158a98980047001"));
        var req40 = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .parseFrom(java.util.HexFormat.of().parseHex("20bea4bd21380158f88880047002"));
        assertTrue(BlossomManager.isClaimFinish(req20));
        assertTrue(BlossomManager.isClaimFinish(req40));
        assertEquals(1, BlossomManager.normalClaimCount(req20));
        assertEquals(2, BlossomManager.normalClaimCount(req40));
        assertEquals(1, req20.getResinCostTypeValue());
        assertEquals(1, req40.getResinCostTypeValue());
    }

    @Test
    void global71ResinModesUseTheirOwnDebitsAndRewardMultipliers() {
        var normal = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 1);
        assertEquals(1, normal.rolls());
        assertEquals(20, normal.amount());
        assertEquals(106, normal.itemId());

        var doubleClaim = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 2);
        assertEquals(2, doubleClaim.rolls());
        assertEquals(40, doubleClaim.amount());
        assertEquals(106, doubleClaim.itemId());
        assertNull(BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 0));
        assertNull(BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_NORMAL, 0, 3));

        var condensed = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_CONDENSE, 0, 0);
        assertEquals(3, condensed.rolls());
        assertEquals(220007, condensed.itemId());
        assertEquals(1, condensed.amount());

        var fragile = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_FRAGILE, 0, 0);
        assertEquals(3, fragile.rolls());
        assertEquals(107009, fragile.itemId());

        var transientResin = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_TIMING, 0, 0);
        assertEquals(3, transientResin.rolls());
        assertEquals(107012, transientResin.itemId());

        var primogems = BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_HCOIN, 0, 0);
        assertEquals(3, primogems.rolls());
        assertEquals(201, primogems.itemId());
        assertEquals(50, primogems.amount());
        assertNull(BlossomManager.claimCost(
                emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType.ResinCostType_HCOIN,
                emu.grasscutter.game.managers.ResinManager.MAX_RESIN_BUYING_COUNT, 0));
    }

    @Test
    void createGadgetRejectsInvalidRequestsRatherThanClaimingSuccess() {
        var scriptLib = new emu.grasscutter.scripts.ScriptLib();
        var invalid = new org.luaj.vm2.LuaTable();
        invalid.set("config_id", 0);
        assertEquals(1, scriptLib.CreateGadget(invalid));
        invalid.set("config_id", 374004);
        assertEquals(1, scriptLib.CreateGadget(invalid));
    }

    @Test
    void nativeBlossomChestTypesUseSeparateRewardGadgets() {
        // BlossomChestExcelConfigData: SCOIN uses 70210109 and EXP uses 70210110.
        assertEquals(70210109, BlossomType.forRefresh("BLOSSOM_REFRESH_SCOIN").getRewardChestGadgetId());
        assertEquals(70210110, BlossomType.forRefresh("BLOSSOM_REFRESH_EXP").getRewardChestGadgetId());
    }

    @Test
    void luaRewardMarkerIsPartOfTheParsedSourceObject() {
        var nativeReward = JsonUtils.decode(
                "{\"config_id\":374006,\"gadget_id\":70210106,\"is_blossom_chest\":true}",
                emu.grasscutter.scripts.data.SceneGadget.class);
        var unrelated = JsonUtils.decode(
                "{\"config_id\":374004,\"gadget_id\":70360056}",
                emu.grasscutter.scripts.data.SceneGadget.class);
        assertTrue(nativeReward.is_blossom_chest);
        assertFalse(unrelated.is_blossom_chest);
        var node = new BlossomGraph.CampNode(1);
        node.blossom = new BlossomManager.Blossom(BlossomType.WEALTH, 0);
        var activity = new BlossomManager.BlossomActivity(null,
                node);
        activity.beginNative(0);
        assertEquals(2, activity.scheduleState());
        assertFalse(activity.completeFromLua(unrelated));
        assertTrue(activity.completeFromLua(nativeReward));
        assertFalse(activity.completeFromLua(nativeReward));
        assertTrue(activity.rewardReady());
        assertEquals(3, activity.scheduleState());
    }

}
