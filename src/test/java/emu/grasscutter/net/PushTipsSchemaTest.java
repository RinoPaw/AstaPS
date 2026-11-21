package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetPushTipsRewardReqOuterClass.GetPushTipsRewardReq;
import emu.grasscutter.net.proto.GetPushTipsRewardRspOuterClass.GetPushTipsRewardRsp;
import emu.grasscutter.net.proto.PushTipsAllDataNotifyOuterClass.PushTipsAllDataNotify;
import emu.grasscutter.net.proto.PushTipsChangeNotifyOuterClass.PushTipsChangeNotify;
import emu.grasscutter.net.proto.PushTipsDataOuterClass.PushTipsData;
import emu.grasscutter.net.proto.PushTipsReadFinishReqOuterClass.PushTipsReadFinishReq;
import emu.grasscutter.net.proto.PushTipsReadFinishRspOuterClass.PushTipsReadFinishRsp;
import emu.grasscutter.net.proto.SetCodexPushtipsReadReqOuterClass.SetCodexPushtipsReadReq;
import emu.grasscutter.net.proto.SetCodexPushtipsReadRspOuterClass.SetCodexPushtipsReadRsp;
import org.junit.jupiter.api.Test;

/** 7.2.0 field numbers and CmdIds of the push tips messages. */
final class PushTipsSchemaTest {
    @Test
    void commandIds() {
        assertEquals(26129, PacketOpcodes.PushTipsAllDataNotify);
        assertEquals(22373, PacketOpcodes.PushTipsChangeNotify);
        assertEquals(23199, PacketOpcodes.PushTipsReadFinishReq);
        assertEquals(24454, PacketOpcodes.PushTipsReadFinishRsp);
        assertEquals(24813, PacketOpcodes.GetPushTipsRewardReq);
        assertEquals(9626, PacketOpcodes.GetPushTipsRewardRsp);
        assertEquals(594, PacketOpcodes.SetCodexPushtipsReadReq);
        assertEquals(3881, PacketOpcodes.SetCodexPushtipsReadRsp);
    }

    @Test
    void tipDataAndAllDataNotify() {
        assertEquals(11, PushTipsData.STATE_FIELD_NUMBER);
        assertEquals(10, PushTipsData.PUSH_TIPS_ID_FIELD_NUMBER);
        assertEquals(3, PushTipsAllDataNotify.PUSH_TIPS_LIST_FIELD_NUMBER);
    }

    @Test
    void changeNotify() {
        assertEquals(5, PushTipsChangeNotify.PUSH_TIPS_LIST_FIELD_NUMBER);
        var descriptor = PushTipsChangeNotify.getDescriptor();
        assertEquals(10, descriptor.findFieldByName("_repeated_push_tips_id_list").getNumber());
    }

    @Test
    void readFinish() {
        assertEquals(3, PushTipsReadFinishReq.PUSH_TIPS_ID_FIELD_NUMBER);
        assertEquals(11, PushTipsReadFinishRsp.RETCODE_FIELD_NUMBER);
        assertEquals(8, PushTipsReadFinishRsp.PUSH_TIPS_ID_FIELD_NUMBER);
    }

    @Test
    void reward() {
        assertEquals(2, GetPushTipsRewardReq.PUSH_TIPS_ID_LIST_FIELD_NUMBER);
        assertEquals(7, GetPushTipsRewardRsp.PUSH_TIPS_ID_LIST_FIELD_NUMBER);
        assertEquals(11, GetPushTipsRewardRsp.RETCODE_FIELD_NUMBER);
    }

    @Test
    void codexRead() {
        assertEquals(5, SetCodexPushtipsReadReq.CODEX_ID_FIELD_NUMBER);
        assertEquals(14, SetCodexPushtipsReadReq.TYPE_ID_FIELD_NUMBER);
        assertEquals(3, SetCodexPushtipsReadRsp.TYPE_ID_FIELD_NUMBER);
        assertEquals(1, SetCodexPushtipsReadRsp.CODEX_ID_FIELD_NUMBER);
        assertEquals(8, SetCodexPushtipsReadRsp.RETCODE_FIELD_NUMBER);
    }
}
