package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetReliquaryOfferingReqOuterClass.GetReliquaryOfferingReq;
import emu.grasscutter.net.proto.GetReliquaryOfferingRspOuterClass.GetReliquaryOfferingRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.ReliquaryFilterStateNotifyOuterClass.ReliquaryFilterStateNotify;
import emu.grasscutter.net.proto.ReliquaryFilterStateSaveNotifyOuterClass.ReliquaryFilterStateSaveNotify;
import emu.grasscutter.net.proto.Uint32PairOuterClass.Uint32Pair;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;

@ExtendWith(ServerResourceFixture.class)
final class ReliquaryTransactionTest {
    private RecordingPlayer player;
    private Field configField;
    private Object previousConfig;
    private ItemData previousElixir;

    @BeforeEach
    void setup() throws Exception {
        var config = new ReliquaryConfig();
        config.constants.put(
                "CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_1",
                List.of("220109", "2", "2", "105005", "1;1;2;4;3", "14"));
        config.constants.put(
                "CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_2",
                List.of(
                        "5",
                        "5",
                        "25",
                        "100",
                        "1",
                        "0;0;0;0;4;5;6;7;10;11;12;13;18;19;20;21;30;31;32;33;50"));
        config.constants.put(
                "CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_3",
                List.of("13", "105002,0.5;105003,2"));
        configField = ReliquaryConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        previousConfig = configField.get(null);
        configField.set(null, config);
        previousElixir =
                GameData.getItemDataMap()
                        .put(
                                105005,
                                new Gson()
                                        .fromJson(
                                                "{\"id\":105005,\"itemType\":\"ITEM_MATERIAL\",\"stackLimit\":9999}",
                                                ItemData.class));
        player = allocate(RecordingPlayer.class);
        player.inventory = allocate(RecordingInventory.class);
        player.inventory.materials = new HashMap<>();
        player.inventory.relics = new HashMap<>();
        player.inventory.grants = new ArrayList<>();
        player.inventory.acceptOutput = true;
        player.state = new ReliquaryState();
        player.sent = new ArrayList<>();
        var state = new ArtifactTransmuterSystem.PlayerState();
        state.ensureCycle(System.currentTimeMillis() / 1000);
        player.setTransmuterState(state);
    }

    @AfterEach
    void restore() throws Exception {
        configField.set(null, previousConfig);
        if (previousElixir == null) GameData.getItemDataMap().remove(105005);
        else GameData.getItemDataMap().put(105005, previousElixir);
    }

    private GetReliquaryOfferingReq.Builder request() {
        return GetReliquaryOfferingReq.newBuilder()
                .setScheduleId(player.getTransmuterState().scheduleId);
    }

    private GetReliquaryOfferingRsp extract(GetReliquaryOfferingReq.Builder request)
            throws Exception {
        new ReliquarySystem(player, 41)
                .handle(PacketOpcodes.GetReliquaryOfferingReq, request.build().toByteArray());
        var packet =
                player.sent.stream().filter(p -> p.getOpcode() == 28301).findFirst().orElseThrow();
        assertNotNull(packet.getHeader());
        return GetReliquaryOfferingRsp.parseFrom(packet.getData());
    }

    private ItemParam material(int count) {
        return ItemParam.newBuilder().setItemId(105002).setCount(count).build();
    }

    @Test
    void extractionConsumesOnlySubmittedMaterialsAndGrantsTheElixir() throws Exception {
        player.inventory.materials.put(105002, 240);
        var response = extract(request().addItemList(material(100)).addItemList(material(100)));
        assertEquals(0, response.getRetcode());
        assertEquals(40, player.inventory.getItemCountById(105002));
        assertEquals(1, player.inventory.grants.size());
        assertEquals(105005, player.inventory.grants.get(0).getItemId());
        assertEquals(1, player.inventory.grants.get(0).getCount());
        assertEquals(0, player.getTransmuterState().progress);
        assertEquals(1, player.getTransmuterState().extractedThisCycle);
        assertEquals(1, player.saves);
    }

    @Test
    void fullOutputInventoryDoesNotChargeOrAdvanceProgress() throws Exception {
        player.inventory.materials.put(105002, 200);
        player.inventory.acceptOutput = false;
        assertNotEquals(0, extract(request().addItemList(material(200))).getRetcode());
        assertEquals(200, player.inventory.getItemCountById(105002));
        assertEquals(0, player.getTransmuterState().progress);
        assertEquals(0, player.getTransmuterState().extractedThisCycle);
        assertEquals(0, player.saves);
    }

    @Test
    void insufficientMaterialsDoNotProduceFreeProgress() throws Exception {
        player.inventory.materials.put(105002, 1);
        assertNotEquals(0, extract(request().addItemList(material(2))).getRetcode());
        assertEquals(1, player.inventory.getItemCountById(105002));
        assertEquals(0, player.getTransmuterState().progress);
        assertTrue(player.inventory.grants.isEmpty());
    }

    @Test
    void emptyRequestDoesNotProduceFreeProgress() throws Exception {
        assertNotEquals(0, extract(request()).getRetcode());
        assertEquals(0, player.getTransmuterState().progress);
        assertEquals(0, player.saves);
    }

    @Test
    void fractionalProgressPersistsWithoutProducingAnItem() throws Exception {
        player.inventory.materials.put(105002, 3);
        assertEquals(0, extract(request().addItemList(material(3))).getRetcode());
        assertEquals(1.5, player.getTransmuterState().progress);
        assertTrue(player.inventory.grants.isEmpty());
        assertEquals(1, player.saves);
    }

    @Test
    void duplicateOrLockedRelicsAreRejectedBeforeAnyConsumption() throws Exception {
        var relic = new GameItem();
        relic.setItemData(
                new Gson()
                        .fromJson(
                                "{\"id\":75554,\"itemType\":\"ITEM_RELIQUARY\",\"rankLevel\":5}",
                                ItemData.class));
        relic.setLevel(21);
        player.inventory.relics.put(99L, relic);
        assertNotEquals(
                0,
                extract(request().addReliquaryGuidList(99).addReliquaryGuidList(99)).getRetcode());
        assertSame(relic, player.inventory.getItemByGuid(99));
        player.sent.clear();
        relic.setLocked(true);
        assertNotEquals(0, extract(request().addReliquaryGuidList(99)).getRetcode());
        assertSame(relic, player.inventory.getItemByGuid(99));
        assertEquals(0, player.saves);
    }

    @Test
    void staleScheduleDoesNotChargeMaterials() throws Exception {
        player.inventory.materials.put(105002, 200);
        assertNotEquals(
                0, extract(request().setScheduleId(700).addItemList(material(200))).getRetcode());
        assertEquals(200, player.inventory.getItemCountById(105002));
    }

    @Test
    void filterSnapshotRestoresMultipleSetsUnderTheSameKey() throws Exception {
        var data =
                ReliquaryFilterStateSaveNotify.newBuilder()
                        .addReliquaryFilterStateMap(
                                Uint32Pair.newBuilder().setKey(3).setValue(15001))
                        .addReliquaryFilterStateMap(
                                Uint32Pair.newBuilder().setKey(3).setValue(15002))
                        .build();
        new ReliquarySystem(player, 0).handle(842, data.toByteArray());
        assertEquals(1, player.saves);
        var gson = new Gson();
        player.state = gson.fromJson(gson.toJson(player.state), ReliquaryState.class);
        player.sent.clear();
        new ReliquarySystem(player, 0).sendFilter();
        assertEquals(6229, player.sent.get(0).getOpcode());
        assertEquals(
                data.getReliquaryFilterStateMapList(),
                ReliquaryFilterStateNotify.parseFrom(player.sent.get(0).getData())
                        .getReliquaryFilterStateMapList());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingPlayer extends Player {
        RecordingInventory inventory;
        ReliquaryState state;
        List<BasePacket> sent;
        int saves;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public ReliquaryState getReliquaryState() {
            return state;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            sent.add(packet);
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingInventory extends Inventory {
        Map<Integer, Integer> materials;
        Map<Long, GameItem> relics;
        List<GameItem> grants;
        boolean acceptOutput;

        private RecordingInventory() {
            super(null);
        }

        @Override
        public GameItem getItemByGuid(long guid) {
            return relics.get(guid);
        }

        @Override
        public int getItemCountById(int id) {
            return materials.getOrDefault(id, 0);
        }

        @Override
        public synchronized boolean payItems(Iterable<ItemParamData> items) {
            for (var item : items)
                if (getItemCountById(item.getId()) < item.getCount()) return false;
            for (var item : items) materials.merge(item.getId(), -item.getCount(), Integer::sum);
            return true;
        }

        @Override
        public synchronized boolean removeItem(GameItem item) {
            return relics.values().remove(item);
        }

        @Override
        public synchronized InventoryAddResult addItems(
                Collection<GameItem> items,
                ActionReason reason,
                InventoryAddPolicy policy,
                BooleanSupplier authorize,
                Runnable confirmed) {
            if (!acceptOutput)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            if (!authorize.getAsBoolean())
                return InventoryAddResult.rejected(
                        items, InventoryAddResult.Status.AUTHORIZATION_FAILED);
            grants.addAll(items);
            confirmed.run();
            return new InventoryAddResult(
                    items.stream()
                            .map(
                                    item ->
                                            new InventoryAddResult.Entry(
                                                    item.getItemId(),
                                                    item.getCount(),
                                                    item.getCount(),
                                                    InventoryAddResult.Status.ADDED))
                            .toList());
        }
    }
}
