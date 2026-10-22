package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BlossomBriefInfoOuterClass.BlossomBriefInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Experimental original-client wire path for the 7.1 ley-line list.
 *
 * <p>The pinned Global 7.1 executable identifies CmdId 6743 as ABJOMOIIDHH
 * and its protobuf list at field 10. Native method CJEDOIPINHC.FHBJPLNKLLC
 * sends that list to the original client receiver at RVA 0xC78CA80.
 * The original semantic name and map UI outcome still require client testing.
 * See Genshin-Reverse analyses/blossom-map-markers for exact-native evidence.
 */
public class PacketBlossomBriefInfoNotify extends BasePacket {
    private static final int VERIFIED_NATIVE_LIST_OPCODE = 6743;
    private static final int VERIFIED_NATIVE_LIST_FIELD = 10;

    public PacketBlossomBriefInfoNotify(Iterable<BlossomBriefInfo> blossoms) {
        super(VERIFIED_NATIVE_LIST_OPCODE);
        try {
            var buffer = new ByteArrayOutputStream();
            var writer = CodedOutputStream.newInstance(buffer);
            for (var entry : blossoms) {
                writer.writeMessage(VERIFIED_NATIVE_LIST_FIELD, entry);
            }
            writer.flush();
            setData(buffer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
