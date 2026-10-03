package emu.grasscutter.server.born;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/** Lightweight configuration for the account's one-time birth/Traveler choice. */
public final class BornDataConfig {
    public enum Mode {
        /** Server chooses/creates the Traveler and skips character-selection/native intro visuals. */
        AUTO,
        /** Client performs the native 7.1 Traveler selection and post-born intro. */
        SELECT
    }

    private static final Path CONFIG_PATH = Path.of("config-born.json");
    private static volatile Mode mode = Mode.AUTO;
    private static volatile int avatarId = 10000007;
    private static volatile boolean randomGender = true;
    private static volatile String nickname = "";

    private BornDataConfig() {}

    public static void reload() {
        if (!Files.isRegularFile(CONFIG_PATH, new LinkOption[0])) {
            return;
        }

        try {
            String text = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();

            if (json.has("mode")) {
                mode = parseMode(json.get("mode").getAsString());
            }
            if (json.has("avatarId")) {
                avatarId = json.get("avatarId").getAsInt();
            }
            if (json.has("randomGender")) {
                randomGender = json.get("randomGender").getAsBoolean();
            }
            if (json.has("nickname")) {
                nickname = json.get("nickname").getAsString();
            }

            Grasscutter.getLogger()
                    .info(
                            "Born-data config: mode={} randomGender={} avatarId={} nickname={}",
                            mode,
                            randomGender,
                            randomGender ? "(random)" : Integer.valueOf(avatarId),
                            nickname == null || nickname.isBlank() ? "(account name)" : nickname);
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to read config-born.json, using defaults", exception);
        }
    }

    private static Mode parseMode(String value) {
        if (value == null || value.isBlank()) return Mode.AUTO;

        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            Grasscutter.getLogger()
                    .warn("Unknown config-born mode '{}'; using AUTO. Expected auto or select.", value);
            return Mode.AUTO;
        }
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean isAutoMode() {
        return mode == Mode.AUTO;
    }

    public static boolean isSelectionMode() {
        return mode == Mode.SELECT;
    }

    public static boolean isRandomGender() {
        return randomGender;
    }

    public static int getAvatarId() {
        return avatarId;
    }

    public static String getNickname(String fallback) {
        return nickname == null || nickname.isBlank() ? fallback : nickname;
    }

    static {
        reload();
    }
}
