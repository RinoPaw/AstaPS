package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.PlayerLevelData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakePlayerLevelRewardRspOuterClass.TakePlayerLevelRewardRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameServerPacketHandler;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

/** Routes 7.2 rank claims through the real router, with isolated resources and inventory. */
@ExtendWith(ServerResourceFixture.class)
final class PlayerLevelRewardClaimTest {
    private static final int REQUEST_OPCODE = 8154;
    private RecordingPlayer player;
    private CaptureSession session;
    private GameServerPacketHandler router;
    private final Map<Integer, PlayerLevelData> oldLevels = new HashMap<>();
    private RewardData oldReward;
    private ItemData oldPrimogem;

    @BeforeEach
    void setup() throws Exception {
        for (int level : List.of(2, 3, 4, 5, 6)) {
            oldLevels.put(level, GameData.getPlayerLevelDataMap().get(level));
            GameData.getPlayerLevelDataMap()
                    .put(
                            level,
                            new Gson()
                                    .fromJson(
                                            "{\"level\":" + level + ",\"rewardId\":991001}",
                                            PlayerLevelData.class));
        }
        var primogem = new Gson().fromJson(
                "{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class);
        oldPrimogem = GameData.getItemDataMap().put(201, primogem);
        oldReward = GameData.getRewardDataMap().get(991001);
        var reward = new RewardData();
        reward.rewardId = 991001;
        reward.rewardItemList = List.of(new ItemParamData(201, 100));
        GameData.getRewardDataMap().put(reward.rewardId, reward);
        player = allocate(RecordingPlayer.class);
        player.inventory = allocate(RecordingInventory.class);
        player.inventory.grants = new ArrayList<>();
        player.setRewardedLevels(new HashSet<>());
        session = new CaptureSession();
        session.player = player;
        player.setSession(session);
        router = new GameServerPacketHandler(PacketHandler.class);
    }

    @AfterEach
    void restore() {
        oldLevels.forEach(
                (id, row) -> {
                    if (row == null) GameData.getPlayerLevelDataMap().remove((int) id);
                    else GameData.getPlayerLevelDataMap().put((int) id, row);
                });
        if (oldPrimogem == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, oldPrimogem);
        if (oldReward == null) GameData.getRewardDataMap().remove(991001);
        else GameData.getRewardDataMap().put(991001, oldReward);
    }

    @Test
    void earnedRewardPaysOnceAndRetryIsAnswered() throws Exception {
        claim(5);
        assertEquals(0, reply().getRetcode());
        assertEquals(991001, reply().getRewardId());
        assertEquals(1, player.inventory.grants.size());
        assertTrue(player.getRewardedLevels().contains(5));
        assertEquals(1, player.saves);
        session.sent.clear();
        claim(5);
        assertEquals(0, reply().getRetcode(), "A duplicate must be answered without paying again");
        assertEquals(1, player.inventory.grants.size());
        assertEquals(1, player.saves);
    }

    @Test
    void refusedRewardKeepsTheRankClaimable() throws Exception {
        player.inventory.refuse = true;
        claim(5);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.getRewardedLevels().isEmpty());
        assertEquals(0, player.saves);

        player.inventory.refuse = false;
        session.sent.clear();
        claim(5);
        assertEquals(0, reply().getRetcode());
        assertEquals(Set.of(5), player.getRewardedLevels());
        assertEquals(1, player.inventory.grants.size());
    }

    @Test
    void rewardCurrencyOutsideItemListIsDelivered() throws Exception {
        var reward = GameData.getRewardDataMap().get(991001);
        reward.rewardItemList = List.of();
        reward.hcoin = 100;
        claim(5);
        assertEquals(0, reply().getRetcode());
        var item = player.inventory.grants.getFirst().getFirst();
        assertEquals(201, item.getItemId());
        assertEquals(100, item.getCount());
    }

    @Test
    void futureRankIsRejectedWithoutPaying() throws Exception {
        claim(6);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        assertTrue(player.getRewardedLevels().isEmpty());
    }

    @Test
    void unknownRankIsAnsweredWithoutThrowing() throws Exception {
        claim(9999);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
    }

    @Test
    void missingRewardDataDoesNotConsumeTheClaim() throws Exception {
        GameData.getRewardDataMap().remove(991001);
        claim(5);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.getRewardedLevels().isEmpty());
    }

    @Test
    void nullPlayerIsRejectedAndAnswered() throws Exception {
        session.player = null;
        claim(5);
        assertNotEquals(0, reply().getRetcode());
    }

    @ParameterizedTest
    @CsvSource({"4802, 2", "4803, 3", "4804, 4"})
    void rankRequestsRouteDecodeAndPayOnce(String hex, int level) throws Exception {
        route(hex);
        assertEquals(REQUEST_OPCODE, PacketOpcodes.TakePlayerLevelRewardReq);
        assertEquals(0, reply().getRetcode());
        assertEquals(level, reply().getLevel());
        assertEquals(991001, reply().getRewardId());
        assertEquals(1, player.inventory.grants.size());
        assertEquals(Set.of(level), player.getRewardedLevels());
        session.sent.clear();
        route(hex);
        assertEquals(0, reply().getRetcode());
        assertEquals(1, player.inventory.grants.size());
        assertEquals(1, player.saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "7805", "4a0105", "480580", "4c"})
    void missingWrongWireTypeOrMalformedRankIsAnsweredWithoutPaying(String hex) throws Exception {
        route(hex);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        assertTrue(player.getRewardedLevels().isEmpty());
    }

    @Test
    void unknownFieldsAreSkippedAndLastRankWins() throws Exception {
        route("08014805");
        assertEquals(0, reply().getRetcode());
        assertEquals(5, reply().getLevel());
        session.sent.clear();
        route("48054806");
        assertNotEquals(0, reply().getRetcode());
        assertEquals(1, player.inventory.grants.size(), "A trailing future rank must not pay");
    }

    private void claim(int level) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var output = CodedOutputStream.newInstance(bytes);
        output.writeUInt32(9, level);
        output.flush();
        router.handle(session, REQUEST_OPCODE, new byte[0], bytes.toByteArray());
    }

    private void route(String hex) {
        router.handle(session, REQUEST_OPCODE, new byte[0], HexFormat.of().parseHex(hex));
    }

    private TakePlayerLevelRewardRsp reply() throws Exception {
        assertEquals(1, session.sent.size(), "Every claim must receive a response");
        assertEquals(PacketOpcodes.TakePlayerLevelRewardRsp, session.sent.get(0).getOpcode());
        return TakePlayerLevelRewardRsp.parseFrom(session.sent.get(0).getData());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        RecordingInventory inventory;
        int saves;

        @Override
        public int getLevel() {
            return 5;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingInventory extends Inventory {
        List<List<GameItem>> grants;
        boolean refuse;

        private RecordingInventory() {
            super(null);
        }

        @Override
        public InventoryAddResult addItems(Collection<GameItem> items, ActionReason reason) {
            assertEquals(ActionReason.PlayerUpgradeReward, reason);
            if (refuse) return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            grants.add(List.copyOf(items));
            return new InventoryAddResult(items.stream()
                    .map(item -> new InventoryAddResult.Entry(item.getItemId(), item.getCount(),
                            item.getCount(), InventoryAddResult.Status.ADDED))
                    .toList());
        }
    }

    private static final class CaptureSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        Player player;

        private CaptureSession() {
            super(null);
            setState(SessionState.ACTIVE);
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
