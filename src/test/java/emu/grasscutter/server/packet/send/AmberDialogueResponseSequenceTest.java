package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.NpcTalkRspOuterClass.NpcTalkRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.QuestDestroyNpcRspOuterClass.QuestDestroyNpcRsp;
import org.junit.jupiter.api.Test;

class AmberDialogueResponseSequenceTest {
    @Test
    void npcTalkReplyUsesClientSequenceAndPreservesPayload() throws Exception {
        var reply = new PacketNpcTalkRsp(123, 35601, 456, 19876);
        assertEquals(PacketOpcodes.NpcTalkRsp, reply.getOpcode());
        assertFalse(reply.shouldBuildHeader());
        assertEquals(19876, PacketHead.parseFrom(reply.getHeader()).getClientSequenceId());
        var body = NpcTalkRsp.parseFrom(reply.getData());
        assertEquals(123, body.getNpcEntityId());
        assertEquals(35601, body.getCurTalkId());
        assertEquals(456, body.getEntityId());
    }

    @Test
    void questDestroyReplyUsesClientSequenceAndPreservesPayload() throws Exception {
        var reply = new PacketQuestDestroyNpcRsp(123, 356, 0, 19877);
        assertEquals(PacketOpcodes.QuestDestroyNpcRsp, reply.getOpcode());
        assertFalse(reply.shouldBuildHeader());
        assertEquals(19877, PacketHead.parseFrom(reply.getHeader()).getClientSequenceId());
        var body = QuestDestroyNpcRsp.parseFrom(reply.getData());
        assertEquals(123, body.getNpcId());
        assertEquals(356, body.getParentQuestId());
        assertEquals(0, body.getRetcode());
    }

    @Test
    void concurrentQuestRequestsReceiveDistinctOriginalSequences() throws Exception {
        var first = new PacketQuestDestroyNpcRsp(1, 356, 0, 112);
        var second = new PacketQuestDestroyNpcRsp(2, 356, 0, 113);
        assertEquals(112, PacketHead.parseFrom(first.getHeader()).getClientSequenceId());
        assertEquals(113, PacketHead.parseFrom(second.getHeader()).getClientSequenceId());
    }
}
