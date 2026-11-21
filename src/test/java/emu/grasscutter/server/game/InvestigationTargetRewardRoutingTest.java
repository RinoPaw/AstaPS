package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakeInvestigationRewardRspOuterClass.TakeInvestigationRewardRsp;
import emu.grasscutter.net.proto.TakeInvestigationTargetRewardRspOuterClass.TakeInvestigationTargetRewardRsp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(ServerResourceFixture.class)
final class InvestigationTargetRewardRoutingTest {
    private static final int TARGET_REQUEST_OPCODE = 29063;

    @ParameterizedTest
    @CsvSource({"78e1d403, 60001", "78e2d403, 60002"})
    void objectiveClaimIsRegisteredAndAnswered(String hex, int objectiveId) throws Exception {
        var router = new GameServerPacketHandler(PacketHandler.class);
        var session = new CaptureSession();

        router.handle(session, TARGET_REQUEST_OPCODE, new byte[0], HexFormat.of().parseHex(hex));

        assertEquals(1, session.sent.size(), "The Experience claim must receive a response");
        assertEquals(TARGET_REQUEST_OPCODE, PacketOpcodes.TakeInvestigationTargetRewardReq);
        var packet = session.sent.get(0);
        assertEquals(PacketOpcodes.TakeInvestigationTargetRewardRsp, packet.getOpcode());
        var reply = TakeInvestigationTargetRewardRsp.parseFrom(packet.getData());
        assertEquals(objectiveId, reply.getQuestId());
        // No player: verify routing and rejection without modifying inventory or progress.
        assertTrue(reply.getRetcode() != 0);
    }

    @Test
    void chapterClaimUsesTheCurrentChapterIdField() throws Exception {
        var router = new GameServerPacketHandler(PacketHandler.class);
        var session = new CaptureSession();

        router.handle(session, 9056, new byte[0], HexFormat.of().parseHex("1806"));

        assertEquals(1, session.sent.size());
        var packet = session.sent.getFirst();
        assertEquals(21327, packet.getOpcode());
        var reply = TakeInvestigationRewardRsp.parseFrom(packet.getData());
        assertEquals(6, reply.getId());
        assertTrue(reply.getRetcode() != 0);
    }

    private static final class CaptureSession extends GameSession {
        private final List<BasePacket> sent = new ArrayList<>();

        private CaptureSession() {
            super(null);
            setState(SessionState.ACTIVE);
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
