package emu.grasscutter.net.packet;

import emu.grasscutter.Grasscutter;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.net.proto.EvtBulletDeactiveNotifyOuterClass.EvtBulletDeactiveNotify;
import emu.grasscutter.server.packet.recv.HandlerUnionCmdNotify;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DecodedPacketLoggerTest {
    @Test
    void decodesByOpcodeEvenWhenHandlerHasAnotherName() {
        byte[] payload = EvtBulletDeactiveNotify.newBuilder()
                .setEntityId(42).build().toByteArray();
        String rendered = DecodedPacketLogger.formatPayload(
                PacketOpcodes.EvtBulletDeactiveNotify, payload, new HandlerUnionCmdNotify());
        assertTrue(rendered.contains("entity_id: 42"));
        assertFalse(rendered.contains("payload="));
    }

    @Test
    void unknownOpcodeWithoutHandlerRetainsWireFieldsAndHex() {
        String rendered = DecodedPacketLogger.formatPayload(987654, new byte[] {8, 5}, null);
        assertTrue(rendered.contains("payload=0805"));
        assertTrue(rendered.contains("rawFields="));
        assertTrue(rendered.contains("1: 5"));
    }

    @Test
    void malformedNamedPayloadRetainsItsOriginalBytes() {
        String rendered = DecodedPacketLogger.formatPayload(
                PacketOpcodes.EvtBulletDeactiveNotify, new byte[] {(byte) 0x80}, null);
        assertTrue(rendered.contains("payload=80"));
        assertTrue(rendered.contains("non-protobuf payload"));
    }

    @Test
    void packetLoggingFiltersStillApplyToUnrecognizedOpcodes() {
        var oldMode = Grasscutter.getConfig().server.game.logPackets;
        var oldLoops = Grasscutter.getConfig().server.game.isShowLoopPackets;
        var oldWhitelist = Grasscutter.getConfig().server.debugWhitelist;
        var oldBlacklist = Grasscutter.getConfig().server.debugBlacklist;
        try {
            Grasscutter.getConfig().server.game.isShowLoopPackets = false;
            Grasscutter.getConfig().server.game.logPackets = ServerDebugMode.ALL;
            assertTrue(DecodedPacketLogger.shouldDump(987654));
            assertFalse(DecodedPacketLogger.shouldDump(PacketOpcodes.PingReq));
            assertFalse(DecodedPacketLogger.shouldDump(
                    PacketOpcodes.WorldOwnerBlossomScheduleInfoNotify));

            Grasscutter.getConfig().server.debugWhitelist = Set.of(987654);
            Grasscutter.getConfig().server.game.logPackets = ServerDebugMode.WHITELIST;
            assertTrue(DecodedPacketLogger.shouldDump(987654));
            assertFalse(DecodedPacketLogger.shouldDump(123456));

            Grasscutter.getConfig().server.debugBlacklist = Set.of(987654);
            Grasscutter.getConfig().server.game.logPackets = ServerDebugMode.BLACKLIST;
            assertFalse(DecodedPacketLogger.shouldDump(987654));
            assertTrue(DecodedPacketLogger.shouldDump(123456));
        } finally {
            Grasscutter.getConfig().server.game.logPackets = oldMode;
            Grasscutter.getConfig().server.game.isShowLoopPackets = oldLoops;
            Grasscutter.getConfig().server.debugWhitelist = oldWhitelist;
            Grasscutter.getConfig().server.debugBlacklist = oldBlacklist;
        }
    }
}
