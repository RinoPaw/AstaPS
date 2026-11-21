package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.game.avatar.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarFetterLevelRewardReqOuterClass.AvatarFetterLevelRewardReq;
import emu.grasscutter.net.proto.AvatarFetterLevelRewardRspOuterClass.AvatarFetterLevelRewardRsp;
import emu.grasscutter.server.game.GameSession;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import org.junit.jupiter.api.*;

import sun.misc.Unsafe;

import java.util.*;

final class AvatarFetterClaimTest {
    private TestPlayer player;
    private TestSession session;
    private final Map<Integer, RewardData> oldRewards = new HashMap<>();
    private ItemData oldItem;

    @BeforeEach
    void setup() throws Exception {
        player = allocate(TestPlayer.class);
        player.avatar = new TestAvatar();
        player.inventory = new TestInventory(player);
        player.storage =
                new AvatarStorage(player) {
                    @Override
                    public Avatar getAvatarByGuid(long guid) {
                        return guid == 42 ? AvatarFetterClaimTest.this.player.avatar : null;
                    }
                };
        var data = new Gson().fromJson("{\"id\":10000126}", AvatarData.class);
        var configured = new Int2IntOpenHashMap();
        configured.put(1, 991201);
        configured.put(10, 991210);
        var field = AvatarData.class.getDeclaredField("fetterRewards");
        field.setAccessible(true);
        field.set(data, configured);
        player.avatar.data = data;
        oldItem =
                GameData.getItemDataMap()
                        .put(
                                991200,
                                new Gson()
                                        .fromJson(
                                                "{\"id\":991200,\"itemType\":\"ITEM_MATERIAL\",\"stackLimit\":9999}",
                                                ItemData.class));
        for (int id : new int[] {991201, 991210}) {
            var reward = new RewardData();
            reward.rewardId = id;
            reward.rewardItemList = List.of(new ItemParamData(991200, 1));
            oldRewards.put(id, GameData.getRewardDataMap().put(id, reward));
        }
        session = new TestSession();
        session.player = player;
    }

    @AfterEach
    void restore() {
        oldRewards.forEach(
                (id, row) -> {
                    if (row == null) GameData.getRewardDataMap().remove((int) id);
                    else GameData.getRewardDataMap().put((int) id, row);
                });
        if (oldItem == null) GameData.getItemDataMap().remove(991200);
        else GameData.getItemDataMap().put(991200, oldItem);
    }

    @Test
    void guidOnlyRequestClaimsAllEligibleLevelsAndReturnsTheList() throws Exception {
        claim(0);
        assertEquals(List.of(991201, 991210), reply().getRewardIdListList());
        assertEquals(Set.of(1, 10), player.avatar.claimed);
        assertEquals(1, player.avatar.saves);
        assertEquals(1, player.inventory.grants);
        claim(0);
        assertEquals(0, reply().getRetcode());
        assertTrue(reply().getRewardIdListList().isEmpty());
        assertEquals(1, player.inventory.grants);
    }

    @Test
    void levelOneCannotClaimTheNameCardEarly() throws Exception {
        player.avatar.level = 1;
        claim(0);
        assertEquals(List.of(991201), reply().getRewardIdListList());
        assertEquals(Set.of(1), player.avatar.claimed);
        claim(10);
        assertNotEquals(0, reply().getRetcode());
    }

    @Test
    void fullInventoryKeepsEveryLevelClaimableForRetry() throws Exception {
        player.inventory.refuse = true;
        claim(0);
        assertNotEquals(0, reply().getRetcode());
        assertTrue(player.avatar.claimed.isEmpty());
        assertEquals(0, player.avatar.saves);
        player.inventory.refuse = false;
        claim(0);
        assertEquals(Set.of(1, 10), player.avatar.claimed);
    }

    private void claim(int level) throws Exception {
        session.sent.clear();
        var request =
                AvatarFetterLevelRewardReq.newBuilder()
                        .setAvatarGuid(42)
                        .setFetterLevel(level)
                        .build();
        new HandlerAvatarFetterLevelRewardReq().handle(session, new byte[0], request.toByteArray());
    }

    private AvatarFetterLevelRewardRsp reply() throws Exception {
        var packet =
                session.sent.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AvatarFetterLevelRewardRsp)
                        .findFirst()
                        .orElseThrow();
        return AvatarFetterLevelRewardRsp.parseFrom(packet.getData());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class TestPlayer extends Player {
        TestAvatar avatar;
        TestInventory inventory;
        AvatarStorage storage;

        @Override
        public AvatarStorage getAvatars() {
            return storage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static class TestAvatar extends Avatar {
        AvatarData data;
        Set<Integer> claimed = new HashSet<>();
        int level = 10;
        int saves;

        @Override
        public AvatarData getAvatarData() {
            return data;
        }

        @Override
        public int getFetterLevel() {
            return level;
        }

        @Override
        public Set<Integer> getRewardedFetterLevels() {
            return claimed;
        }

        @Override
        public void addRewardedFetterLevel(int level) {
            claimed.add(level);
        }

        @Override
        public long getGuid() {
            return 42;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static class TestInventory extends Inventory {
        int grants;
        boolean refuse;

        TestInventory(Player player) {
            super(player);
        }

        @Override
        public int getItemCountById(int id) {
            return 0;
        }

        @Override
        public InventoryAddResult addItems(Collection<GameItem> items, ActionReason reason) {
            if (refuse)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            grants++;
            return new InventoryAddResult(
                    items.stream()
                            .map(
                                    i ->
                                            new InventoryAddResult.Entry(
                                                    i.getItemId(),
                                                    i.getCount(),
                                                    i.getCount(),
                                                    InventoryAddResult.Status.ADDED))
                            .toList());
        }
    }

    private static class TestSession extends GameSession {
        Player player;
        List<BasePacket> sent = new ArrayList<>();

        TestSession() {
            super(null);
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
