package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class AmberGuide2178ProbeTest {
    @Test
    void decodesObservedUint32WireTagWithoutAssigningAFieldMeaning() throws Exception {
        // Field number 7 is illustrative, not an asserted mapping for native CmdId 2178.
        assertEquals("7=123", GameSession.summarizeAmber2178Fields(new byte[] {0x38, 0x7B}));
    }

    @Test
    void distinguishesEmptyMessageFromUnsignedUint32() throws Exception {
        assertEquals("(empty)", GameSession.summarizeAmber2178Fields(new byte[0]));
        assertEquals(
                "7=4294967295",
                GameSession.summarizeAmber2178Fields(
                        new byte[] {0x38, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f}));
    }

    @Test
    void doesNotLogRawUnknownBytes() throws Exception {
        assertEquals(
                "7(wire=2)",
                GameSession.summarizeAmber2178Fields(new byte[] {0x3a, 0x02, 0x01, 0x02}));
        assertEquals("oversized", GameSession.summarizeAmber2178Fields(new byte[65]));
    }

    @Test
    void rejectsTruncatedVarint() {
        assertThrows(
                IOException.class,
                () -> GameSession.summarizeAmber2178Fields(
                        new byte[] {0x38, (byte) 0x80}));
    }
}
