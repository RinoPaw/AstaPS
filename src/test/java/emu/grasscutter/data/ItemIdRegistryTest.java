package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemIdRegistryTest {
    @Test
    void resourceIdUsesThreeDottedParts() {
        var id = ResourceId.parse("genshin.item.mora");
        assertEquals("genshin.item.mora", id.toString());
        assertEquals("--genshin-item-mora", id.toCssProperty());
        assertEquals(id, ResourceId.fromCssProperty("--genshin-item-mora"));
        assertEquals(new ResourceId("my_mod", "item", "my-item"),
                ResourceId.fromCssProperty("--my_mod-item-my-item"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin.item"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("Genshin.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin.item.mora.more"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceId.fromCssProperty("--genshin--item--mora"));
    }

    @Test
    void itemIdsAreStrictlyTypedAndExistInLoadedResources() {
        var names = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        assertEquals(202, names.resolve("genshin.item.mora"));
        assertEquals(104003, names.resolve("genshin.item.heros_wit"));
        assertEquals(202, names.resolve("202"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.id_202"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.id_104003"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.id_999999"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.avatar.amber"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("astaps.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.item.primogem"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("123456"));
    }

    @Test
    void nativeArtifactVariantsUseSetSlotRarityAndInitialCount() {
        var names = new ItemIdRegistry(Map.of(
                55311, "Berserker 3 Star Goblet",
                55312, "Berserker 3 Star Goblet 2",
                55341, "Berserker 3 Star Flower",
                60110, "Adventurer 1 Star Goblet"));
        assertEquals("artifact:berserker:goblet:3:1", names.describe(55311).key());
        assertEquals("artifact:berserker:goblet:3:2", names.describe(55312).key());
        assertEquals("artifact:berserker:flower:3:1", names.describe(55341).key());
        assertEquals("artifact:adventurer:goblet:1:0", names.describe(60110).key());
        assertEquals(55311, names.resolve("artifact:berserker:goblet:3:1"));
        assertEquals(55312, names.resolve("artifact:berserker:goblet:3:2"));
        assertEquals(60110, names.resolve("artifact:adventurer:goblet:1:0"));
        assertThrows(IllegalArgumentException.class, () ->
                names.resolve("genshin.item.artifact_berserker_flower_three_star_one_initial_substats"));
        assertThrows(IllegalArgumentException.class, () ->
                names.resolve("artifact:berserker:goblet:3:5"));
        assertThrows(IllegalArgumentException.class, () ->
                names.resolve("artifact:berserker:goblet:3"));
        assertEquals(135, ItemIdRegistry.chestDropNames().values().stream()
                .filter(name -> name.startsWith("artifact:")).count());
        assertEquals(135, ItemIdRegistry.chestDropNames().values().stream()
                .filter(ItemIdRegistry::isArtifactReference).distinct().count());
    }

    @Test
    void identicalEnglishNamesDoNotSilentlySelectOneItem() {
        var names = new ItemIdRegistry(Map.of(501, "Duplicated", 502, "Duplicated"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.duplicated"));
        assertEquals(501, names.resolve("501"));
        assertEquals(502, names.resolve("502"));
    }
}
