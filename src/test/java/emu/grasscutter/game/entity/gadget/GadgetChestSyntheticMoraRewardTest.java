package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.drop.BaseDropData;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.game.systems.TestPlayers.TestPlayer;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;

@org.junit.jupiter.api.extension.ExtendWith(ServerResourceFixture.class)
final class GadgetChestSyntheticMoraRewardTest {
    private static final int GADGET_ID = 99002060;
    private static final int DROP_ID = 99002061;
    private static final String TAG = "摩拉石箱枫丹";
    private boolean oldScriptMode;
    private GadgetData priorGadget;
    private ItemData priorMora;
    private DropTableData priorDrop;
    private TestPlayer player;
    private GadgetChest chest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws ReflectiveOperationException {
        oldScriptMode = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        GameServer server = TestPlayers.server();
        var drops = new DropSystem(server);
        Field dropField = GameServer.class.getDeclaredField("dropSystem");
        dropField.setAccessible(true);
        dropField.set(server, drops);
        Field rewardField = DropSystem.class.getDeclaredField("chestReward");
        rewardField.setAccessible(true);
        Map<String, List<BaseDropData>> tags = (Map<String, List<BaseDropData>>) rewardField.get(drops);
        tags.put(TAG, List.of(JsonUtils.decode(
                "{\"index\":\"摩拉石箱枫丹\",\"minLevel\":1,\"dropId\":99002061,\"dropCount\":1}",
                BaseDropData.class)));

        player = new TestPlayer(server, 990060);
        World world = new World(player);
        Scene scene = new Scene(world, JsonUtils.decode("{\"id\":3}", SceneData.class));
        player.setWorld(world);
        player.setScene(scene);
        scene.getPlayers().add(player);
        priorGadget = GameData.getGadgetDataMap().put(GADGET_ID,
                JsonUtils.decode("{\"id\":99002060,\"type\":\"Chest\"}", GadgetData.class));
        priorMora = GameData.getItemDataMap().put(202,
                JsonUtils.decode("{\"id\":202,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
        priorDrop = GameData.getDropTableDataMap().remove(DROP_ID);

        var gadget = new EntityGadget(scene, GADGET_ID, new Position(player.getPosition()));
        gadget.setGroupId(910720001); // Synthetic Fontaine source ID 20001.
        gadget.setConfigId(20001);
        var meta = new SceneGadget();
        meta.config_id = 20001;
        meta.gadget_id = GADGET_ID;
        meta.drop_tag = TAG;
        gadget.setMetaGadget(meta);
        chest = new GadgetChest(gadget);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
    }

    @AfterEach
    void cleanup() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = oldScriptMode;
        restore(GameData.getGadgetDataMap(), GADGET_ID, priorGadget);
        restore(GameData.getItemDataMap(), 202, priorMora);
        restore(GameData.getDropTableDataMap(), DROP_ID, priorDrop);
    }

    private static <T> void restore(it.unimi.dsi.fastutil.ints.Int2ObjectMap<T> map, int id, T old) {
        if (old == null) map.remove(id);
        else map.put(id, old);
    }

    private static DropTableData table(String entries) {
        return JsonUtils.decode(
                "{\"id\":99002061,\"randomType\":1,\"fallToGround\":false,\"dropVec\":" + entries + "}",
                DropTableData.class);
    }

    private boolean grant() throws ReflectiveOperationException {
        // Exercise the real payout decision, without persisting synthetic claim files.
        Method method = GadgetChest.class.getDeclaredMethod("grantOrdinaryChestLoot", Player.class);
        method.setAccessible(true);
        return (boolean) method.invoke(chest, player);
    }

    @Test
    void missingPoolDoesNotAwardOrMarkClaimed() throws ReflectiveOperationException {
        int before = player.getMora();
        int primogems = player.getPrimogems();
        assertFalse(grant());
        assertEquals(before, player.getMora());
        assertEquals(primogems, player.getPrimogems());

        GameData.getDropTableDataMap().put(DROP_ID, table(
                "[{\"itemId\":202,\"countRange\":\"10000\",\"weight\":10000}]"));
        assertTrue(grant());
        assertEquals(before + 10000, player.getMora());
        assertEquals(primogems, player.getPrimogems());
    }

    @Test
    void emptyRootIsRejected() throws ReflectiveOperationException {
        GameData.getDropTableDataMap().put(DROP_ID, table("[]"));
        assertFalse(grant());
    }

    @Test
    void rejectedInventoryGrantIsRejected() throws ReflectiveOperationException {
        GameData.getDropTableDataMap().put(DROP_ID, table(
                "[{\"itemId\":202,\"countRange\":\"2147483647\",\"weight\":10000}]"));
        player.setMora(1);
        assertFalse(grant());
        assertEquals(1, player.getMora());
    }
}
