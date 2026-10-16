package emu.grasscutter.data;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, arbitrarily deep resource path. Path segments have no fixed
 * namespace/type/name positions; each consumer owns its own schema.
 */
public record ResourceId(List<String> segments) {
    public ResourceId {
        Objects.requireNonNull(segments, "segments");
        segments = List.copyOf(segments);
        if (segments.isEmpty() || !segments.get(0).matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid resource root: " + segments);
        }
        for (int i = 1; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (!segment.matches("[\\p{L}\\p{N}_-]+")) {
                throw new IllegalArgumentException("Invalid resource path segment: " + segment);
            }
        }
    }

    public static ResourceId of(String... segments) {
        return new ResourceId(Arrays.asList(segments));
    }

    public static ResourceId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing resource path");
        }
        return new ResourceId(Arrays.asList(value.split("\\.", -1)));
    }

    public int size() {
        return segments.size();
    }

    public String segment(int index) {
        return segments.get(index);
    }

    public boolean startsWith(ResourceId prefix) {
        return size() >= prefix.size()
                && segments.subList(0, prefix.size()).equals(prefix.segments);
    }

    public ResourceId append(String... suffix) {
        var joined = new java.util.ArrayList<>(segments);
        joined.addAll(Arrays.asList(suffix));
        return new ResourceId(joined);
    }

    public ResourceId replacePrefix(ResourceId original, ResourceId replacement) {
        if (!startsWith(original)) {
            throw new IllegalArgumentException(this + " is not under " + original);
        }
        var joined = new java.util.ArrayList<>(replacement.segments);
        joined.addAll(segments.subList(original.size(), size()));
        return new ResourceId(joined);
    }

    /** A resource's own schema, rather than this class, interprets path segments. */
    public String tail(int start) {
        if (start < 0 || start >= size()) {
            throw new IllegalArgumentException("Missing path from segment " + start + ": " + this);
        }
        return String.join(".", segments.subList(start, size()));
    }

    /**
     * Dotted names are the preferred reward CSS dialect. The historical
     * --namespace-category-name spelling remains readable during migration.
     */
    public static ResourceId fromCssProperty(String property) {
        if (!property.startsWith("--")) return parse(property);
        String[] parts = property.substring(2).split("-", 3);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()
                || parts[2].isEmpty()) {
            throw new IllegalArgumentException("Invalid legacy CSS resource: " + property);
        }
        if ("genshin".equals(parts[0]) && "currency".equals(parts[1])
                && parts[2].matches("sigil-[a-z][a-z0-9_]*")) {
            return of("genshin", "currency", "sigil", parts[2].substring(6));
        }
        return of(parts[0], parts[1], parts[2]);
    }

    /** Emit the unambiguous dotted spelling for new CSS resource declarations. */
    public String toCssProperty() {
        return toString();
    }

    @Override
    public String toString() {
        return String.join(".", segments);
    }
}
