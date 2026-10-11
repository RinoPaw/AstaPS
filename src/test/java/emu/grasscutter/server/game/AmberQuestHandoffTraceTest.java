package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.NpcTalkRspOuterClass.NpcTalkRsp;
import emu.grasscutter.net.proto.QuestListUpdateNotifyOuterClass.QuestListUpdateNotify;
import emu.grasscutter.net.proto.QuestOuterClass.Quest;
import org.junit.jupiter.api.Test;

class AmberQuestHandoffTraceTest {
    private static Quest quest(int id, int parent, int state) {
        return Quest.newBuilder()
                .setQuestId(id)
                .setParentQuestId(parent)
                .setState(state)
                .build();
    }

    @Test
    void reportsActualClientVisible35603AndParentState() {
        var notify = QuestListUpdateNotify.newBuilder()
                .addQuestList(quest(35601, 356, 3))
                .addQuestList(quest(35603, 356, 2))
                .addQuestList(quest(30701, 307, 2))
                .build();
        assertEquals(
                "35601(parent=356,state=3), 35603(parent=356,state=2)",
                AmberQuestHandoffTrace.formatQuestEntries(notify));
    }

    @Test
    void doesNotInventAnEntryWhenTheOutgoingQuestPacketIsEmptyOrUnrelated() {
        assertEquals("(none)", AmberQuestHandoffTrace.formatQuestEntries(
                QuestListUpdateNotify.getDefaultInstance()));
        assertEquals("(none)", AmberQuestHandoffTrace.formatQuestEntries(
                QuestListUpdateNotify.newBuilder()
                        .addQuestList(quest(42, 4, 2)).build()));
    }

    @Test
    void keepsTheWireParentIdEvenIfItIsWrong() {
        var notify = QuestListUpdateNotify.newBuilder()
                .addQuestList(quest(35603, 9999, 2)).build();
        assertEquals("35603(parent=9999,state=2)",
                AmberQuestHandoffTrace.formatQuestEntries(notify));
    }

    @Test
    void npcTalkResponseIdentityIsEncodedAs35601() throws Exception {
        byte[] body = NpcTalkRsp.newBuilder().setCurTalkId(35601).build().toByteArray();
        assertEquals(35601, NpcTalkRsp.parseFrom(body).getCurTalkId());
    }

    @Test
    void decodesKnownScalarFieldsButNeverAssignsMeaningToUnknownOpcodes() {
        assertEquals("1", AmberQuestHandoffTrace.readOptionalVarint(
                new byte[] {0x38, 0x01}, 7));
        assertEquals("0", AmberQuestHandoffTrace.readOptionalVarint(
                new byte[] {0x38, 0x00}, 7));
        assertEquals("42", AmberQuestHandoffTrace.readOptionalVarint(
                new byte[] {0x78, 0x2a}, 15));
        assertEquals("absent", AmberQuestHandoffTrace.readOptionalVarint(
                new byte[0], 7));
        assertEquals("absent", AmberQuestHandoffTrace.readOptionalVarint(
                new byte[] {0x78, 0x2a}, 7));
    }

    @Test
    void refusesToTreatTruncatedOrMissingPayloadAsObservedFalse() {
        assertEquals("malformed", AmberQuestHandoffTrace.readOptionalVarint(
                new byte[] {0x38, (byte) 0x80}, 7));
        assertEquals("malformed", AmberQuestHandoffTrace.readOptionalVarint(null, 7));
    }
}
