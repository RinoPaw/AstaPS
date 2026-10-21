package emu.grasscutter.data;

import java.util.Map;
import java.util.Objects;

/**
 * An explicitly authored resource-name dictionary.
 *
 * <p>Entries supply both the name and native ID. No display text, native item
 * category, usage metadata, aliases or implicit naming rules participate.
 */
public final class ResourceRegistry {
    private final Map<String, Integer> ids;

    public ResourceRegistry(Map<String, Integer> entries) {
        Objects.requireNonNull(entries, "entries");
        for (var entry : entries.entrySet()) {
            String name = entry.getKey();
            ResourceId path = ResourceId.parse(name);
            if (path.size() < 3) {
                throw new IllegalArgumentException("Incomplete resource name: " + name);
            }
            Integer id = entry.getValue();
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Invalid native ID for " + name + ": " + id);
            }
        }
        ids = Map.copyOf(entries);
    }

    public int resolve(String name) {
        Integer id = ids.get(name);
        if (id == null) {
            throw new IllegalArgumentException("Unknown resource: " + name);
        }
        return id;
    }

    public Map<String, Integer> entries() {
        return ids;
    }
}
