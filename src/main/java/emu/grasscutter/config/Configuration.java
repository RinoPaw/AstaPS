package emu.grasscutter.config;

import emu.grasscutter.Grasscutter;

import emu.grasscutter.utils.FileUtils;
import java.nio.file.Path;

/** Path helpers and small configuration utilities. */
public final class Configuration {
    private Configuration() {}

    /*
     * Utilities
     */
    @Deprecated(forRemoval = true)
    public static String DATA() {
        return Grasscutter.getConfig().folderStructure.data;
    }

    @Deprecated(forRemoval = true)
    public static String DATA(String path) {
        return Path.of(Grasscutter.getConfig().folderStructure.data, path).toString();
    }

    @Deprecated(forRemoval = true)
    public static Path getResourcePath(String path) {
        return FileUtils.getResourcePath(path);
    }

    @Deprecated(forRemoval = true)
    public static String RESOURCE(String path) {
        return FileUtils.getResourcePath(path).toString();
    }

    @Deprecated(forRemoval = true)
    public static String PLUGIN() {
        return Grasscutter.getConfig().folderStructure.plugins;
    }

    public static String PLUGIN(String path) {
        return Path.of(Grasscutter.getConfig().folderStructure.plugins, path).toString();
    }

    @Deprecated(forRemoval = true)
    public static String SCRIPT(String path) {
        return Path.of(Grasscutter.getConfig().folderStructure.scripts, path).toString();
    }

    @Deprecated(forRemoval = true)
    public static String PACKET(String path) {
        return Path.of(Grasscutter.getConfig().folderStructure.packets, path).toString();
    }

    /**
     * Fallback method.
     *
     * @param left Attempt to use.
     * @param right Use if left is undefined.
     * @return Left or right.
     */
    public static <T> T lr(T left, T right) {
        return left == null ? right : left;
    }

    /**
     * {@link Configuration#lr(Object, Object)} for {@link String}s.
     *
     * @param left Attempt to use.
     * @param right Use if left is empty.
     * @return Left or right.
     */
    public static String lr(String left, String right) {
        return left.isEmpty() ? right : left;
    }

    /**
     * {@link Configuration#lr(Object, Object)} for {@link Integer}s.
     *
     * @param left Attempt to use.
     * @param right Use if left is 0.
     * @return Left or right.
     */
    public static int lr(int left, int right) {
        return left == 0 ? right : left;
    }
}
