package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class PacketWorldPlayerReviveRspTest {
    @Test
    public void usesCurrent71CandidateOpcode() {
        assertEquals(7003, new PacketWorldPlayerReviveRsp().getOpcode());
    }
}
