package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Descriptors.FieldDescriptor;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakePlayerLevelRewardReqOuterClass.TakePlayerLevelRewardReq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdventureRankRequestSchemaTest {
    @Test
    void canonicalDescriptorUsesNativeFieldAndUnsignedType() {
        var field = TakePlayerLevelRewardReq.getDescriptor().findFieldByName("level");
        assertEquals(9, field.getNumber());
        assertEquals(FieldDescriptor.Type.UINT32, field.getType());
        assertEquals(8154, PacketOpcodes.TakePlayerLevelRewardReq);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void decodesNativeKatheryneRequest(int level) throws Exception {
        byte[] wire = {(byte) 0x48, (byte) level};
        assertEquals(level, TakePlayerLevelRewardReq.parseFrom(wire).getLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void writesTheNativeWireLayout(int level) {
        byte[] wire = {(byte) 0x48, (byte) level};
        assertArrayEquals(
                wire,
                TakePlayerLevelRewardReq.newBuilder().setLevel(level).build().toByteArray());
    }

    @Test
    void staleFieldFifteenIsNotTheCanonicalLevel() throws Exception {
        assertEquals(0, TakePlayerLevelRewardReq.parseFrom(new byte[] {0x78, 0x03}).getLevel());
    }
}
