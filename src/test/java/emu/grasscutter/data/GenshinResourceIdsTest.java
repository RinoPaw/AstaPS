package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.drop.ArtifactResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenshinResourceIdsTest {
    private static GenshinResourceIds registry() {
        var items = new ItemIdRegistry(Map.of(
                11101, "Dull Blade", 202, "Mora", 109406, "Recipe: Mora Meat"));
        return new GenshinResourceIds(items,
                Map.of(10000021, "Amber", 10000003, "Jean"),
                Map.of(100014, "Mora Meat", 100015, "Northern Smoked Chicken"));
    }

    @Test
    void nativeDomainsResolveWithoutPretendingEverythingIsAnItem() {
        var ids = registry();
        assertEquals(11101, ids.resolve("genshin.weapon.dull_blade"));
        assertEquals(202, ids.resolve("genshin.item.mora"));
        assertEquals(10000021, ids.resolve("genshin.avatar.amber"));
        assertEquals(100014, ids.resolve("genshin.recipe.mora_meat"));
        assertEquals(109406, ids.resolve("genshin.item.recipe_mora_meat"));
        assertNotEquals(ids.resolve("genshin.item.recipe_mora_meat"),
                ids.resolve("genshin.recipe.mora_meat"));
    }

    @Test
    void namesCannotCrossKindsOrSilentlyClaimAnotherObject() {
        var ids = registry();
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.item.dull_blade"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.weapon.mora"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.avatar.mora_meat"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.recipe.amber"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.avatar.id_10000021"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.recipe.id_100014"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("astaps.avatar.amber"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.monster.hilichurl"));
        assertEquals(10005, ArtifactResolver.nativeSetId("berserker"));
        assertThrows(IllegalArgumentException.class, () ->
                ArtifactResolver.nativeSetId("unknown_artifact"));
    }

    @Test
    void ambiguousNativeNamesAreErrorsNotFirstMatchWins() {
        var ids = new GenshinResourceIds(new ItemIdRegistry(Map.of(202, "Mora")),
                Map.of(1, "Traveler", 2, "Traveler"),
                Map.of(100, "Mystery Dish", 101, "Mystery Dish"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.avatar.traveler"));
        assertThrows(IllegalArgumentException.class, () -> ids.resolve("genshin.recipe.mystery_dish"));
    }

    @Test
    void recipeNameComesFromUnmodifiedFoodNotQualityVariants() {
        assertEquals("Mora Meat", GenshinResourceIds.unmodifiedDishName(
                List.of("Suspicious Mora Meat", "Mora Meat", "Delicious Mora Meat")));
        assertNull(GenshinResourceIds.unmodifiedDishName(
                List.of("Suspicious Mora Meat", "Delicious Mora Meat")));
        assertNull(GenshinResourceIds.unmodifiedDishName(List.of("Mora Meat", "Sweet Madame")));
    }
}
