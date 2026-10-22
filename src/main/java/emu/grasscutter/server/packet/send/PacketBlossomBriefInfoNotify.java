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
 * <p>The pinned Global 7.1 executable identifies two native messages containing
 * the exact original repeated BlossomBriefInfo element type:
 * 6743/field 10 and 20484/field 14. Both reach the original-client handler
 * CJEDOIPINHC.FOEKNCLMGDP at RVA 0xC78CA80. Their semantic mapping and
 * map display behavior are still unresolved and must be tested separately.
 * See Genshin-Reverse analyses/blossom-map-markers for exact-native evidence.
 */
public class PacketBlossomBriefInfoNotify extends BasePacket {
    public static final int NATIVE_LIST_6743 = 6743;
    public static final int NATIVE_LIST_20484 = 20484;

    public PacketBlossomBriefInfoNotify(Iterable<BlossomBriefInfo> blossoms) {
        this(blossoms, NATIVE_LIST_6743);
    }

    public PacketBlossomBriefInfoNotify(Iterable<BlossomBriefInfo> blossoms, int opcode) {
        super(opcode);
        int listField = switch (opcode) {
            case NATIVE_LIST_6743 -> 10;
            case NATIVE_LIST_20484 -> 14;
            default -> throw new IllegalArgumentException("Unsupported original-client blossom opcode " + opcode);
        };
        try {
            var buffer = new ByteArrayOutputStream();
            var writer = CodedOutputStream.newInstance(buffer);
            for (var entry : blossoms) {
                writer.writeMessage(listField, entry);
            }
            writer.flush();
            setData(buffer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
