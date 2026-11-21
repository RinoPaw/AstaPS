package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DelMailReqOuterClass.DelMailReq;
import emu.grasscutter.net.proto.GetAllMailNotifyOuterClass.GetAllMailNotify;
import emu.grasscutter.net.proto.GetMailItemReqOuterClass.GetMailItemReq;
import emu.grasscutter.net.proto.ReadMailNotifyOuterClass.ReadMailNotify;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** The 7.2 mailbox requests, without which the mailbox never finishes loading. */
final class MailProtocolTest {
    @Test
    void mailboxRequestsAreMapped() {
        assertEquals(8992, PacketOpcodes.GetAllMailNotify);
        assertEquals(6678, PacketOpcodes.ReadMailNotify);
    }

    @Test
    void requestFieldsMatchSevenTwo() throws Exception {
        // is_collected = true on field 1; mail ids 3 and 5 packed on field 6.
        assertTrue(GetAllMailNotify.parseFrom(HexFormat.of().parseHex("0801")).getIsCollected());
        var read = ReadMailNotify.parseFrom(HexFormat.of().parseHex("32020305"));
        assertEquals(java.util.List.of(3, 5), read.getMailIdListList());
    }

    @Test
    void claimAndDeleteReadTheirMailIds() throws Exception {
        assertEquals(29775, PacketOpcodes.GetMailItemReq);
        assertEquals(24171, PacketOpcodes.DelMailReq);
        // Claiming attachments: mail ids 3 and 5 packed on field 15.
        var claim = GetMailItemReq.parseFrom(HexFormat.of().parseHex("7a020305"));
        assertEquals(java.util.List.of(3, 5), claim.getMailIdListList());
        // Deleting: mail ids 3 and 5 packed on field 7.
        var delete = DelMailReq.parseFrom(HexFormat.of().parseHex("3a020305"));
        assertEquals(java.util.List.of(3, 5), delete.getMailIdListList());
    }
}
