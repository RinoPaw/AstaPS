package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RegionalSigilResolverTest {
    private static final Gson GSON = new Gson();

    private static CityData city(int id, int areaId) {
        var city = new CityData();
        city.setCityId(id);
        city.setAreaIdVec(List.of(areaId));
        return city;
    }

    private static WorldAreaData area(int id, int scene) {
        return GSON.fromJson(
                "{\"areaID1\":" + id + ",\"sceneID\":" + scene + "}",
                WorldAreaData.class);
    }

    @Test
    void ordinaryNativeAreasResolveThroughWorldAreaAndCityConfig() {
        assertEquals(305, RegionalSigilResolver.resolveArea(
                3, 1, area(1, 3), List.of(city(1, 1))));
        assertEquals(307, RegionalSigilResolver.resolveArea(
                3, 19, area(19, 3), List.of(city(2, 19))));
        assertEquals(304, RegionalSigilResolver.resolveArea(
                5, 200, area(200, 5), List.of(city(3, 200))));
    }

    @Test
    void specialScenesAndAmbiguousAreaOwnershipDoNotGuessACurrency() {
        assertThrows(IllegalArgumentException.class, () ->
                RegionalSigilResolver.resolveArea(
                        7, 210, area(210, 7), List.of(city(101, 210))));
        assertThrows(IllegalArgumentException.class, () ->
                RegionalSigilResolver.resolveArea(
                        4, 103, area(103, 4), List.of(city(100, 103))));
        assertThrows(IllegalArgumentException.class, () ->
                RegionalSigilResolver.resolveArea(
                        3, 19, area(19, 4), List.of(city(2, 19))));
        assertThrows(IllegalArgumentException.class, () ->
                RegionalSigilResolver.resolveArea(
                        3, 1, area(1, 3), List.of(city(1, 1), city(2, 1))));
        assertThrows(IllegalArgumentException.class, () ->
                RegionalSigilResolver.resolve(RewardContext.NONE));
    }

    @Test
    void sourceRegionsResolveWithoutSyntheticGroupOrCoordinates() {
        assertEquals(302, RegionalSigilResolver.resolve(
                new RewardContext(3, 0, "fontaine")));
        assertEquals(301, RegionalSigilResolver.resolve(
                new RewardContext(3, 0, "natlan")));
        assertEquals(303, RegionalSigilResolver.resolve(
                new RewardContext(3, 0, "sumeru")));
        assertEquals(308, RegionalSigilResolver.resolve(
                new RewardContext(3, 0, "nodkrai")));
        assertEquals(306, RegionalSigilResolver.resolve(
                new RewardContext(3, 0, "snezhnaya")));
        assertThrows(IllegalArgumentException.class, () ->
                RegionalSigilResolver.resolve(new RewardContext(3, 0, "unknown")));
    }

    @Test
    void cssAndYamlUseTheSameDeferredRegionalItem() {
        var ids = new ItemIdRegistry(Map.of(
                201, "Primogem", 202, "Mora", 305, "Anemo Sigil", 302, "Hydro Sigil"));
        assertEquals(ItemIdRegistry.REGIONAL_SIGIL, ids.resolve("sigil"));
        assertEquals(ItemIdRegistry.REGIONAL_SIGIL, ids.resolve("genshin.item.sigil"));

        var loot = YamlLootTables.parse("""
                seals:
                  - {reward: genshin.item.sigil, count: 2}
                currency:
                  - {reward: genshin.item.mora, count: 50}
                """, ids::resolve);
        var css = CssRewards.parse("""
                chest[kind="common"] { --loot: seals, currency; }
                chest[kind="exquisite"] {
                    --loot: seals;
                    --genshin-item-sigil: 4;
                    --genshin-item-primogem: 5;
                }
                """, ids, loot);
        var mondstadt = new RewardContext(3, 0, "mondstadt");
        var fontaine = new RewardContext(3, 0, "fontaine");
        assertEquals(Map.of(305, 2, 202, 50), RewardRoller.roll(
                css.resolve("chest", Map.of("kind", "common")),
                loot, new Random(1), mondstadt));
        assertEquals(Map.of(302, 2, 202, 50), RewardRoller.roll(
                css.resolve("chest", Map.of("kind", "common")),
                loot, new Random(1), fontaine));
        assertEquals(Map.of(302, 4, 201, 5), RewardRoller.roll(
                css.resolve("chest", Map.of("kind", "exquisite")),
                loot, new Random(1), fontaine));
        assertThrows(IllegalArgumentException.class, () ->
                RewardRoller.roll(css.resolve("chest", Map.of("kind", "common")),
                        loot, new Random(1)));
        assertThrows(IllegalArgumentException.class, () ->
                loot.rollItems("astaps.loot.seals", new Random(1)));
    }
}
