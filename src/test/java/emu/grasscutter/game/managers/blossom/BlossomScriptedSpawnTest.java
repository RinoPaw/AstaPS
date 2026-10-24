package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BlossomScriptedSpawnTest {
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
                1, BlossomType.GOLD, List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101002003, BlossomNativeCamps.initialId(
                1, BlossomType.BLUE, List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101001001, BlossomNativeCamps.initialId(
                2, BlossomType.GOLD, List.of(mondstadtWealth, mondstadtRevelation)));
        assertEquals(101002003, BlossomNativeCamps.initialId(
                2, BlossomType.BLUE, List.of(mondstadtWealth, mondstadtRevelation)));
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
    void nativeCampGraphFollowsLinksOnceAndStopsAtCycle() {
        var one = JsonUtils.decode(
                "{\"id\":1,\"nextCampIdVec\":[2]}", emu.grasscutter.data.excels.BlossomCampData.class);
        var two = JsonUtils.decode(
                "{\"id\":2,\"nextCampIdVec\":[3]}", emu.grasscutter.data.excels.BlossomCampData.class);
        var three = JsonUtils.decode(
                "{\"id\":3,\"nextCampIdVec\":[1,999]}", emu.grasscutter.data.excels.BlossomCampData.class);
        var map = java.util.Map.of(1, one, 2, two, 3, three);
        assertEquals(List.of(2, 3, 1), BlossomNativeCamps.graphOrder(List.of(2), map));
        assertEquals(List.of(3, 1, 2), BlossomNativeCamps.graphOrder(List.of(3), map));
        assertEquals(List.of(), BlossomNativeCamps.graphOrder(List.of(999), map));
    }

    @Test
    void encounterCompletesFromKillScoreEvenWithLingeringMonsterReferences() {
        assertFalse(BlossomActivity.encounterComplete(8, 4, 0, 4));
        assertFalse(BlossomActivity.encounterComplete(8, 8, 1, 7));
        assertTrue(BlossomActivity.encounterComplete(8, 8, 2, 8));
        assertTrue(BlossomActivity.encounterComplete(8, 8, 0, 7));
    }

    @Test
    void global71OriginalClient20And40ResinPacketsSelectSeparateAmounts() throws Exception {
        // Captured GadgetInteractReq FINISH packets from the Global 7.1 client.
        var req20 = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .parseFrom(java.util.HexFormat.of().parseHex("20bea4bd21380158a98980047001"));
        var req40 = emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq
                .parseFrom(java.util.HexFormat.of().parseHex("20bea4bd21380158f88880047002"));
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
    void nativeBlossomChestTypesUseSeparateRewardGadgets() {
        // BlossomChestExcelConfigData: SCOIN uses 70210109 and EXP uses 70210110.
        assertEquals(70210109, BlossomType.GOLD.getRewardChestGadgetId());
        assertEquals(70210110, BlossomType.BLUE.getRewardChestGadgetId());
        assertEquals(1, BlossomType.GOLD.getBlossomChestId());
        assertEquals(2, BlossomType.BLUE.getBlossomChestId());
    }

    @Test
    void progressionUnlocksEachTypeAtItsExpectedAdventureRank() {
        assertFalse(BlossomManager.unlockedAtRank(BlossomType.BLUE, 7));
        assertTrue(BlossomManager.unlockedAtRank(BlossomType.BLUE, 8));
        assertFalse(BlossomManager.unlockedAtRank(BlossomType.GOLD, 11));
        assertTrue(BlossomManager.unlockedAtRank(BlossomType.GOLD, 12));
    }

}
