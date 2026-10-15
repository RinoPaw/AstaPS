package emu.grasscutter.data;

/** A dotted resource identity, including typed artifact subfields. */
public record ResourceId(String namespace, String type, String name) {
    public ResourceId {
        if (!validHead(namespace) || !validHead(type) || !validName(type, name)) {
            throw new IllegalArgumentException("Invalid resource ID: " + namespace + "." + type + "." + name);
        }
    }

    public static ResourceId parse(String value) {
        String[] parts = value.split("\\.", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected namespace.type.name: " + value);
        }
        return new ResourceId(parts[0], parts[1], parts[2]);
    }

    /** CSS custom property: genshin.item.mora -> --genshin-item-mora. */
    public static ResourceId fromCssProperty(String property) {
        if (!property.startsWith("--")) {
            throw new IllegalArgumentException("Expected a CSS custom property: " + property);
        }
        String[] parts = property.substring(2).split("-", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected --namespace-type-name: " + property);
        }
        return new ResourceId(parts[0], parts[1], parts[2]);
    }

    public String toCssProperty() {
        return "--" + namespace + "-" + type + "-" + name;
    }

    private static boolean validHead(String part) {
        return part != null && part.matches("[a-z][a-z0-9_]*");
    }

    private static boolean validName(String type, String part) {
        if ("artifact".equals(type)) {
            return part != null && part.matches(
                    "[a-z][a-z0-9_]*\\.(flower|plume|sands|goblet|circlet)\\.[1-5]\\.[0-4]");
        }
        return part != null && part.matches("[\\p{L}_][\\p{L}\\p{N}_-]*");
    }

    @Override
    public String toString() {
        return namespace + "." + type + "." + name;
    }
}
