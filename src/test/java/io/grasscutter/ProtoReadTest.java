package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.utils.ProtoRead;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/** Unit coverage for the schema-free protobuf field reader. */
public final class ProtoReadTest {
    @Test
    public void readsRequestedFieldsAndDefaultsForAbsentOnes() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeString(2, "account");
        output.writeUInt32(7, 5);
        output.flush();
        var payload = bytes.toByteArray();

        assertEquals("account", ProtoRead.string(payload, 2));
        assertEquals(5, ProtoRead.varint(payload, 7));
        assertEquals("", ProtoRead.string(payload, 9));
        assertEquals(0, ProtoRead.varint(payload, 9));
    }

    @Test
    public void skipsOtherWireTypesAndNestedMessages() throws Exception {
        var nestedBytes = new ByteArrayOutputStream();
        var nested = CodedOutputStream.newInstance(nestedBytes);
        nested.writeUInt32(7, 999);
        nested.flush();

        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeFixed64(1, Long.MAX_VALUE);
        output.writeByteArray(2, nestedBytes.toByteArray());
        output.writeFixed32(3, 1069547520);
        output.writeUInt32(7, 60);
        output.flush();

        assertEquals(60, ProtoRead.varint(bytes.toByteArray(), 7));
    }

    @Test
    public void malformedPayloadReturnsDefaults() {
        assertEquals(0, ProtoRead.varint(new byte[] {(byte) 0xFF}, 1));
        assertEquals("", ProtoRead.string(new byte[] {0x12, 0x05, 'a'}, 2));
        assertEquals("", ProtoRead.string(new byte[0], 2));
        assertEquals(0, ProtoRead.varint(null, 2));
    }
}
