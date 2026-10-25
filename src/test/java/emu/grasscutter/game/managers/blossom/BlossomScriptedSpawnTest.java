package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

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
            int gold = BlossomNativeCamps.firstAvailableCamp(
                    List.of(start), graph, city, 20, 1, occupied, unused -> true);
            assertEquals(start, gold);
            occupied.add(gold);
            int blue = BlossomNativeCamps.firstAvailableCamp(
                    List.of(start), graph, city, 20, 3, occupied, unused -> true);
            assertEquals(start + 1, blue);
            occupied.add(blue);
        }
        assertEquals(8, occupiedByCity.size());
        occupiedByCity.values().forEach(occupied -> assertEquals(2, occupied.size()));

        // Claiming the fourth country's gold flower must not move any other country's cursor.
        int next = BlossomNativeCamps.firstAvailableCamp(
                List.of(4002), graph, 4, 20, 1, Set.of(4002), unused -> true);
        assertEquals(4003, next);
        assertEquals(Set.of(1001, 1002), occupiedByCity.get(1));
        assertEquals(Set.of(5001, 5002), occupiedByCity.get(5));
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
    void unlockingOneCountryDoesNotUnlockAnotherCountryBlossoms() {
        var mondstadt = new emu.grasscutter.data.excels.CityData();
        mondstadt.setCityId(1);
        mondstadt.setAreaIdVec(List.of(101));
        var inazuma = new emu.grasscutter.data.excels.CityData();
        inazuma.setCityId(3);
        inazuma.setAreaIdVec(List.of(301, 302));
        var refresh = JsonUtils.decode(
                "{\"id\":20,\"cityId\":3,\"refreshType\":\"BLOSSOM_REFRESH_SCOIN\","
                        + "\"refreshCondVec\":["
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER\",\"param\":[18]},"
                        + "{\"type\":\"BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY\",\"param\":[3]}]}",
                emu.grasscutter.data.excels.BlossomRefreshExcelConfigData.class);
        var cities = List.of(mondstadt, inazuma);
        assertFalse(BlossomManager.refreshEligible(refresh, 20, Set.of(101), cities));
        assertFalse(BlossomManager.refreshEligible(refresh, 17, Set.of(301), cities));
        assertTrue(BlossomManager.refreshEligible(refresh, 20, Set.of(301), cities));
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

        assertFalse(BlossomManager.refreshEligible(blue, 7, Set.of(), List.of()));
        assertTrue(BlossomManager.refreshEligible(blue, 8, Set.of(), List.of()));
        assertFalse(BlossomManager.refreshEligible(gold, 11, Set.of(), List.of()));
        assertTrue(BlossomManager.refreshEligible(gold, 12, Set.of(), List.of()));
        assertFalse(BlossomManager.refreshEligible(empty, 60, Set.of(), List.of()));
    }

    @Test
    void campGraphCannotTraverseASecondCountry() {
        var graph = Map.of(
                101, camp(101, 1, List.of(1, 3), 201),
                201, camp(201, 2, List.of(1, 3), 102),
                102, camp(102, 1, List.of(1, 3), 101));
        assertEquals(0, BlossomNativeCamps.firstAvailableCamp(
                List.of(101), graph, 1, 20, 1, Set.of(101), unused -> true));
        assertEquals(201, BlossomNativeCamps.firstAvailableCamp(
                List.of(201), graph, 2, 20, 1, Set.of(), unused -> true));
    }

    @Test
    void missingLuaInOneCountryDoesNotHideFlowersInAnother() {
        var graph = Map.of(
                101, camp(101, 1, List.of(1, 3), 102),
                102, camp(102, 1, List.of(1, 3), 101),
                301, camp(301, 3, List.of(1, 3), 302),
                302, camp(302, 3, List.of(1, 3), 301));
        assertEquals(101, BlossomNativeCamps.firstAvailableCamp(
                List.of(101), graph, 1, 20, 1, Set.of(), node -> node.getCityId() == 1));
        assertEquals(102, BlossomNativeCamps.firstAvailableCamp(
                List.of(101), graph, 1, 20, 3, Set.of(101), node -> node.getCityId() == 1));
        assertEquals(0, BlossomNativeCamps.firstAvailableCamp(
                List.of(301), graph, 3, 20, 1, Set.of(), node -> node.getCityId() == 1));
    }

    @Test
    void sharedGraphSkipsAnOccupiedCampForEitherFlowerType() {
        var a = camp(1, 1, List.of(1, 3), 2);
        var b = camp(2, 1, List.of(1, 3), 3);
        var c = camp(3, 1, List.of(1, 3), 1);
        var graph = Map.of(1, a, 2, b, 3, c);
        assertEquals(3, BlossomNativeCamps.firstAvailableCamp(
                List.of(2), graph, 1, 20, 1, Set.of(2), node -> true));
        assertEquals(3, BlossomNativeCamps.firstAvailableCamp(
                List.of(2), graph, 1, 20, 3, Set.of(2), node -> true));
        assertEquals(0, BlossomNativeCamps.firstAvailableCamp(
                List.of(2), graph, 1, 20, 1, Set.of(1, 2, 3), node -> true));
    }

    @Test
    void singleFlowerMayReturnToItsOnlyAuthoredCamp() {
        var a = camp(1, 1, List.of(1), 1);
        assertEquals(1, BlossomNativeCamps.firstAvailableCamp(
                List.of(1), Map.of(1, a), 1, 20, 1, Set.of(), node -> true));
    }

    @Test
    void missingLuaDoesNotAuthorizeUnconnectedFallbackCamps() {
        var a = camp(1, 1, List.of(1, 3), 2);
        var b = camp(2, 1, List.of(1, 3), 3);
        var c = camp(3, 1, List.of(1, 3), 1);
        var other = camp(4, 1, List.of(1, 3), 4);
        var graph = Map.of(1, a, 2, b, 3, c, 4, other);
        assertEquals(3, BlossomNativeCamps.firstAvailableCamp(
                List.of(1), graph, 1, 20, 1, Set.of(), node -> node.getId() == 3));
        assertEquals(0, BlossomNativeCamps.firstAvailableCamp(
                List.of(1), graph, 1, 20, 1, Set.of(), node -> node.getId() == 4));
    }

    @Test
    void blossomIdentityUsesOnlyItsGadgetId() {
        assertSame(BlossomType.GOLD, BlossomType.valueOf(70360056));
        assertSame(BlossomType.BLUE, BlossomType.valueOf(70360057));
        assertNull(BlossomType.valueOf(70210106));
        assertNull(BlossomType.valueOf(70220103));
    }

    @Test
    void originalMonsterProgressFinishesOnlyOnce() {
        var activity = new BlossomActivity(null);
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
        assertEquals(101001001, BlossomNativeCamps.initialId(
                List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101002003, BlossomNativeCamps.initialId(
                List.of(mondstadtRevelation, mondstadtWealth)));
        assertEquals(0, BlossomNativeCamps.initialId(List.of()));
    }

    @Test
    void incompleteGroupCannotMasqueradeAsNativeBlossomEncounter() {
        var group = new emu.grasscutter.scripts.data.SceneGroup();
        group.init_config = new emu.grasscutter.scripts.data.SceneInitConfig();
        group.init_config.suite = 1;
        var gold = new emu.grasscutter.scripts.data.SceneGadget();
        gold.gadget_id = 70360056;
        var reward = new emu.grasscutter.scripts.data.SceneGadget();
        reward.gadget_id = 70210106;
        reward.is_blossom_chest = true;
        group.gadgets = Map.of(1, gold, 2, reward);
        group.monsters = Map.of(1, new emu.grasscutter.scripts.data.SceneMonster());
        group.triggers = Map.of();
        group.suites = List.of(new emu.grasscutter.scripts.data.SceneSuite());
        assertFalse(BlossomNativeCamps.hasNativeEncounter(group, BlossomType.GOLD));
        // Adding just a worktop trigger does not supply progress or reward behavior.
        var trigger = new emu.grasscutter.scripts.data.SceneTrigger();
        trigger.setName("GROUP_LOAD");
        trigger.setEvent(emu.grasscutter.scripts.constants.EventType.EVENT_GROUP_LOAD);
        trigger.setAction("load");
        group.triggers = Map.of("GROUP_LOAD", trigger);
        group.suites.get(0).triggers = List.of("GROUP_LOAD");
        group.suites.get(0).init(group);
        assertFalse(BlossomNativeCamps.hasNativeEncounter(group, BlossomType.GOLD));
    }

    @Test
    void nativeRunIdentitySurvivesRepeatedSelectionAndChangesOnlyAfterRotation() {
        var host = new emu.grasscutter.game.player.Player() {
            @Override public void save() {}
        };
        assertEquals(0, host.getBlossomCampId(11));
        host.setBlossomCampId(11, 101001001);
        assertEquals(1, host.getBlossomIncarnation(11));
        host.setBlossomRewardReady(11);
        assertTrue(host.isBlossomRewardReady(11));
        host.setBlossomCampId(11, 101001001);
        assertEquals(1, host.getBlossomIncarnation(11));
        assertTrue(host.isBlossomRewardReady(11));
        host.setBlossomCampId(11, 101001002);
        assertEquals(2, host.getBlossomIncarnation(11));
        assertFalse(host.isBlossomRewardReady(11));
        host.setBlossomConsumed(11);
        assertTrue(host.isBlossomConsumed(11));
        var run = "123:11:2";
        assertEquals(0, host.getBlossomClaimAttempt(run));
        host.advanceBlossomClaimAttempt(run);
        assertEquals(1, host.getBlossomClaimAttempt(run));
    }

    @Test
    void luaRefreshTypesFollowAuthoredGadgetSelection() {
        assertEquals(1, BlossomManager.luaRefreshType(BlossomType.GOLD));
        assertEquals(3, BlossomManager.luaRefreshType(BlossomType.BLUE));
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
        assertEquals(70210109, BlossomType.GOLD.getRewardChestGadgetId());
        assertEquals(70210110, BlossomType.BLUE.getRewardChestGadgetId());
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
        var activity = new BlossomActivity(null);
        assertEquals(2, activity.scheduleState());
        assertFalse(activity.completeFromLua(unrelated));
        assertTrue(activity.completeFromLua(nativeReward));
        assertFalse(activity.completeFromLua(nativeReward));
        assertTrue(activity.rewardReady());
        assertEquals(3, activity.scheduleState());
    }

}
