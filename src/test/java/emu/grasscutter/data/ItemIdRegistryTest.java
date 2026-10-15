package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemIdRegistryTest {
    @Test
    void resourceIdRequiresThreeLowercaseParts() {
        assertEquals("genshin:item:mora", ResourceId.parse("genshin:item:mora").toString());
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin:mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("Genshin:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin:item:mora:more"));
    }

    @Test
    void itemIdsAreStrictlyTypedAndExistInLoadedResources() {
        var names = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        assertEquals(202, names.resolve("genshin:item:mora"));
        assertEquals(104003, names.resolve("genshin:item:heros_wit"));
        assertEquals(202, names.resolve("202"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin:avatar:amber"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("astaps:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin:item:primogem"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("123456"));
    }

    @Test
    void identicalEnglishNamesDoNotSilentlySelectOneItem() {
        var names = new ItemIdRegistry(Map.of(501, "Duplicated", 502, "Duplicated"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin:item:duplicated"));
        assertEquals(501, names.resolve("501"));
        assertEquals(502, names.resolve("502"));
    }
}
