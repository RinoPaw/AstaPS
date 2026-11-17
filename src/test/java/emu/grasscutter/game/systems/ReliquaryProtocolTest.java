package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CombineReqOuterClass.CombineReq;
import emu.grasscutter.net.proto.CombineRspOuterClass.CombineRsp;
import emu.grasscutter.net.proto.GetReliquaryOfferPurchaseInfoNotifyOuterClass.GetReliquaryOfferPurchaseInfoNotify;
import emu.grasscutter.net.proto.GetReliquaryTransferRspOuterClass.GetReliquaryTransferRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.ReliquaryLockAppendPropFilterOuterClass.ReliquaryLockAppendPropFilter;
import emu.grasscutter.net.proto.ReliquaryLockSchemeOuterClass.ReliquaryLockScheme;
import emu.grasscutter.net.proto.ReliquaryWearCustomDataOuterClass.ReliquaryWearCustomData;
import emu.grasscutter.net.proto.SaveReliquaryWearCustomDataRspOuterClass.SaveReliquaryWearCustomDataRsp;
import emu.grasscutter.net.proto.Uint32PairOuterClass.Uint32Pair;
import emu.grasscutter.server.packet.send.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.*;

final class ReliquaryProtocolTest {
    @Test
    void craftingInitializationUsesNativeEmptySnapshot() {
        var packet = new PacketCraftingAvatarDataNotify();
        assertEquals(27330, packet.getOpcode());
        assertArrayEquals(new byte[0], packet.getData());
    }

    @Test
    void combineResponseKeepsEveryRewardCategory() throws Exception {
        var req =
                CombineReq.newBuilder()
                        .setCombineId(11011)
                        .setCombineCount(7)
                        .setAvatarGuid(42)
                        .build();
        var packet =
                new PacketCombineRsp(
                        req,
                        List.of(item(201, 70)),
                        List.of(item(202, 2)),
                        List.of(item(203, 3)),
                        List.of(item(204, 4)),
                        List.of(item(205, 5)));
        var rsp = CombineRsp.parseFrom(packet.getData());
        assertEquals(7, rsp.getCombineCount());
        assertEquals(2, rsp.getResultItemList(0).getCount());
        assertEquals(70, rsp.getCostItemList(0).getCount());
        assertEquals(3, rsp.getTotalRandomItemList(0).getCount());
        assertEquals(4, rsp.getTotalReturnItemList(0).getCount());
        assertEquals(5, rsp.getTotalExtraItemList(0).getCount());
    }

    @Test
    void transmuterSnapshotSeparatesTimeAndFractionalProgress() throws Exception {
        var state = new ReliquaryState.Transmuter();
        state.ensureCycle(System.currentTimeMillis() / 1000);
        state.progress = 49.5;
        state.extractedThisCycle = 1;
        state.definedSuites.put(15041, 2);
        var packet = new PacketReliquaryOfferDataNotify(state);
        assertEquals(29798, packet.getOpcode());
        var fields = scalarFields(packet.getData());
        assertEquals(49, fields.get(3).intValue());
        assertEquals(Float.floatToRawIntBits(0.5f), fields.get(4).intValue());
        assertEquals(1, fields.get(5).intValue());
        assertEquals(state.cycleEndUnix, fields.get(6).intValue());
        assertEquals(state.scheduleId, fields.get(14).intValue());
        var decoded = GetReliquaryOfferPurchaseInfoNotify.parseFrom(packet.getData());
        assertEquals(15041, decoded.getPurchaseRecordList(0).getReliquarySetId());
        assertEquals(2, decoded.getPurchaseRecordList(0).getPurchaseCount());
    }

    @Test
    void cycleBoundaryResetsLimitsAndKeepsUnspentProgress() {
        var state = new ReliquaryState.Transmuter();
        state.ensureCycle(1785528000L);
        state.progress = 60.5;
        state.extractedThisCycle = 1;
        state.definedSuites.put(15041, 2);
        long end = state.cycleEndUnix;
        state.ensureCycle(end - 1);
        assertEquals(1, state.extractedThisCycle);
        state.ensureCycle(end);
        assertEquals(0, state.extractedThisCycle);
        assertTrue(state.definedSuites.isEmpty());
        assertEquals(60.5, state.progress);
        assertEquals(end, state.scheduleId);
    }

    @Test
    void transferCaptureUsesCorrectExperienceAndRewardTags() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        // 官服样本：旧余量 2080，加 3780，产出两份 2500，余 860。
        out.writeUInt32(4, 2080);
        out.writeMessage(8, item(105002, 2));
        out.writeUInt32(9, 3780);
        out.writeUInt32(15, 860);
        out.flush();
        var rsp = GetReliquaryTransferRsp.parseFrom(bytes.toByteArray());
        assertEquals(2080, rsp.getOldExp());
        assertEquals(3780, rsp.getAddExp());
        assertEquals(860, rsp.getRemainExp());
        assertEquals(item(105002, 2), rsp.getTransferItemList(0));
    }

    @Test
    void wearConditionsRoundTripThroughPersistentState() {
        var expected =
                ReliquaryWearCustomData.newBuilder()
                        .setAvatarId(10000002)
                        .setAvatarElementType(7)
                        .setIndex(1)
                        .setIsAll(true)
                        .addReliquarySetIdList(15041)
                        .addMainPropTypeList(28)
                        .addAppendPropTypeList(20)
                        .addMainPropTypeMap(pair(3, 28))
                        .addMainPropTypeMap(pair(4, 46))
                        .build();
        var state = new ReliquaryState();
        state.wear.put(ReliquaryState.wearKey(10000002, 7, 1), ReliquaryState.Wear.from(expected));
        var restored = roundTrip(state);
        assertEquals(expected, restored.wear.values().iterator().next().proto());
        assertEquals(29607, PacketOpcodes.SaveReliquaryWearCustomDataRsp);
        assertEquals(0, SaveReliquaryWearCustomDataRsp.getDefaultInstance().getRetcode());
    }

    @Test
    void deletingFirstWearSlotMovesSecondWithoutTouchingAnotherAvatar() {
        var state = new ReliquaryState();
        var second =
                ReliquaryState.Wear.from(
                        ReliquaryWearCustomData.newBuilder()
                                .setAvatarId(10000002)
                                .setIndex(1)
                                .addAppendPropTypeList(20)
                                .build());
        var other =
                ReliquaryState.Wear.from(
                        ReliquaryWearCustomData.newBuilder()
                                .setAvatarId(10000003)
                                .setIndex(1)
                                .build());
        state.wear.put(ReliquaryState.wearKey(second.avatarId, 0, 1), second);
        state.wear.put(ReliquaryState.wearKey(other.avatarId, 0, 1), other);
        state.deleteWear(second.avatarId, 0, 0);
        assertEquals(0, state.wear.get(ReliquaryState.wearKey(second.avatarId, 0, 0)).index);
        assertEquals(List.of(20), second.append);
        assertSame(other, state.wear.get(ReliquaryState.wearKey(other.avatarId, 0, 1)));
    }

    @Test
    void filtersPreserveMultipleValuesForTheSameKeyAndEmptyClears() {
        var state = new ReliquaryState();
        state.replaceFilters(List.of(pair(1, 15041), pair(1, 15042), pair(3, 20), pair(1, 15041)));
        var restored = roundTrip(state);
        assertEquals(List.of(15041, 15042), restored.filters.get(1));
        assertEquals(List.of(20), restored.filters.get(3));
        restored.replaceFilters(List.of());
        assertTrue(restored.filters.isEmpty());
    }

    @Test
    void customLockRulesKeepRequiredOptionalAndMinimumCounts() {
        var proto =
                ReliquaryLockScheme.newBuilder()
                        .setSchemeId(2)
                        .setVersion(3)
                        .setIsDefault(true)
                        .addAppendPropFilterList(
                                ReliquaryLockAppendPropFilter.newBuilder()
                                        .setEquipType(4)
                                        .addRequiredAppendPropList(20)
                                        .addOptionalAppendPropList(22)
                                        .setMinCount(2))
                        .build();
        var state = new ReliquaryState();
        state.schemes.put(15041, List.of(ReliquaryState.LockScheme.from(proto)));
        var restored = roundTrip(state).schemes.get(15041).get(0).proto(false);
        assertFalse(restored.getIsDefault());
        assertEquals(proto.toBuilder().setIsDefault(false).build(), restored);
        assertEquals(11, ReliquaryLockScheme.IS_DEFAULT_FIELD_NUMBER);
    }

    @Test
    void bundledDefaultRulesAreNotAppliedToEverySet() throws Exception {
        try (var stream = getClass().getResourceAsStream("/ReliquaryLockSchemes.json")) {
            assertNotNull(stream);
            var object =
                    com.google.gson.JsonParser.parseReader(
                                    new java.io.InputStreamReader(
                                            stream, java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            assertEquals(Set.of("15041"), object.keySet());
            assertEquals(3, object.getAsJsonArray("15041").size());
        }
    }

    @Test
    void pendingReforgeKeepsStableItemIdentityAcrossSerialization() {
        var state = new ReliquaryState();
        state.pending = new ReliquaryState.Pending();
        state.pending.itemKey = "0123456789abcdef01234567";
        state.pending.oldAppend.addAll(List.of(1, 2, 3, 4));
        state.pending.newAppend.addAll(List.of(5, 6, 7, 8));
        state.pending.chosen.addAll(List.of(20, 22));
        var restored = roundTrip(state);
        assertEquals(state.pending.itemKey, restored.pending.itemKey);
        assertEquals(state.pending.newAppend, restored.pending.newAppend);
        assertEquals(state.pending.chosen, restored.pending.chosen);
    }

    private static ReliquaryState roundTrip(ReliquaryState value) {
        var gson = new Gson();
        return gson.fromJson(gson.toJson(value), ReliquaryState.class);
    }

    private static ItemParam item(int id, int count) {
        return ItemParam.newBuilder().setItemId(id).setCount(count).build();
    }

    private static Uint32Pair pair(int key, int value) {
        return Uint32Pair.newBuilder().setKey(key).setValue(value).build();
    }

    private static Map<Integer, Long> scalarFields(byte[] bytes) throws Exception {
        var input = CodedInputStream.newInstance(bytes);
        var fields = new HashMap<Integer, Long>();
        for (int tag; (tag = input.readTag()) != 0; ) {
            if ((tag & 7) == 0) fields.put(tag >>> 3, input.readUInt64());
            else if ((tag & 7) == 5)
                fields.put(tag >>> 3, Integer.toUnsignedLong(input.readFixed32()));
            else input.skipField(tag);
        }
        return fields;
    }
}
