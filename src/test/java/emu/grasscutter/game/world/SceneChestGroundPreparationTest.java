package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class SceneChestGroundPreparationTest {
    private static final int GADGET_ID = 99002101;
    private GadgetData priorGadget;

    @BeforeEach
    void installGadgetDefinition() {
        priorGadget = GameData.getGadgetDataMap().put(
                GADGET_ID, JsonUtils.decode(
                        "{\"id\":99002101,\"type\":\"Chest\"}", GadgetData.class));
    }

    @AfterEach
    void restoreGadgetDefinition() {
        if (priorGadget == null) GameData.getGadgetDataMap().remove(GADGET_ID);
        else GameData.getGadgetDataMap().put(GADGET_ID, priorGadget);
    }

    private record Setup(Scene scene, EntityGadget chest, TestPlayers.TestPlayer player) {}

    private static Setup setup() {
        var server = TestPlayers.server();
        var player = new TestPlayers.TestPlayer(server, 990021);
        var world = new World(player);
        var scene = new Scene(world, JsonUtils.decode("{\"id\":3}", SceneData.class));
        player.setWorld(world);
        player.setScene(scene);
        scene.getPlayers().add(player);
        // Construction is enough for a born-from source; this gadget is not
        // inserted into the scene because no chest interaction is performed.
        var chest = new EntityGadget(scene, GADGET_ID, new Position(player.getPosition()));
        return new Setup(scene, chest, player);
    }

    private static GameItem item(int id, int gadgetId, int count) {
        ItemData definition = JsonUtils.decode(
                "{\"id\":" + id + ",\"itemType\":\"ITEM_MATERIAL\","
                        + "\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":99999,"
                        + "\"gadgetId\":" + gadgetId + "}",
                ItemData.class);
        return new GameItem(definition, count);
    }

    @Test
    void stagingCreatesUnpublishedOwnerOnlyPickupsWithOriginalItemProperties() {
        var setup = setup();
        var nativeMaterial = item(104001, 70310001, 5);
        nativeMaterial.setLevel(20);
        int before = setup.scene().getEntities().size();

        var staged = setup.scene().prepareChestGroundEntities(
                List.of(nativeMaterial), setup.chest(), setup.player());
        assertEquals(1, staged.size());
        assertEquals(before, setup.scene().getEntities().size());
        assertFalse(staged.getFirst().isShare());
        assertEquals(5, staged.getFirst().getItem().getCount());
        assertEquals(20, staged.getFirst().getItem().getLevel());
        assertNotSame(nativeMaterial, staged.getFirst().getItem());
        assertEquals(5, nativeMaterial.getCount());
        assertEquals(setup.player().getUid(), (int) (staged.getFirst().getGuid() >> 32));
    }

    @Test
    void wildlifeSplitExpandsBeforeAnyScenePublication() {
        var setup = setup();
        int before = setup.scene().getEntities().size();
        var staged = setup.scene().prepareChestGroundEntities(
                List.of(item(100061, 70310001, 3), item(104001, 70310002, 8)),
                setup.chest(), setup.player());
        assertEquals(4, staged.size());
        assertEquals(List.of(1, 1, 1, 8),
                staged.stream().map(it -> it.getItem().getCount()).toList());
        assertEquals(before, setup.scene().getEntities().size());
    }

    @Test
    void aBadLateItemRejectsTheEntireBatchBeforeEntityConstruction() {
        var setup = setup();
        int before = setup.scene().getEntities().size();
        assertThrows(IllegalArgumentException.class,
                () -> setup.scene().prepareChestGroundEntities(
                        List.of(item(104001, 70310001, 2),
                                item(104002, 0, 1)),
                        setup.chest(), setup.player()));
        assertEquals(before, setup.scene().getEntities().size());

        assertThrows(IllegalArgumentException.class,
                () -> setup.scene().prepareChestGroundEntities(
                        List.of(item(100061, 70310001, 10_001)),
                        setup.chest(), setup.player()));
        assertEquals(before, setup.scene().getEntities().size());
    }

    @Test
    void rejectsAbsentAndMismatchedSources() {
        var setup = setup();
        assertThrows(IllegalArgumentException.class,
                () -> setup.scene().prepareChestGroundEntities(
                        List.of(), setup.chest(), setup.player()));
        assertThrows(NullPointerException.class,
                () -> setup.scene().prepareChestGroundEntities(
                        null, setup.chest(), setup.player()));
        assertThrows(NullPointerException.class,
                () -> setup.scene().prepareChestGroundEntities(
                        List.of(item(104001, 70310001, 1)), null, setup.player()));
    }
}
