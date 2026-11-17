package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.CodedInputStream;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ActivityTakeWatcherRewardReqOuterClass.ActivityTakeWatcherRewardReq;
import emu.grasscutter.net.proto.AvatarPromoteReqOuterClass.AvatarPromoteReq;
import emu.grasscutter.net.proto.AvatarPromoteReqParser;
import emu.grasscutter.net.proto.CancelCoopTaskReqOuterClass.CancelCoopTaskReq;
import emu.grasscutter.net.proto.DelBackupAvatarTeamReqOuterClass.DelBackupAvatarTeamReq;
import emu.grasscutter.net.proto.DelMailReqOuterClass.DelMailReq;
import emu.grasscutter.net.proto.GetGachaInfoRspOuterClass.GetGachaInfoRsp;
import emu.grasscutter.net.proto.GetMailItemReqOuterClass.GetMailItemReq;
import emu.grasscutter.net.proto.GetPushTipsRewardReqOuterClass.GetPushTipsRewardReq;
import emu.grasscutter.net.proto.GetPushTipsRewardRspOuterClass.GetPushTipsRewardRsp;
import emu.grasscutter.net.proto.HomeChangeBgmReqOuterClass.HomeChangeBgmReq;
import emu.grasscutter.net.proto.HomeChangeBgmRspOuterClass.HomeChangeBgmRsp;
import emu.grasscutter.net.proto.HomeChangeEditModeReqOuterClass.HomeChangeEditModeReq;
import emu.grasscutter.net.proto.HomeChangeModuleReqOuterClass.HomeChangeModuleReq;
import emu.grasscutter.net.proto.HomeChooseModuleReqOuterClass.HomeChooseModuleReq;
import emu.grasscutter.net.proto.HomeResourceOuterClass.HomeResource;
import emu.grasscutter.net.proto.ReadMailNotifyOuterClass.ReadMailNotify;
import emu.grasscutter.net.proto.ReceivedTrialAvatarActivityRewardReqOuterClass.ReceivedTrialAvatarActivityRewardReq;
import emu.grasscutter.net.proto.SetNameCardReqOuterClass.SetNameCardReq;
import emu.grasscutter.net.proto.SetPlayerSignatureReqOuterClass.SetPlayerSignatureReq;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import emu.grasscutter.net.proto.TowerBuffSelectReqOuterClass.TowerBuffSelectReq;
import emu.grasscutter.net.proto.TowerBuffSelectRspOuterClass.TowerBuffSelectRsp;
import emu.grasscutter.net.proto.UnlockPersonalLineReqOuterClass.UnlockPersonalLineReq;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

final class Verified72BindingsTest {
    @Test
    void ascensionUsesTheNativeRequestGuid() throws Exception {
        // Native 7.2 request: uint64 guid = 3, including GUID bits above 32 bits.
        var bytes = HexFormat.of().parseHex("188180808010");
        assertEquals(6528, PacketOpcodes.AvatarPromoteReq);
        assertEquals(0x100000001L, AvatarPromoteReq.parseFrom(bytes).getGuid());
        assertEquals(0x100000001L, AvatarPromoteReqParser.parseGuid(bytes));
        assertArrayEquals(
                bytes, AvatarPromoteReq.newBuilder().setGuid(0x100000001L).build().toByteArray());
        assertEquals(0, AvatarPromoteReqParser.parseGuid(new byte[] {0x18, (byte) 0x80}));
    }

    @Test
    void homeResourceKeepsStoredValueSeparateFromCapacity() throws Exception {
        var nativeData = HexFormat.of().parseHex("087b38e807");
        var resource = HomeResource.parseFrom(nativeData);
        assertEquals(123, resource.getStoreValue());
        assertEquals(1000, resource.getStoreLimit());

        var output = HomeResource.newBuilder().setStoreValue(123).setStoreLimit(1000).build();
        var input = CodedInputStream.newInstance(output.toByteArray());
        var fields = new HashMap<Integer, Integer>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            fields.put(tag >>> 3, input.readUInt32());
        }
        assertEquals(Map.of(1, 123, 7, 1000), fields);
    }

    @Test
    void gachaFlagsAreReadFromTheCurrentClientSlots() throws Exception {
        var bytes = HexFormat.of().parseHex("08017001");
        var response = GetGachaInfoRsp.parseFrom(bytes);
        assertTrue(response.getBKMCHIJHJJE());
        assertTrue(response.getLHBELFNPPNK());
        assertArrayEquals(bytes, response.toByteArray());
    }

    @Test
    void furnitureOutputDoesNotBecomeReturnedMaterials() throws Exception {
        var bytes = HexFormat.of().parseHex("0a0508ca0110013a0508cb011002");
        var response = TakeFurnitureMakeRsp.parseFrom(bytes);
        assertEquals(1, response.getOutputItemListCount());
        assertEquals(202, response.getOutputItemList(0).getItemId());
        assertEquals(1, response.getOutputItemList(0).getCount());
        assertEquals(1, response.getReturnItemListCount());
        assertEquals(203, response.getReturnItemList(0).getItemId());
        assertEquals(2, response.getReturnItemList(0).getCount());
        assertArrayEquals(bytes, response.toByteArray());
    }

    @Test
    void profileRequestsUseTheNativeCardAndSignatureFields() throws Exception {
        assertEquals(3230, PacketOpcodes.SetNameCardReq);
        var card = SetNameCardReq.parseFrom(HexFormat.of().parseHex("58ffffffff0f"));
        assertEquals(0xffffffffL, Integer.toUnsignedLong(card.getNameCardId()));

        assertEquals(4867, PacketOpcodes.SetPlayerSignatureReq);
        var bytes = HexFormat.of().parseHex("4a06e7adbee5908d");
        var signature = SetPlayerSignatureReq.parseFrom(bytes);
        assertEquals("签名", signature.getSignature());
        assertArrayEquals(bytes, signature.toByteArray());
    }

    @Test
    void homeMusicRequestAndReplyUseTheCurrentClientBindings() throws Exception {
        assertEquals(28560, PacketOpcodes.HomeChangeBgmReq);
        assertEquals(25773, PacketOpcodes.HomeChangeBgmRsp);
        var request = HomeChangeBgmReq.parseFrom(HexFormat.of().parseHex("20e907"));
        assertEquals(1001, request.getBgmId());

        var bytes = HexFormat.of().parseHex("688401");
        assertEquals(132, HomeChangeBgmRsp.parseFrom(bytes).getRetcode());
        assertArrayEquals(
                bytes, HomeChangeBgmRsp.newBuilder().setRetcode(132).build().toByteArray());
    }

    @Test
    void teamDeletionUsesTheCurrentIdField() throws Exception {
        assertEquals(2864, PacketOpcodes.DelBackupAvatarTeamReq);
        var request = DelBackupAvatarTeamReq.parseFrom(HexFormat.of().parseHex("207b2809"));
        assertEquals(9, request.getBackupAvatarTeamId());
        assertEquals(812, PacketOpcodes.GetAllUnlockNameCardReq);
    }

    @Test
    void mailOperationsUseTheirOwnIdLists() throws Exception {
        assertEquals(29775, PacketOpcodes.GetMailItemReq);
        var claimBytes = HexFormat.of().parseHex("7a0601ffffffff0f");
        var claim = GetMailItemReq.parseFrom(claimBytes);
        assertEquals(1, claim.getMailIdList(0));
        assertEquals(0xffffffffL, Integer.toUnsignedLong(claim.getMailIdList(1)));
        assertArrayEquals(claimBytes, claim.toByteArray());

        assertEquals(24171, PacketOpcodes.DelMailReq);
        var deletion = DelMailReq.parseFrom(HexFormat.of().parseHex("3a03010207"));
        assertEquals(java.util.List.of(1, 2, 7), deletion.getMailIdListList());

        assertEquals(6678, PacketOpcodes.ReadMailNotify);
        var read = ReadMailNotify.parseFrom(HexFormat.of().parseHex("3202092a"));
        assertEquals(java.util.List.of(9, 42), read.getMailIdListList());
    }

    @Test
    void emptyRequestsHaveDistinctCurrentBindings() {
        assertEquals(21907, PacketOpcodes.GetProfilePictureDataReq);
        assertEquals(21795, PacketOpcodes.GetPlayerAskFriendListReq);
        assertEquals(1684, PacketOpcodes.GetPlayerFriendListReq);
        assertEquals(22361, PacketOpcodes.GetOnlinePlayerListReq);
        assertEquals(29401, PacketOpcodes.GetCityReputationMapInfoReq);
        assertEquals(20856, PacketOpcodes.GetMapMarkTipsReq);
        assertEquals(28289, PacketOpcodes.BackMyWorldReq);
        assertEquals(20545, PacketOpcodes.BuyResinReq);

        var tips = new emu.grasscutter.server.packet.send.PacketGetMapMarkTipsRsp();
        assertEquals(25684, tips.getOpcode());
        assertArrayEquals(new byte[0], tips.getData());
    }

    @Test
    void cooperativeTaskCancellationUsesTheCurrentChapterField() throws Exception {
        assertEquals(26546, PacketOpcodes.CancelCoopTaskReq);
        var bytes = HexFormat.of().parseHex("20a09c01");
        assertEquals(20000, CancelCoopTaskReq.parseFrom(bytes).getChapterId());
        assertArrayEquals(
                bytes, CancelCoopTaskReq.newBuilder().setChapterId(20000).build().toByteArray());
    }

    @Test
    void trialRewardUsesTheCurrentTrialIndexField() throws Exception {
        assertEquals(20742, PacketOpcodes.ReceivedTrialAvatarActivityRewardReq);
        var request =
                ReceivedTrialAvatarActivityRewardReq.parseFrom(
                        HexFormat.of().parseHex("0807109f01"));
        assertEquals(159, request.getTrialAvatarIndexId());
        assertArrayEquals(
                HexFormat.of().parseHex("109f01"),
                ReceivedTrialAvatarActivityRewardReq.newBuilder()
                        .setTrialAvatarIndexId(159)
                        .build()
                        .toByteArray());
    }

    @Test
    void homeRealmSwitchUsesTheCurrentTargetField() throws Exception {
        assertEquals(21043, PacketOpcodes.HomeChangeModuleReq);
        var request = HomeChangeModuleReq.parseFrom(HexFormat.of().parseHex("70075004"));
        assertEquals(4, request.getTargetModuleId());
        assertArrayEquals(
                HexFormat.of().parseHex("5004"),
                HomeChangeModuleReq.newBuilder().setTargetModuleId(4).build().toByteArray());
    }

    @Test
    void activityWatcherClaimKeepsBothIdsInTheirCurrentFields() throws Exception {
        assertEquals(1504, PacketOpcodes.ActivityTakeWatcherRewardReq);
        var bytes = HexFormat.of().parseHex("30e907408a27");
        var request = ActivityTakeWatcherRewardReq.parseFrom(bytes);
        assertEquals(5002, request.getActivityId());
        assertEquals(1001, request.getWatcherId());
        assertArrayEquals(
                bytes,
                ActivityTakeWatcherRewardReq.newBuilder()
                        .setActivityId(5002)
                        .setWatcherId(1001)
                        .build()
                        .toByteArray());
    }

    @Test
    void storyUnlockAndHomeEditModeUseTheirCurrentRequests() throws Exception {
        assertEquals(28530, PacketOpcodes.UnlockPersonalLineReq);
        var story = UnlockPersonalLineReq.parseFrom(HexFormat.of().parseHex("68a09c01"));
        assertEquals(20000, story.getPersonalLineId());

        assertEquals(29979, PacketOpcodes.HomeChangeEditModeReq);
        var bytes = HexFormat.of().parseHex("6001");
        assertTrue(HomeChangeEditModeReq.parseFrom(bytes).getIsEnterEditMode());
        assertArrayEquals(
                bytes,
                HomeChangeEditModeReq.newBuilder().setIsEnterEditMode(true).build().toByteArray());
    }

    @Test
    void towerBuffSelectionAndFirstRealmChoiceUseCurrentFields() throws Exception {
        assertEquals(705, PacketOpcodes.TowerBuffSelectReq);
        var selection = TowerBuffSelectReq.parseFrom(HexFormat.of().parseHex("60e90710d20f"));
        assertEquals(2002, selection.getTowerBuffId());

        assertEquals(8282, PacketOpcodes.TowerBuffSelectRsp);
        var reply = TowerBuffSelectRsp.parseFrom(HexFormat.of().parseHex("20d20960d20f"));
        assertEquals(2002, reply.getTowerBuffId());
        assertEquals(0, reply.getRetcode());

        assertEquals(8792, PacketOpcodes.HomeChooseModuleReq);
        var choice = HomeChooseModuleReq.parseFrom(HexFormat.of().parseHex("68071804"));
        assertEquals(4, choice.getModuleId());
    }

    @Test
    void pushTipRewardsReplyWithTheCurrentIdListAndRetcode() throws Exception {
        assertEquals(24813, PacketOpcodes.GetPushTipsRewardReq);
        var request = GetPushTipsRewardReq.parseFrom(HexFormat.of().parseHex("12070129ffffffff0f"));
        assertEquals(java.util.List.of(1, 41, -1), request.getPushTipsIdListList());

        var packet =
                new emu.grasscutter.server.packet.send.PacketGetPushTipsRewardRsp(
                        132, request.getPushTipsIdListList());
        assertEquals(9626, packet.getOpcode());
        assertArrayEquals(HexFormat.of().parseHex("3a070129ffffffff0f588401"), packet.getData());
        var reply = GetPushTipsRewardRsp.parseFrom(packet.getData());
        assertEquals(request.getPushTipsIdListList(), reply.getPushTipsIdListList());
        assertEquals(132, reply.getRetcode());
    }

    @Test
    void multiplayerRequestsAndHostRoutingUseCurrentFields() throws Exception {
        assertEquals(9526, PacketOpcodes.PlayerApplyEnterMpReq);
        var request =
                emu.grasscutter.net.proto.PlayerApplyEnterMpReqOuterClass.PlayerApplyEnterMpReq
                        .parseFrom(HexFormat.of().parseHex("607b"));
        assertEquals(123, request.getTargetUid());
        var notification =
                emu.grasscutter.net.proto.PlayerApplyEnterMpNotifyOuterClass
                        .PlayerApplyEnterMpNotify.parseFrom(HexFormat.of().parseHex("08076809"));
        assertEquals(7, notification.getSrcThreadIndex());
        assertEquals(9, notification.getSrcAppId());
        assertEquals(24164, PacketOpcodes.PlayerApplyEnterHomeNotify);
        var home =
                emu.grasscutter.net.proto.PlayerApplyEnterHomeNotifyOuterClass
                        .PlayerApplyEnterHomeNotify.parseFrom(
                        HexFormat.of().parseHex("48097202087b"));
        assertEquals(9, home.getSrcAppId());
        assertEquals(123, home.getSrcPlayerInfo().getUid());
        assertEquals(21380, PacketOpcodes.SceneKickPlayerReq);
    }

    @Test
    void towerAndHomeCompletionRepliesHaveCurrentRoutes() throws Exception {
        assertEquals(3450, PacketOpcodes.TowerTeamSelectRsp);
        assertEquals(2048, PacketOpcodes.TowerSurrenderRsp);
        assertEquals(
                132,
                emu.grasscutter.net.proto.TowerTeamSelectRspOuterClass.TowerTeamSelectRsp.parseFrom(
                                HexFormat.of().parseHex("288401"))
                        .getRetcode());
        assertEquals(
                132,
                emu.grasscutter.net.proto.TowerSurrenderRspOuterClass.TowerSurrenderRsp.parseFrom(
                                HexFormat.of().parseHex("408401"))
                        .getRetcode());
        assertEquals(7167, PacketOpcodes.HomeEnterEditModeFinishReq);
        var packet = new emu.grasscutter.server.packet.send.PacketHomeEnterEditModeFinishRsp();
        assertEquals(7570, packet.getOpcode());
        assertEquals(22143, PacketOpcodes.DelTeamEntityNotify);
        var removed =
                emu.grasscutter.net.proto.DelTeamEntityNotifyOuterClass.DelTeamEntityNotify
                        .parseFrom(HexFormat.of().parseHex("420207087803"));
        assertEquals(java.util.List.of(7, 8), removed.getDelEntityIdListList());
        assertEquals(3, removed.getSceneId());
    }
}
