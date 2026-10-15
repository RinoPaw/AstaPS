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
        assertEquals(202, names.resolve("genshin.item.id_202"));
        assertEquals(104003, names.resolve("genshin.item.id_104003"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.id_999999"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.avatar.amber"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("astaps.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.item.primogem"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("123456"));
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
