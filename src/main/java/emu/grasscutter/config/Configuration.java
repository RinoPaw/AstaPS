package emu.grasscutter.config;

import static emu.grasscutter.Grasscutter.config;

import emu.grasscutter.utils.FileUtils;
import java.nio.file.Path;
import java.util.Locale;

/** Shared configuration access. New gameplay code should read {@link #GAME} directly. */
public final class Configuration extends ConfigContainer {
    private Configuration() {}

    public static final ConfigContainer c = config;

    public static final Locale LANGUAGE = config.language.language;
    public static final Locale FALLBACK_LANGUAGE = config.language.fallback;
    public static final String DOCUMENT_LANGUAGE = config.language.document;
    public static final ConfigContainer.Server SERVER = config.server;
    public static final ConfigContainer.Database DATABASE = config.databaseInfo;
    public static final ConfigContainer.HTTP HTTP_INFO = config.server.http;
    public static final ConfigContainer.Game GAME_INFO = config.server.game;
    public static final ConfigContainer.Dispatch DISPATCH_INFO = config.server.dispatch;
    public static final ConfigContainer.DebugMode DEBUG_MODE_INFO = config.server.debugMode;
    public static final ConfigContainer.Encryption HTTP_ENCRYPTION = config.server.http.encryption;
    public static final ConfigContainer.Policies HTTP_POLICIES = config.server.http.policies;
    public static final ConfigContainer.Files HTTP_STATIC_FILES = config.server.http.files;

    /** Root of game.json. */
    public static final GameConfig GAME = GameConfig.get();

    public static final ConfigContainer.Account ACCOUNT = GAME.account;
    public static final ConfigContainer.GameOptions.InventoryLimits INVENTORY_LIMITS =
            GAME.inventoryLimits;
    public static final ConfigContainer.GameOptions.HandbookOptions HANDBOOK = GAME.handbook;

    /** Compatibility alias while older callers are moved to GAME. */
    @Deprecated
    public static final ConfigContainer.GameOptions GAME_OPTIONS = config.server.game.gameOptions;

    public static final boolean FAST_REQUIRE = config.server.fastRequire;
    private static final String DATA_FOLDER = config.folderStructure.data;
    private static final String PLUGINS_FOLDER = config.folderStructure.plugins;
    private static final String SCRIPTS_FOLDER = config.folderStructure.scripts;
    private static final String PACKETS_FOLDER = config.folderStructure.packets;

    @Deprecated(forRemoval = true)
    public static String DATA() {
        return DATA_FOLDER;
    }

    @Deprecated(forRemoval = true)
    public static String DATA(String path) {
        return Path.of(DATA_FOLDER, path).toString();
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
        return PLUGINS_FOLDER;
    }

    public static String PLUGIN(String path) {
        return Path.of(PLUGINS_FOLDER, path).toString();
    }

    @Deprecated(forRemoval = true)
    public static String SCRIPT(String path) {
        return Path.of(SCRIPTS_FOLDER, path).toString();
    }

    @Deprecated(forRemoval = true)
    public static String PACKET(String path) {
        return Path.of(PACKETS_FOLDER, path).toString();
    }

    public static <T> T lr(T left, T right) {
        return left == null ? right : left;
    }

    public static String lr(String left, String right) {
        return left.isEmpty() ? right : left;
    }

    public static int lr(int left, int right) {
        return left == 0 ? right : left;
    }
}
