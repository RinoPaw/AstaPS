package emu.grasscutter.data;

/** An explicit resource identity: namespace:type:name. */
public record ResourceId(String namespace, String type, String name) {
    public ResourceId {
        if (!valid(namespace) || !valid(type) || !valid(name)) {
            throw new IllegalArgumentException("Invalid resource ID components");
        }
    }

    public static ResourceId parse(String value) {
        if (value == null) throw new IllegalArgumentException("Missing resource ID");
        String[] parts = value.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected namespace:type:name: " + value);
        }
        return new ResourceId(parts[0], parts[1], parts[2]);
    }

    /**
     * A CSS custom property encodes namespace:type:name with double hyphens.
     * For example, genshin:item:mora is --genshin--item--mora.
     */
    public static ResourceId fromCssProperty(String property) {
        if (property == null || !property.startsWith("--")) {
            throw new IllegalArgumentException("Expected a CSS custom property: " + property);
        }
        String[] parts = property.substring(2).split("--", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected --namespace--type--name: " + property);
        }
        return new ResourceId(parts[0], parts[1], parts[2]);
    }

    public String toCssProperty() {
        if (namespace.contains("--") || type.contains("--") || name.contains("--")) {
            throw new IllegalArgumentException("Resource ID cannot be represented in CSS: " + this);
        }
        return "--" + namespace + "--" + type + "--" + name;
    }

    private static boolean valid(String part) {
        return part != null && part.matches("[a-z][a-z0-9_-]*");
    }

    @Override
    public String toString() {
        return namespace + ":" + type + ":" + name;
    }
}
