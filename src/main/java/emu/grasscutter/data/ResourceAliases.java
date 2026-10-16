package emu.grasscutter.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A namespace-level alias resolver. Aliases may point to whole resource
 * families: unmatched trailing path segments are preserved. Longest prefix
 * wins, and transitive cycles are rejected rather than silently truncated.
 */
public final class ResourceAliases {
    private final Map<ResourceId, ResourceId> aliases;

    public ResourceAliases(Map<ResourceId, ResourceId> aliases) {
        this.aliases = Map.copyOf(aliases);
        for (var entry : aliases.entrySet()) {
            if (entry.getKey().equals(entry.getValue())) {
                throw new IllegalArgumentException("Self-referential resource alias: " + entry.getKey());
            }
            resolve(entry.getKey());
        }
    }

    public ResourceId resolve(ResourceId input) {
        var path = input;
        var seen = new HashSet<ResourceId>();
        while (true) {
            if (!seen.add(path)) {
                throw new IllegalArgumentException("Cyclic resource alias: " + input);
            }
            ResourceId best = null;
            for (var candidate : aliases.keySet()) {
                if (path.startsWith(candidate)
                        && (best == null || candidate.size() > best.size())) {
                    best = candidate;
                }
            }
            if (best == null) return path;
            path = path.replacePrefix(best, aliases.get(best));
        }
    }

    public ResourceId resolve(String input) {
        return resolve(ResourceId.parse(input));
    }

    /**
     * Genshin's seven elemental ascension gem families and the historical
     * flat item slugs are registered in the same namespace alias service.
     */
    public static final ResourceAliases GENSHIN = genshin();

    private static ResourceAliases genshin() {
        var entries = new LinkedHashMap<ResourceId, ResourceId>();
        String[][] families = {
            {"anemo", "vayuda_turquoise"},
            {"geo", "prithiva_topaz"},
            {"electro", "vajrada_amethyst"},
            {"dendro", "nagadus_emerald"},
            {"hydro", "varunada_lazurite"},
            {"pyro", "agnidus_agate"},
            {"cryo", "shivada_jade"}
        };
        for (String[] family : families) {
            var canonical = ResourceId.of("genshin", "avatar_ascension", family[1]);
            entries.put(ResourceId.of("genshin", "avatar_ascension", family[0]), canonical);
            for (String grade : new String[]{"sliver", "fragment", "chunk", "gemstone"}) {
                entries.put(ResourceId.of("genshin", "avatar_ascension",
                        family[1] + "_" + grade), canonical.append(grade));
            }
        }
        return new ResourceAliases(entries);
    }
}
