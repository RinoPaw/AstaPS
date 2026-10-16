package emu.grasscutter.data;

/** A dotted resource identity, including typed artifact subfields. */
public record ResourceId(String namespace, String type, String name) {
    public ResourceId {
        if (!validHead(namespace) || !validHead(type) || !validName(namespace, type, name)) {
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

    /** CSS custom property: genshin.currency.mora -> --genshin-currency-mora. */
    public static ResourceId fromCssProperty(String property) {
        if (!property.startsWith("--")) {
            throw new IllegalArgumentException("Expected a CSS custom property: " + property);
        }
        String[] parts = property.substring(2).split("-", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected --namespace-type-name: " + property);
        }
        // CSS custom property names cannot contain a dot. Preserve the
        // semantic sigil.<region> suffix with a CSS-safe hyphen instead.
        String name = parts[2];
        if ("genshin".equals(parts[0]) && "currency".equals(parts[1])
                && name.matches("sigil-[a-z][a-z0-9_]*")) {
            name = "sigil." + name.substring("sigil-".length());
        }
        return new ResourceId(parts[0], parts[1], name);
    }

    public String toCssProperty() {
        String cssName = isQualifiedSigil(namespace, type, name)
                ? name.replace('.', '-') : name;
        return "--" + namespace + "-" + type + "-" + cssName;
    }

    private static boolean validHead(String part) {
        return part != null && part.matches("[a-z][a-z0-9_]*");
    }

    private static boolean isQualifiedSigil(String namespace, String type, String name) {
        return "genshin".equals(namespace) && "currency".equals(type)
                && name != null && name.matches("sigil\\.[a-z][a-z0-9_]*");
    }

    private static boolean validName(String namespace, String type, String name) {
        if (isQualifiedSigil(namespace, type, name)) return true;
        if ("artifact".equals(type)) {
            return name != null && name.matches("[a-z][a-z0-9_]*");
        }
        return name != null && name.matches("[\\p{L}_][\\p{L}\\p{N}_-]*");
    }

    @Override
    public String toString() {
        return namespace + "." + type + "." + name;
    }
}
