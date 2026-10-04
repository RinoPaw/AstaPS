package emu.grasscutter.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Player-facing and gameplay configuration stored directly in {@code game.json}. */
public final class GameConfig {
    private static final int CURRENT_VERSION = 5;
    private static final Path FILE = Path.of("game.json");

    private static volatile GameConfig current;

    public int version = CURRENT_VERSION;

    public ConfigContainer.Account account = new ConfigContainer.Account();
    public int maxOnlinePlayers = -1;
    public ConfigContainer.GameOptions.InventoryLimits inventoryLimits =
            new ConfigContainer.GameOptions.InventoryLimits();
    public ConfigContainer.GameOptions.AvatarLimits avatarLimits =
            new ConfigContainer.GameOptions.AvatarLimits();
    public int sceneEntityLimit = 1000;
    public boolean isPreventEntityError = true;

    public boolean watchGachaConfig = false;
    public boolean enableShopItems = false;
    public ArtifactSettings artifacts = new ArtifactSettings();
    public ShopSettings shops = new ShopSettings();

    public boolean staminaUsage = true;
    public boolean energyUsage = true;
    public boolean fishhookTeleport = true;
    public boolean trialCostumes = false;
    public int defaultAvatarId = 10000007;
    public String defaultNickname = "Traveler";
    public int firstLoginCutscene = 0;
    public boolean disableCutscenes = false;
    public boolean forceFinishMainQuestsOnLogin = false;
    public ConfigContainer.GameOptions.NewAccountIntro newAccountIntro =
            new ConfigContainer.GameOptions.NewAccountIntro();

    @SerializedName(value = "quests", alternate = {"questing", "questOptions"})
    public ConfigContainer.GameOptions.Questing quests =
            new ConfigContainer.GameOptions.Questing();

    @SerializedName(value = "resin", alternate = "resinOptions")
    public ConfigContainer.GameOptions.ResinOptions resin =
            new ConfigContainer.GameOptions.ResinOptions();

    public Rewards rewards = new Rewards();

    public ConfigContainer.GameOptions.TowerOptions tower =
            new ConfigContainer.GameOptions.TowerOptions();
    public ConfigContainer.GameOptions.HandbookOptions handbook =
            new ConfigContainer.GameOptions.HandbookOptions();
    public ConfigContainer.GameOptions.BirthdayMailOptions birthdayMail =
            new ConfigContainer.GameOptions.BirthdayMailOptions();
    public ConfigContainer.GameOptions.WatermarkOptions watermark =
            new ConfigContainer.GameOptions.WatermarkOptions();

    @SerializedName(value = "join", alternate = "joinOptions")
    public ConfigContainer.JoinOptions join = new ConfigContainer.JoinOptions();

    public ConfigContainer.ConsoleAccount serverAccount = new ConfigContainer.ConsoleAccount();
    public ConfigContainer.ConsoleAccount dpsAccount = defaultDpsAccount();
    public ConfigContainer.VisionOptions[] visionOptions = defaultVisionOptions();

    private GameConfig() {}

    /** Loads game.json, or migrates the gameplay fields out of config.json exactly once. */
    public static synchronized GameConfig loadAndBind(
            JsonObject legacyRoot, ConfigContainer serverConfig) {
        boolean rewrite = false;
        if (current == null) {
            if (Files.exists(FILE)) {
                try {
                    JsonObject stored = JsonUtils.loadToClass(FILE, JsonObject.class);
                    validateRewardSchema(stored);
                    validateShopSchema(stored);
                    current = stored == null ? new GameConfig() : JsonUtils.decode(stored, GameConfig.class);
                    if (current == null) current = new GameConfig();
                    rewrite = migrateStored(stored, current);
                } catch (Exception exception) {
                    Grasscutter.getLogger()
                            .error(
                                    "Unable to load game.json. Fix its syntax or delete it to regenerate defaults.",
                                    exception);
                    throw new IllegalStateException("Invalid game.json", exception);
                }
            } else {
                current = migrate(legacyRoot);
                current.normalize();
                save(current);
                Grasscutter.getLogger().info("Created game.json from the previous gameplay configuration.");
            }
        }

        current.normalize();
        if (rewrite) {
            save(current);
            Grasscutter.getLogger().info("Migrated game.json to version {}.", CURRENT_VERSION);
        }
        current.bindLegacyViews(serverConfig);
        return current;
    }

    public static GameConfig get() {
        var loaded = current;
        if (loaded != null) return loaded;

        synchronized (GameConfig.class) {
            if (current == null) {
                JsonObject legacyRoot = null;
                try {
                    if (Grasscutter.configFile.exists()) {
                        legacyRoot =
                                JsonUtils.loadToClass(
                                        Grasscutter.configFile.toPath(), JsonObject.class);
                    }
                } catch (Exception exception) {
                    Grasscutter.getLogger().warn("Could not inspect config.json for game migration.", exception);
                }
                loadAndBind(legacyRoot, Grasscutter.config);
            }
            return current;
        }
    }

    private static boolean migrateStored(JsonObject root, GameConfig target) {
        if (root == null) return false;

        boolean rewrite = !root.has("version") || root.get("version").getAsInt() < CURRENT_VERSION;
        JsonObject oldAccount = object(root, "account");
        if (!root.has("maxOnlinePlayers")
                && oldAccount != null
                && oldAccount.has("maxPlayer")
                && !oldAccount.get("maxPlayer").isJsonNull()) {
            target.maxOnlinePlayers = oldAccount.get("maxPlayer").getAsInt();
            rewrite = true;
        }

        if (oldAccount != null
                && (oldAccount.has("maxPlayer")
                        || oldAccount.has("EXPERIMENTAL_RealPassword")
                        || oldAccount.has("useIntegrationPassword"))) {
            rewrite = true;
        }

        JsonObject oldArtifactShop = object(root, "artifactShop");
        if (oldArtifactShop != null) {
            migrateLegacyArtifactShop(oldArtifactShop, target);
            rewrite = true;
        }

        JsonObject oldNestedShop = object(object(root, "artifacts"), "shop");
        if (oldNestedShop != null) {
            migrateArtifactShopV4(oldNestedShop, target);
            rewrite = true;
        }

        if (!root.has("artifacts") || !root.has("shops")) rewrite = true;
        return rewrite;
    }

    private static GameConfig migrate(JsonObject root) {
        GameConfig migrated = new GameConfig();
        if (root == null) return migrated;

        JsonObject server = object(root, "server");
        JsonObject game = object(server, "game");
        JsonObject oldOptions = object(game, "gameOptions");
        if (oldOptions != null) {
            var decoded = JsonUtils.decode(oldOptions, GameConfig.class);
            if (decoded != null) migrated = decoded;
            migrateLegacyArtifactShop(object(oldOptions, "artifactShop"), migrated);
        }

        JsonObject oldAccount = object(root, "account");
        if (oldAccount != null) {
            var account = JsonUtils.decode(oldAccount, ConfigContainer.Account.class);
            if (account != null) migrated.account = account;
            if (oldAccount.has("maxPlayer") && !oldAccount.get("maxPlayer").isJsonNull()) {
                migrated.maxOnlinePlayers = oldAccount.get("maxPlayer").getAsInt();
            }
        }
        if (game != null) {
            migrated.join =
                    decodeOr(
                            game.get("joinOptions"),
                            ConfigContainer.JoinOptions.class,
                            migrated.join);
            migrated.serverAccount =
                    decodeOr(
                            game.get("serverAccount"),
                            ConfigContainer.ConsoleAccount.class,
                            migrated.serverAccount);
            migrated.dpsAccount =
                    decodeOr(
                            game.get("dpsAccount"),
                            ConfigContainer.ConsoleAccount.class,
                            migrated.dpsAccount);
            if (game.has("visionOptions") && game.get("visionOptions").isJsonArray()) {
                var vision =
                        JsonUtils.decode(
                                game.get("visionOptions"), ConfigContainer.VisionOptions[].class);
                if (vision != null) migrated.visionOptions = vision;
            }
        }

        if (hasLegacyGameplay(root)) backupLegacyConfig();
        return migrated;
    }

    private static void migrateLegacyArtifactShop(JsonObject old, GameConfig target) {
        if (old == null || target == null) return;
        ensureArtifactShop(target);
        if (old.has("enabled") && !old.get("enabled").isJsonNull()) {
            target.shops.artifact.enabled = old.get("enabled").getAsBoolean();
        }
        if (old.has("buyLimit") && !old.get("buyLimit").isJsonNull()) {
            target.shops.artifact.buyLimit = Math.max(0, old.get("buyLimit").getAsInt());
        }
    }

    private static void migrateArtifactShopV4(JsonObject old, GameConfig target) {
        if (old == null || target == null) return;
        ensureArtifactShop(target);
        var decoded = JsonUtils.decode(old, ShopSettings.Artifact.class);
        if (decoded != null) target.shops.artifact = decoded;
    }

    private static void ensureArtifactShop(GameConfig target) {
        if (target.shops == null) target.shops = new ShopSettings();
        if (target.shops.artifact == null) target.shops.artifact = new ShopSettings.Artifact();
    }

    private void normalize() {
        version = CURRENT_VERSION;
        if (account == null) account = new ConfigContainer.Account();
        if (inventoryLimits == null) {
            inventoryLimits = new ConfigContainer.GameOptions.InventoryLimits();
        }
        if (avatarLimits == null) avatarLimits = new ConfigContainer.GameOptions.AvatarLimits();
        if (artifacts == null) artifacts = new ArtifactSettings();
        artifacts.normalize();
        if (shops == null) shops = new ShopSettings();
        shops.normalize();
        if (defaultNickname == null || defaultNickname.isBlank()) defaultNickname = "Traveler";
        if (newAccountIntro == null) {
            newAccountIntro = new ConfigContainer.GameOptions.NewAccountIntro();
        }
        if (quests == null) quests = new ConfigContainer.GameOptions.Questing();
        if (resin == null) resin = new ConfigContainer.GameOptions.ResinOptions();
        if (rewards == null) rewards = new Rewards();
        rewards.normalize();
        if (tower == null) tower = new ConfigContainer.GameOptions.TowerOptions();
        if (handbook == null) handbook = new ConfigContainer.GameOptions.HandbookOptions();
        if (birthdayMail == null) birthdayMail = new ConfigContainer.GameOptions.BirthdayMailOptions();
        if (watermark == null) watermark = new ConfigContainer.GameOptions.WatermarkOptions();
        if (join == null) join = new ConfigContainer.JoinOptions();
        if (serverAccount == null) serverAccount = new ConfigContainer.ConsoleAccount();
        if (dpsAccount == null) dpsAccount = defaultDpsAccount();
        if (visionOptions == null) visionOptions = defaultVisionOptions();
    }

    /** Keeps existing code working while callers move from GAME_OPTIONS to GAME. */
    private void bindLegacyViews(ConfigContainer serverConfig) {
        if (serverConfig == null || serverConfig.server == null || serverConfig.server.game == null) {
            return;
        }

        serverConfig.account = account;
        var legacy = serverConfig.server.game.gameOptions;
        if (legacy == null) {
            legacy = new ConfigContainer.GameOptions();
            serverConfig.server.game.gameOptions = legacy;
        }

        legacy.inventoryLimits = inventoryLimits;
        legacy.avatarLimits = avatarLimits;
        legacy.sceneEntityLimit = sceneEntityLimit;
        legacy.isPreventEntityError = isPreventEntityError;
        legacy.watchGachaConfig = watchGachaConfig;
        legacy.enableShopItems = enableShopItems;
        legacy.staminaUsage = staminaUsage;
        legacy.energyUsage = energyUsage;
        legacy.fishhookTeleport = fishhookTeleport;
        legacy.trialCostumes = trialCostumes;
        legacy.defaultAvatarId = defaultAvatarId;
        legacy.defaultNickname = defaultNickname;
        legacy.firstLoginCutscene = firstLoginCutscene;
        legacy.disableCutscenes = disableCutscenes;
        legacy.forceFinishMainQuestsOnLogin = forceFinishMainQuestsOnLogin;
        legacy.newAccountIntro = newAccountIntro;
        legacy.questing = quests;
        legacy.resinOptions = resin;
        legacy.tower = tower;
        legacy.handbook = handbook;
        legacy.birthdayMail = birthdayMail;
        legacy.watermark = watermark;

        serverConfig.server.game.joinOptions = join;
        serverConfig.server.game.serverAccount = serverAccount;
        serverConfig.server.game.dpsAccount = dpsAccount;
        serverConfig.server.game.visionOptions = visionOptions;
    }

    private static void save(GameConfig value) {
        try (var writer = new FileWriter(FILE.toFile())) {
            writer.write(JsonUtils.encode(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write game.json", exception);
        }
    }

    private static void validateRewardSchema(JsonObject root) {
        if (root == null) return;
        if (root.has("rates")) {
            throw new IllegalArgumentException(
                    "game.json uses the removed rewards alias 'rates'; regenerate or update it to 'rewards'.");
        }

        JsonObject rewards = object(root, "rewards");
        if (rewards == null) return;

        JsonElement leyLines = rewards.get("leyLines");
        if (leyLines != null && !leyLines.isJsonNull()) {
            if (!leyLines.isJsonObject()) {
                throw new IllegalArgumentException(
                        "game.json rewards.leyLines must be an object with wealth/revelation only.");
            }
            JsonObject rates = leyLines.getAsJsonObject();
            for (String removed : new String[] {"global", "mora", "exp", "experienceBooks"}) {
                if (rates.has(removed)) {
                    throw new IllegalArgumentException(
                            "game.json rewards.leyLines contains removed field '" + removed + "'.");
                }
            }
        }

        JsonObject chests = object(rewards, "chests");
        if (chests == null) return;
        for (String tier : new String[] {"common", "exquisite", "precious", "luxurious"}) {
            JsonObject reward = object(chests, tier);
            if (reward != null && reward.has("enabled")) {
                throw new IllegalArgumentException(
                        "game.json rewards.chests." + tier + " contains removed field 'enabled'.");
            }
        }
    }

    private static void validateShopSchema(JsonObject root) {
        if (root == null) return;
        validateArtifactShopFields(object(object(root, "shops"), "artifact"), "shops.artifact");
        // Version 4 stored the same shop object under artifacts.shop; accept it for migration while
        // still rejecting the already-removed pre-v4 fields.
        validateArtifactShopFields(object(object(root, "artifacts"), "shop"), "artifacts.shop");
    }

    private static void validateArtifactShopFields(JsonObject shop, String path) {
        if (shop == null) return;
        for (String removed :
                new String[] {
                    "shopId",
                    "costMora",
                    "costPrimogems",
                    "costItemId",
                    "costItemCount",
                    "critWeight",
                    "damageWeight",
                    "highRollBias"
                }) {
            if (shop.has(removed)) {
                throw new IllegalArgumentException(
                        "game.json " + path + " contains removed field '" + removed + "'.");
            }
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static <T> T decodeOr(JsonElement value, Class<T> type, T fallback) {
        if (value == null || value.isJsonNull()) return fallback;
        T decoded = JsonUtils.decode(value, type);
        return decoded == null ? fallback : decoded;
    }

    private static boolean hasLegacyGameplay(JsonObject root) {
        if (root == null) return false;
        if (root.has("account")) return true;
        JsonObject game = object(object(root, "server"), "game");
        if (game == null) return false;
        return game.has("gameOptions")
                || game.has("joinOptions")
                || game.has("serverAccount")
                || game.has("dpsAccount")
                || game.has("visionOptions");
    }

    private static void backupLegacyConfig() {
        try {
            Path source = Grasscutter.configFile.toPath();
            Path backup = Path.of("config.json.bak");
            if (Files.exists(source) && !Files.exists(backup)) {
                Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Grasscutter.getLogger().info("Backed up the pre-split configuration to config.json.bak.");
            }
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Could not create config.json.bak.", exception);
        }
    }

    private static ConfigContainer.ConsoleAccount defaultDpsAccount() {
        var account = new ConfigContainer.ConsoleAccount();
        account.nickName = "DPS";
        account.signature = "Send dps30 to start, dpsstop to end early";
        account.adventureRank = 60;
        return account;
    }

    private static ConfigContainer.VisionOptions[] defaultVisionOptions() {
        return new ConfigContainer.VisionOptions[] {
            new ConfigContainer.VisionOptions("VISION_LEVEL_NORMAL", 80, 20),
            new ConfigContainer.VisionOptions("VISION_LEVEL_LITTLE_REMOTE", 16, 40),
            new ConfigContainer.VisionOptions("VISION_LEVEL_REMOTE", 1000, 250),
            new ConfigContainer.VisionOptions("VISION_LEVEL_SUPER", 4000, 1000),
            new ConfigContainer.VisionOptions("VISION_LEVEL_NEARBY", 40, 20),
            new ConfigContainer.VisionOptions("VISION_LEVEL_SUPER_NEARBY", 20, 20)
        };
    }

    public static final class Rewards {
        public float adventureExp = 1.5f;
        public float mora = 2.0f;
        public LeyLineRates leyLines = new LeyLineRates();
        public UnlockReward waypoint = new UnlockReward(5, 10, 0, 0, 0);
        public UnlockReward statue = new UnlockReward(5, 50, 0, 0, 0);
        public ChestRewards chests = new ChestRewards();

        private void normalize() {
            if (leyLines == null) leyLines = new LeyLineRates();
            if (waypoint == null) waypoint = new UnlockReward(5, 10, 0, 0, 0);
            if (statue == null) statue = new UnlockReward(5, 50, 0, 0, 0);
            if (chests == null) chests = new ChestRewards();
        }

        public UnlockReward unlock(boolean statuePoint) {
            return statuePoint ? statue : waypoint;
        }

        public ChestReward chest(String tier) {
            if (tier == null || chests == null) return null;
            return switch (tier) {
                case "COMMON" -> chests.common;
                case "EXQUISITE" -> chests.exquisite;
                case "PRECIOUS" -> chests.precious;
                case "LUXURIOUS" -> chests.luxurious;
                default -> null;
            };
        }
    }

    public static final class LeyLineRates {
        public float wealth = 2.0f;
        public float revelation = 2.0f;
    }

    public static final class UnlockReward {
        public int primogems;
        public int adventureExp;
        public int fragileResin;
        public int heroWit;
        public int mysticEnhancementOre;

        public UnlockReward() {}

        public UnlockReward(
                int primogems,
                int adventureExp,
                int fragileResin,
                int heroWit,
                int mysticEnhancementOre) {
            this.primogems = primogems;
            this.adventureExp = adventureExp;
            this.fragileResin = fragileResin;
            this.heroWit = heroWit;
            this.mysticEnhancementOre = mysticEnhancementOre;
        }
    }

    public static final class ChestRewards {
        public ChestReward common;
        public ChestReward exquisite;
        public ChestReward precious;
        public ChestReward luxurious;
    }

    /** Presence of a tier object replaces the original drop table for that chest tier. */
    public static final class ChestReward {
        public int primogems;
        public int adventureExp;
        public int sigil;
        public int mora;
        public int enhancementOre;
        public int fineEnhancementOre;
        public int mysticEnhancementOre;
        public int wanderersAdvice;
        public int adventurersExperience;
        public int herosWit;
    }
}
