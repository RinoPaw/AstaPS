package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.proto.BlossomBriefInfoOuterClass.BlossomBriefInfo;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PacketBlossomBriefInfoNotifyNativeTest {
    @Test
    void originalClientListEnvelopeUsesNative6743FieldTen() throws IOException {
        var gold = BlossomBriefInfo.newBuilder()
                .setSceneId(3).setResin(20).setRefreshId(1).build();
        var blue = BlossomBriefInfo.newBuilder()
                .setSceneId(3).setResin(20).setRefreshId(2).build();
        var packet = new PacketBlossomBriefInfoNotify(List.of(gold, blue));
        assertEquals(6743, packet.getOpcode());

        var reader = CodedInputStream.newInstance(packet.getData());
        assertEquals(0x52, reader.readTag());
        assertArrayEquals(gold.toByteArray(), reader.readByteArray());
        assertEquals(0x52, reader.readTag());
        assertArrayEquals(blue.toByteArray(), reader.readByteArray());
        assertEquals(0, reader.readTag());
    }

    @Test
    void emptyListHasNoFields() {
        var packet = new PacketBlossomBriefInfoNotify(List.of());
        assertEquals(6743, packet.getOpcode());
        assertArrayEquals(new byte[0], packet.getData());
    }
}
