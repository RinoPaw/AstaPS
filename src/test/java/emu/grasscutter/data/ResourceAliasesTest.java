package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceAliasesTest {
    @Test
    void canonicalGemFamilyAliasesApplyToAnyRarity() {
        var a = ResourceAliases.GENSHIN;
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.sliver",
                a.resolve("genshin.avatar_ascension.anemo.sliver").toString());
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.gemstone",
                a.resolve("genshin.avatar_ascension.anemo.gemstone").toString());
        assertEquals("genshin.avatar_ascension.vajrada_amethyst.fragment",
                a.resolve("genshin.avatar_ascension.electro.fragment").toString());
        assertEquals("genshin.avatar_ascension.vayuda_turquoise_chunk",
                a.resolve("genshin.avatar_ascension.vayuda_turquoise_chunk").toString());
        assertEquals("genshin.avatar_ascension.vayuda_turquoise",
                a.resolve("genshin.avatar_ascension.anemo").toString());
        assertEquals("genshin.weapon.dull_blade",
                a.resolve("genshin.weapon.dull_blade").toString());
    }

    @Test
    void longestPrefixWinsAndAliasesAreTransitive() {
        var aliases = new ResourceAliases(Map.of(
                ResourceId.parse("mod.element"), ResourceId.parse("mod.canonical"),
                ResourceId.parse("mod.element.anemo"), ResourceId.parse("mod.wind"),
                ResourceId.parse("mod.wind"), ResourceId.parse("mod.vayuda")));
        assertEquals("mod.vayuda.sliver",
                aliases.resolve("mod.element.anemo.sliver").toString());
        assertEquals("mod.canonical.cryo",
                aliases.resolve("mod.element.cryo").toString());
    }

    @Test
    void cyclesAndSelfReferencesFailImmediately() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceAliases(Map.of(
                        ResourceId.parse("x.a"), ResourceId.parse("x.b"),
                        ResourceId.parse("x.b"), ResourceId.parse("x.a"))));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceAliases(Map.of(
                        ResourceId.parse("x.a"), ResourceId.parse("x.a"))));
    }

    @Test
    void inventoryGemResourcesShareOneCanonicalIdentity() {
        var items = new ItemIdRegistry(Map.of(
                8001, "Vayuda Turquoise Sliver",
                8002, "Vajrada Amethyst Gemstone"));
        assertEquals("genshin.avatar_ascension.vayuda_turquoise.sliver",
                items.describe(8001).key());
        assertEquals(8001, items.resolve("genshin.avatar_ascension.anemo.sliver"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.vayuda_turquoise_sliver"));
        assertEquals(8001, items.resolve("genshin.avatar_ascension.vayuda_turquoise.sliver"));
        assertEquals(8002, items.resolve("genshin.avatar_ascension.electro.gemstone"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.anemo"));
        assertThrows(IllegalArgumentException.class,
                () -> items.resolve("genshin.avatar_ascension.geo.sliver"));
    }
}
