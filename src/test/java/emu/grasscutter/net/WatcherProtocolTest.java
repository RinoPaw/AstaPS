package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.IceCreatureDataNotifyOuterClass.IceCreatureDataNotify;
import emu.grasscutter.net.proto.IceCreatureDataNotifyOuterClass.IceCreatureTypeGadget;
import emu.grasscutter.net.proto.IceCreatureSetTypeGadgetReqOuterClass.IceCreatureSetTypeGadgetReq;
import emu.grasscutter.net.proto.UnlockTransPointRspOuterClass.UnlockTransPointRsp;
import emu.grasscutter.net.proto.WatcherEventNotifyOuterClass.WatcherEventNotify;
import emu.grasscutter.net.proto.WatcherEventTypeNotifyOuterClass.WatcherEventTypeNotify;
import emu.grasscutter.server.packet.send.PacketWatcherAllDataNotify;
import emu.grasscutter.server.packet.send.PacketWatcherChangeNotify;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WatcherProtocolTest {
    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }

    @Test
    void allDataListsWatchersOnTwelve() {
        var packet = new PacketWatcherAllDataNotify(List.of(80001));
        assertEquals(329, packet.getOpcode());
        // watcher_list (12, packed): 80001 = 81 f1 04.
        assertEquals("620381f104", hex(packet.getData()));
    }

    @Test
    void legacyChangeRetainsItsDescriptorButHasNoCurrentRoute() {
        var packet = new PacketWatcherChangeNotify(List.of(), List.of(81005));
        assertEquals(0, packet.getOpcode());
        assertEquals("6203edf804", hex(packet.getData()));
    }

    @Test
    void clientEventsParseSevenTwoFields() throws Exception {
        // watcher_id (11) = 81005, add_progress (7) = 1.
        var event = WatcherEventNotify.parseFrom(HexFormat.of().parseHex("58edf8043801"));
        assertEquals(81005, event.getWatcherId());
        assertEquals(1, event.getAddProgress());
        assertEquals(29994, PacketOpcodes.WatcherEventNotify);

        // watcher_trigger_type (10) = 113, param_list (7) = [70510009], add_progress (3) = 1.
        var type = WatcherEventTypeNotify.parseFrom(HexFormat.of().parseHex("5071" + "3a04b9cbcf21" + "1801"));
        assertEquals(113, type.getWatcherTriggerType());
        assertEquals(List.of(70510009), type.getParamListList());
        assertEquals(1, type.getAddProgress());
    }

    @Test
    void unlockTransPointRspRetcodeOnEight() {
        assertEquals(6339, PacketOpcodes.UnlockTransPointRsp);
        assertEquals("4001", hex(UnlockTransPointRsp.newBuilder().setRetcode(1).build().toByteArray()));
    }

    @Test
    void legacyIceCreatureDataPreservesTheCapturedLayout() {
        assertEquals(0, PacketOpcodes.IceCreatureDataNotify);
        var data =
                IceCreatureDataNotify.newBuilder()
                        .addUnlockedGadgetIdList(73094016)
                        .addTypeGadgetList(IceCreatureTypeGadget.newBuilder().setGadgetId(73094016).setType(1))
                        .setTotalLikeNum(2805)
                        .addBuiltGuidList(1)
                        .build()
                        .toByteArray();
        // built_guid_list 4 (packed fixed64), type_gadget_list 10 {4, 7}, 13 = 2805, 14 packed.
        assertEquals(
                "2208" + "0100000000000000"
                        + "5207" + "2080a7ed22" + "3801"
                        + "68f515"
                        + "720480a7ed22",
                hex(data));
    }

    @Test
    void legacyIceCreatureSetTypeGadgetPreservesItsFields() throws Exception {
        assertEquals(0, PacketOpcodes.IceCreatureSetTypeGadgetReq);
        // type (4) = 21, gadget_id (5) = 73174021.
        var req = IceCreatureSetTypeGadgetReq.parseFrom(HexFormat.of().parseHex("2015" + "288598f222"));
        assertEquals(21, req.getType());
        assertEquals(73174021, req.getGadgetId());
    }
}
