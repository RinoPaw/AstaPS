package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.reward.ChestRewardConfig.Kind;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ChestRewardOverlayTest {
    private static String defaults() {
        return """
                [chest."kind=common"]
                adventureExp = 30
                primogems = 2
                [chest."kind=exquisite"]
                adventureExp = 30
                primogems = 5
                [chest."kind=precious"]
                adventureExp = 30
                primogems = 10
                [chest."kind=luxurious"]
                adventureExp = 30
                primogems = 10
                """;
    }

    private static GameItem nativeItem(int id, int count) {
        var item = new GameItem();
        item.setItemId(id);
        item.setCount(count);
        return item;
    }

    private static Map<Integer, Integer> values(List<ItemParamData> items) {
        return items.stream().collect(
                Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    @Test
    void nativeCurrenciesAreReplacedOnlyWhenConfigOwnsTheId() {
        var rules = ChestRewardConfig.parse(defaults());
        var match = rules.resolve(Kind.COMMON, 3, 1, 0);
        var nativeRoll = List.of(
                nativeItem(102, 80), nativeItem(201, 99),
                nativeItem(202, 1500), nativeItem(305, 3),
                nativeItem(104001, 2));

        var plan = ChestRewardOverlay.plan(match, nativeRoll);
        assertEquals(Map.of(102, 30, 201, 2), values(plan.configuredItems()));
        assertEquals(List.of(202, 305, 104001),
                plan.nativeItems().stream().map(GameItem::getItemId).toList());
        assertEquals(List.of(1500, 3, 2),
                plan.nativeItems().stream().map(GameItem::getCount).toList());
        assertEquals(99, nativeRoll.get(1).getCount(), "Planning cannot mutate the roll");
        assertNotSame(nativeRoll.get(2), plan.nativeItems().get(0));
    }

    @Test
    void zeroGemOverrideMustAlsoSuppressNativeGems() {
        var config = ChestRewardConfig.parse(defaults()
                + """
                  [chest."kind=common,area=3:1"]
                  primogems = 0
                  """);
        var match = config.resolve(Kind.COMMON, 3, 1, 107);
        assertTrue(match.ownedItemIds().contains(201));
        assertFalse(values(match.rewards()).containsKey(201));

        var plan = ChestRewardOverlay.plan(match,
                List.of(nativeItem(201, 5), nativeItem(202, 500)));
        assertEquals(List.of(202), plan.nativeItems().stream().map(GameItem::getItemId).toList());
        assertEquals(Map.of(102, 30), values(plan.configuredItems()));
    }

    @Test
    void configuredExtraItemCanReplaceOrClearNativeMaterial() {
        var config = ChestRewardConfig.parse(defaults()
                .replace("primogems = 2", "primogems = 2\nitems = [{ id = 104001, count = 5 }]")
                + """
                  [chest."kind=common,area=3:1"]
                  items = [{ id = 104001, count = 0 }, { id = 104002, count = 3 }]
                  """);
        var result = ChestRewardOverlay.plan(
                config.resolve(Kind.COMMON, 3, 1, 0),
                List.of(nativeItem(104001, 7), nativeItem(104002, 7),
                        nativeItem(104003, 2)));
        assertEquals(List.of(104003), result.nativeItems().stream().map(GameItem::getItemId).toList());
        assertEquals(Map.of(102, 30, 201, 2, 104002, 3), values(result.configuredItems()));
    }

    @Test
    void missingConfiguredFieldDoesNotSilentlyEraseUnownedNativeMora() {
        var config = ChestRewardConfig.parse(defaults()
                + """
                  [chest."kind=exquisite,area=3:1"]
                  mora = 0
                  """);
        var inherited = config.resolve(Kind.EXQUISITE, 3, 2, 0);
        assertFalse(inherited.ownedItemIds().contains(202));
        assertEquals(1, ChestRewardOverlay.plan(
                inherited, List.of(nativeItem(202, 1800))).nativeItems().size());

        var clear = config.resolve(Kind.EXQUISITE, 3, 1, 0);
        assertTrue(clear.ownedItemIds().contains(202));
        assertTrue(ChestRewardOverlay.plan(
                clear, List.of(nativeItem(202, 1800))).nativeItems().isEmpty());
    }

    @Test
    void invalidNativeRollFailsBeforeProducingAnyPlan() {
        var match = ChestRewardConfig.parse(defaults()).resolve(Kind.COMMON, 3, 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardOverlay.plan(match, List.of(nativeItem(0, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardOverlay.plan(match, List.of(nativeItem(201, -1))));
        assertThrows(NullPointerException.class,
                () -> ChestRewardOverlay.plan(match, null));
        assertThrows(NullPointerException.class,
                () -> ChestRewardOverlay.plan(null, List.of()));
    }

    @Test
    void nativeLootRetainsRolledEquipmentState() {
        var item = nativeItem(123456, 1);
        item.setLevel(20);
        item.setMainPropId(60001);
        var plan = ChestRewardOverlay.plan(
                ChestRewardConfig.parse(defaults()).resolve(Kind.PRECIOUS, 3, 10, 0),
                List.of(item));
        assertEquals(20, plan.nativeItems().get(0).getLevel());
        assertEquals(60001, plan.nativeItems().get(0).getMainPropId());
        assertNotSame(item, plan.nativeItems().get(0));
    }
}
