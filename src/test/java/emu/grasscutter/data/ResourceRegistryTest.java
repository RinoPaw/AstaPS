package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceRegistryTest {
    @Test
    void onlyExplicitCompleteNamesResolve() {
        var registry = new ResourceRegistry(Map.of(
                "genshin.avatar_ascension.hoarfrost_core", 113010,
                "genshin.currency.mora", 202,
                "genshin.artifact.instructor.flower.3.1", 60311));
        assertEquals(113010, registry.resolve("genshin.avatar_ascension.hoarfrost_core"));
        assertEquals(202, registry.resolve("genshin.currency.mora"));
        assertEquals(60311, registry.resolve("genshin.artifact.instructor.flower.3.1"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.avatar_ascension"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.currency.Mora"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("113010"));
    }

    @Test
    void constructionRejectsIncompleteKeysAndInvalidIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRegistry(Map.of("genshin.artifact", 10005)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRegistry(Map.of("genshin.currency.mora", -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRegistry(Map.of("genshin.currency.mora", 0)));
    }
}
