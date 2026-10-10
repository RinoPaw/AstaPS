package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AmberInteractionPacketTest {
    @Test
    void recoversExactNativeGlobal71InteractionBoolFieldSeven() throws IOException {
        // Client serializer NDAJDBCBAAE.IENGFLPCLNM @ 0x7AD8C30 emits tag 0x38.
        assertEquals(Boolean.TRUE, GameSession.readAmberInteraction27447Flag(
                HexFormat.of().parseHex("3801")));
        assertEquals(Boolean.FALSE, GameSession.readAmberInteraction27447Flag(
                HexFormat.of().parseHex("3800")));
    }

    @Test
    void doesNotTreatAbsentFieldAsFalseAndSkipsUnknownFields() throws IOException {
        assertNull(GameSession.readAmberInteraction27447Flag(new byte[0]));
        assertNull(GameSession.readAmberInteraction27447Flag(
                HexFormat.of().parseHex("0801")));
        assertEquals(Boolean.TRUE, GameSession.readAmberInteraction27447Flag(
                HexFormat.of().parseHex("08013801")));
    }
}
