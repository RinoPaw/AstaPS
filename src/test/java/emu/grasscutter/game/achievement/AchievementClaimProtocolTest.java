package emu.grasscutter.game.achievement;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TakeAchievementRewardReqOuterClass.TakeAchievementRewardReq;
import emu.grasscutter.net.proto.TakeAchievementRewardRspOuterClass.TakeAchievementRewardRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerTakeAchievementRewardReq;
import emu.grasscutter.server.packet.send.PacketTakeAchievementRewardRsp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Real request routing and packet builders, with no inventory or database mutations. */
@ExtendWith(ServerResourceFixture.class)
final class AchievementClaimProtocolTest {
    private RecordingAchievements achievements;
    private CaptureSession session;

    @BeforeEach
    void setup() throws Exception {
        achievements = new RecordingAchievements();
        var player = allocate(RecordingPlayer.class);
        player.achievements = achievements;
        session = new CaptureSession();
        session.player = player;
    }

    @Test
    void nativeClaimRoutesToTheRewardBoundary() throws Exception {
        var router = new GameServerPacketHandler(PacketHandler.class);
        router.handle(session, 4716, new byte[0], HexFormat.of().parseHex("6203f98005"));
        assertEquals(4716, PacketOpcodes.TakeAchievementRewardReq);
        assertEquals(List.of(82041), achievements.claimed);
        assertEquals(1, achievements.calls);
    }

    @Test
    void generatedRequestUsesNativeField12() throws Exception {
        assertEquals(
                12,
                TakeAchievementRewardReq.getDescriptor().findFieldByName("id_list").getNumber());
        var nativeFixture = HexFormat.of().parseHex("6203f98005");
        assertEquals(
                List.of(82041), TakeAchievementRewardReq.parseFrom(nativeFixture).getIdListList());
        assertArrayEquals(
                nativeFixture,
                TakeAchievementRewardReq.newBuilder().addIdList(82041).build().toByteArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"6203f98005", "60f98005", "08016203f98005", "6203f980051801"})
    void packedUnpackedAndUnknownFieldsAreAccepted(String hex) throws Exception {
        handle(hex);
        assertEquals(List.of(82041), achievements.claimed);
        assertEquals(1, achievements.calls);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "5203f98005",
                "6203f980",
                "65f9800500",
                "64",
                "620100",
                "6206f98005f98005",
                "6203f9800500",
                "6203f980058080",
                "6203f980051c"
            })
    void malformedMissingAndDuplicateIdsCannotReachRewards(String hex) throws Exception {
        handle(hex);
        assertRejected();
    }

    @Test
    void oversizedPayloadAndTooManyIdsCannotReachRewards() throws Exception {
        new HandlerTakeAchievementRewardReq().handle(session, new byte[0], new byte[4097]);
        assertRejected();
        session.sent.clear();
        new HandlerTakeAchievementRewardReq().handle(session, new byte[0], batch(65));
        assertRejected();
    }

    @Test
    void maximumSizedBatchIsAccepted() throws Exception {
        new HandlerTakeAchievementRewardReq().handle(session, new byte[0], batch(64));
        assertEquals(64, achievements.claimed.size());
        assertEquals(1, achievements.calls);
    }

    @Test
    void anyInvalidOrIneligibleIdRejectsTheWholeBatch() throws Exception {
        achievements.invalid.add(82042);
        new HandlerTakeAchievementRewardReq().handle(session, new byte[0], batch(2));
        assertRejected();
        achievements.invalid.clear();
        achievements.eligible = false;
        session.sent.clear();
        handle("6203f98005");
        assertRejected();
    }

    @Test
    void missingPlayerOrAchievementStateIsRejected() throws Exception {
        session.player = null;
        handle("6203f98005");
        assertRejected();
        var player = allocate(RecordingPlayer.class);
        session.player = player;
        session.sent.clear();
        handle("6203f98005");
        assertRejected();
    }

    @Test
    void retryCannotDeliverRewardsTwice() throws Exception {
        handle("6203f98005");
        handle("6203f98005");
        assertEquals(1, achievements.calls);
        assertEquals(1, session.sent.size());
        assertEquals(Retcode.RET_REWARD_HAS_TAKEN_VALUE,
                TakeAchievementRewardRsp.parseFrom(session.sent.get(0).getData()).getRetcode());
    }

    @Test
    void responseBuildersUseNativeSuccessAndErrorFields() throws Exception {
        var item = ItemParam.newBuilder().setItemId(201).setCount(5).build();
        var success = new PacketTakeAchievementRewardRsp(List.of(82041), List.of(item));
        assertEquals(21155, success.getOpcode());
        assertArrayEquals(HexFormat.of().parseHex("3203f98005620508c9011005"), success.getData());
        var error = new PacketTakeAchievementRewardRsp();
        assertEquals(21155, error.getOpcode());
        var decoded = TakeAchievementRewardRsp.parseFrom(error.getData());
        assertEquals(Retcode.RET_REWARD_HAS_TAKEN_VALUE, decoded.getRetcode());
        assertEquals(0, decoded.getIdListCount());
        assertEquals(0, decoded.getItemListCount());
        assertEquals(
                2, TakeAchievementRewardRsp.getDescriptor().findFieldByName("retcode").getNumber());
        assertEquals(
                6, TakeAchievementRewardRsp.getDescriptor().findFieldByName("id_list").getNumber());
        assertEquals(
                12,
                TakeAchievementRewardRsp.getDescriptor().findFieldByName("item_list").getNumber());
    }

    private void handle(String hex) throws Exception {
        new HandlerTakeAchievementRewardReq().handle(session, new byte[0], HexFormat.of().parseHex(hex));
    }

    private void assertRejected() {
        assertEquals(0, achievements.calls);
        assertNull(achievements.claimed);
        assertEquals(1, session.sent.size());
    }

    private static byte[] batch(int size) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        for (int i = 0; i < size; i++) output.writeUInt32(12, 82041 + i);
        output.flush();
        return bytes.toByteArray();
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingAchievements extends Achievements {
        final Set<Integer> invalid = new HashSet<>();
        List<Integer> claimed;
        boolean eligible = true;
        int calls;
        RecordingAchievements() { super(null, 1, null, new HashMap<>(), 0, new ArrayList<>()); }
        @Override public boolean isInvalid(int id) { return invalid.contains(id); }
        @Override public boolean isRewardLeft(int id) { return eligible; }
        @Override public synchronized void takeReward(List<Integer> ids) {
            claimed = List.copyOf(ids);
            eligible = false;
            calls++;
        }
    }

    private static final class RecordingPlayer extends Player {
        RecordingAchievements achievements;
        @Override public Achievements getAchievements() { return achievements; }
    }

    private static final class CaptureSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        Player player;
        CaptureSession() { super(null); setState(SessionState.ACTIVE); }
        @Override public Player getPlayer() { return player; }
        @Override public void send(BasePacket packet) { sent.add(packet); }
    }
}
