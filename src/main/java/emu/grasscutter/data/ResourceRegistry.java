package emu.grasscutter.data;

import emu.grasscutter.game.drop.ArtifactResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

    /** Build the authoritative dictionary from explicit source entries and native artifact variants. */
    public static ResourceRegistry fromGameResources(ArtifactResolver artifacts) throws IOException {
        var names = new LinkedHashMap<String, Integer>();
        try (var stream = ResourceRegistry.class.getResourceAsStream("/rewards/registry.tsv")) {
            if (stream == null) throw new IOException("Missing bundled rewards/registry.tsv");
            String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] columns = line.split("\\t", -1);
                if (columns.length != 2 || !columns[1].matches("[1-9][0-9]*")) {
                    throw new IllegalArgumentException("Malformed registry.tsv line " + (i + 1));
                }
                int id = Integer.parseInt(columns[1]);
                // Native virtual rewards are handled by Player, not inventory ItemData.
                if (id != 101 && id != 102 && id != 105
                        && !GameData.getItemDataMap().containsKey(id)) {
                    throw new IllegalArgumentException("Unknown native ID at registry.tsv:"
                            + (i + 1) + ": " + id);
                }
                if (names.putIfAbsent(columns[0], id) != null) {
                    throw new IllegalArgumentException("Duplicate registry resource at line "
                            + (i + 1) + ": " + columns[0]);
                }
            }
        }
        for (var entry : artifacts.resourceIds().entrySet()) {
            if (names.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate artifact resource: " + entry.getKey());
            }
        }
        return new ResourceRegistry(names);
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
