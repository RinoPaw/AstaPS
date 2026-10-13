package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.game.systems.TestPlayers.TestPlayer;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Field;
import org.junit.jupiter.api.*;

@org.junit.jupiter.api.extension.ExtendWith(ServerResourceFixture.class)
final class GadgetChestNativeRewardTest {
    private static final int GADGET_ID = 99002010;
    private static final int DROP_ID = 99002011;
    private boolean oldScriptMode;
    private GadgetData priorGadget;
    private ItemData priorMora;
    private DropTableData priorDrop;
    private TestPlayer player;
    private EntityGadget gadget;
    private GadgetChest chest;

    @BeforeEach
    void setup() throws ReflectiveOperationException {
        oldScriptMode = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        GameServer server = TestPlayers.server();
        Field dropField = GameServer.class.getDeclaredField("dropSystem");
        dropField.setAccessible(true);
        dropField.set(server, new DropSystem(server));

        player = new TestPlayer(server, 990010);
        var world = new World(player);
        var scene = new Scene(world, JsonUtils.decode("{\\"id\\":3}", SceneData.class));
        player.setWorld(world);
        player.setScene(scene);
        scene.getPlayers().add(player);

        priorGadget = GameData.getGadgetDataMap().put(GADGET_ID,
                JsonUtils.decode("{\\"id\\":99002010,\\"type\\":\\"Chest\\"}", GadgetData.class));
        priorMora = GameData.getItemDataMap().put(202,
                JsonUtils.decode("{\\"id\\":202,\\"itemType\\":\\"ITEM_VIRTUAL\\"}", ItemData.class));
        priorDrop = GameData.getDropTableDataMap().remove(DROP_ID);
        gadget = new EntityGadget(scene, GADGET_ID, new Position(player.getPosition()));
        var meta = new SceneGadget();
        meta.chest_drop_id = DROP_ID;
        meta.drop_count = 1;
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

    private static DropTableData table(String vector) {
        return JsonUtils.decode(
                "{\\"id\\":99002011,\\"randomType\\":1,\\"fallToGround\\":false,\\"dropVec\\":" + vector + "}",
                DropTableData.class);
    }

    private void claim() {
        chest.onInteract(player, GadgetInteractReq.newBuilder()
                .setOpType(InterOpType.InterOpType_INTER_OP_FINISH).build());
    }

    @Test
    void missingNativeDropRemainsOpenThenGrantsOnceWhenDataAppears() {
        int before = player.getMora();
        claim();
        assertNotEquals(102, gadget.getState());
        assertEquals(before, player.getMora());

        GameData.getDropTableDataMap().put(DROP_ID,
                table("[{\\"itemId\\":202,\\"countRange\\":\\"100\\",\\"weight\\":10000}]"));
        claim();
        assertEquals(102, gadget.getState());
        assertEquals(before + 100, player.getMora());

        claim();
        assertEquals(before + 100, player.getMora());
    }

    @Test
    void emptyDropRootDoesNotConsumeChest() {
        GameData.getDropTableDataMap().put(DROP_ID, table("[]"));
        claim();
        assertNotEquals(102, gadget.getState());
    }

    @Test
    void rejectedBackpackGrantDoesNotConsumeChest() {
        GameData.getDropTableDataMap().put(DROP_ID,
                table("[{\\"itemId\\":202,\\"countRange\\":\\"2147483647\\",\\"weight\\":10000}]"));
        player.setMora(1);
        claim();
        assertEquals(1, player.getMora());
        assertNotEquals(102, gadget.getState());
    }
}
