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

    private static boolean valid(String part) {
        return part != null && part.matches("[a-z][a-z0-9_-]*");
    }

    @Override
    public String toString() {
        return namespace + ":" + type + ":" + name;
    }
}
