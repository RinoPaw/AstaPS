package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.net.proto.QuestListUpdateNotifyOuterClass.QuestListUpdateNotify;
import emu.grasscutter.net.proto.QuestOuterClass.Quest;
import org.junit.jupiter.api.Test;

class AmberGuideHandoffOrderTest {
    private static Quest quest(int id, int parent, int state) {
        return Quest.newBuilder()
                .setQuestId(id)
                .setParentQuestId(parent)
                .setState(state)
                .build();
    }

    @Test
    void reportsCompleted35601AndVisible35603WithActualParentAndState() throws Exception {
        var packet = QuestListUpdateNotify.newBuilder()
                .addQuestList(quest(35601, 356, 3))
                .addQuestList(quest(99999, 999, 2))
                .addQuestList(quest(35603, 356, 2))
                .build();
        assertEquals(
                "35601(parent=356,state=3), 35603(parent=356,state=2)",
                GameSession.summarizeAmberHandoffQuestEntries(packet.toByteArray()));
    }

    @Test
    void preservesUnexpectedParentRatherThanGuessingExpectedQuestGuideTarget() throws Exception {
        var packet = QuestListUpdateNotify.newBuilder()
                .addQuestList(quest(35603, 999, 2))
                .addQuestList(quest(35602, 356, 2))
                .build();
        assertEquals(
                "35603(parent=999,state=2), 35602(parent=356,state=2)",
                GameSession.summarizeAmberHandoffQuestEntries(packet.toByteArray()));
    }

    @Test
    void emptyOrUnrelatedQuestListsAreNotFabricated() throws Exception {
        assertEquals("(none)", GameSession.summarizeAmberHandoffQuestEntries(
                QuestListUpdateNotify.getDefaultInstance().toByteArray()));
        assertEquals("(none)", GameSession.summarizeAmberHandoffQuestEntries(
                QuestListUpdateNotify.newBuilder().addQuestList(quest(42, 4, 2))
                        .build().toByteArray()));
    }

    @Test
    void malformedWirePayloadFailsWithoutInventingQuestState() {
        assertThrows(
                InvalidProtocolBufferException.class,
                () -> GameSession.summarizeAmberHandoffQuestEntries(
                        new byte[] {0x0A, 0x05, 0x08}));
    }
}
