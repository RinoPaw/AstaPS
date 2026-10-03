package emu.grasscutter.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Player-facing and gameplay configuration stored directly in {@code game.json}. */
public final class GameConfig {
    private static final int CURRENT_VERSION = 1;
    private static final Path FILE = Path.of("game.json");
    private static final Path LEGACY_REWARD_FILE = Path.of("reward-overrides.json");

    private static volatile GameConfig current;

    public int version = CURRENT_VERSION;

    public ConfigContainer.Account account = new ConfigContainer.Account();
    public ConfigContainer.GameOptions.InventoryLimits inventoryLimits =
            new ConfigContainer.GameOptions.InventoryLimits();
    public ConfigContainer.GameOptions.AvatarLimits avatarLimits =
            new ConfigContainer.GameOptions.AvatarLimits();
    public int sceneEntityLimit = 1000;
    public boolean isPreventEntityError = true;

    public boolean watchGachaConfig = false;
    public boolean enableShopItems = false;
    public ConfigContainer.GameOptions.ArtifactShopOptions artifactShop =
            new ConfigContainer.GameOptions.ArtifactShopOptions();

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

    @SerializedName(value = "rewards", alternate = "rates")
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
        if (current == null) {
            if (Files.exists(FILE)) {
                try {
                    current = JsonUtils.loadToClass(FILE, GameConfig.class);
                } catch (Exception exception) {
                    Grasscutter.getLogger()
                            .error(
                                    "Unable to load game.json. Fix its syntax or delete it to regenerate defaults.",
                                    exception);
                    throw new IllegalStateException("Invalid game.json", exception);
                }
            } else {
                current = migrate(legacyRoot);
                importLegacyRewardOverrides(current);
                current.normalize();
                save(current);
                Grasscutter.getLogger().info("Created game.json from the previous gameplay configuration.");
            }
        }

        current.normalize();
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

    private static GameConfig migrate(JsonObject root) {
        GameConfig migrated = new GameConfig();
        if (root == null) return migrated;

        JsonObject server = object(root, "server");
        JsonObject game = object(server, "game");
        JsonObject oldOptions = object(game, "gameOptions");
        if (oldOptions != null) {
            var decoded = JsonUtils.decode(oldOptions, GameConfig.class);
            if (decoded != null) migrated = decoded;
        }

        if (root.has("account") && root.get("account").isJsonObject()) {
            var account = JsonUtils.decode(root.get("account"), ConfigContainer.Account.class);
            if (account != null) migrated.account = account;
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

    private void normalize() {
        version = CURRENT_VERSION;
        if (account == null) account = new ConfigContainer.Account();
        if (inventoryLimits == null) {
            inventoryLimits = new ConfigContainer.GameOptions.InventoryLimits();
        }
        if (avatarLimits == null) avatarLimits = new ConfigContainer.GameOptions.AvatarLimits();
        if (artifactShop == null) artifactShop = new ConfigContainer.GameOptions.ArtifactShopOptions();
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
        legacy.artifactShop = artifactShop;
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

        if (legacy.rates == null) legacy.rates = new ConfigContainer.GameOptions.Rates();
        legacy.rates.adventureExp = rewards.adventureExp;
        legacy.rates.mora = rewards.mora;
        if (legacy.rates.leyLines == null) {
            legacy.rates.leyLines = new ConfigContainer.GameOptions.LeyLineRates();
        }
        legacy.rates.leyLines.global = rewards.leyLines.global;
        legacy.rates.leyLines.mora = rewards.leyLines.wealth;
        legacy.rates.leyLines.experienceBooks = rewards.leyLines.revelation;

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

    private static void importLegacyRewardOverrides(GameConfig target) {
        if (!Files.exists(LEGACY_REWARD_FILE)) return;

        try {
            JsonObject root = JsonUtils.loadToClass(LEGACY_REWARD_FILE, JsonObject.class);
            applyUnlock(root.get("waypoint"), target.rewards.waypoint);
            applyUnlock(root.get("statue"), target.rewards.statue);

            JsonObject chests = object(root, "chests");
            if (chests != null) {
                applyChest(chests.get("common"), target.rewards.chests.common);
                applyChest(chests.get("exquisite"), target.rewards.chests.exquisite);
                applyChest(chests.get("precious"), target.rewards.chests.precious);
                applyChest(chests.get("luxurious"), target.rewards.chests.luxurious);
            }
            Grasscutter.getLogger()
                    .info(
                            "Imported reward-overrides.json into game.json; the old file is no longer read.");
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn(
                            "Could not import reward-overrides.json; game.json keeps original-game reward defaults.",
                            exception);
        }
    }

    private static void applyUnlock(JsonElement element, UnlockReward target) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        target.primogems = intOr(object, "primogems", target.primogems);
        target.adventureExp = intOr(object, "adventureExp", target.adventureExp);
        target.fragileResin = intOr(object, "fragileResin", target.fragileResin);
        target.heroWit = intOr(object, "heroWit", target.heroWit);
        target.mysticEnhancementOre =
                intOr(object, "mysticEnhancementOre", target.mysticEnhancementOre);
    }

    private static void applyChest(JsonElement element, ChestReward target) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) return;
        var migrated = JsonUtils.decode(element, ChestReward.class);
        if (migrated == null) return;
        target.enabled = true;
        target.primogems = migrated.primogems;
        target.adventureExp = migrated.adventureExp;
        target.sigil = migrated.sigil;
        target.mora = migrated.mora;
        target.enhancementOre = migrated.enhancementOre;
        target.fineEnhancementOre = migrated.fineEnhancementOre;
        target.mysticEnhancementOre = migrated.mysticEnhancementOre;
        target.wanderersAdvice = migrated.wanderersAdvice;
        target.adventurersExperience = migrated.adventurersExperience;
        target.herosWit = migrated.herosWit;
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        return Math.max(0, object.get(key).getAsInt());
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

        @JsonAdapter(LeyLineRatesAdapter.class)
        public LeyLineRates leyLines = new LeyLineRates();

        public UnlockReward waypoint = new UnlockReward(5, 10, 0, 0, 0);
        public UnlockReward statue = new UnlockReward(5, 50, 0, 0, 0);
        public ChestRewards chests = new ChestRewards();

        private void normalize() {
            if (leyLines == null) leyLines = new LeyLineRates();
            if (waypoint == null) waypoint = new UnlockReward(5, 10, 0, 0, 0);
            if (statue == null) statue = new UnlockReward(5, 50, 0, 0, 0);
            if (chests == null) chests = new ChestRewards();
            chests.normalize();
        }

        public UnlockReward unlock(boolean statuePoint) {
            return statuePoint ? statue : waypoint;
        }

        public ChestReward chest(String tier) {
            if (tier == null || chests == null) return null;
            ChestReward reward =
                    switch (tier) {
                        case "COMMON" -> chests.common;
                        case "EXQUISITE" -> chests.exquisite;
                        case "PRECIOUS" -> chests.precious;
                        case "LUXURIOUS" -> chests.luxurious;
                        default -> null;
                    };
            return reward != null && reward.enabled ? reward : null;
        }
    }

    public static final class LeyLineRates {
        public float global = 2.0f;
        public float wealth = 1.0f;
        public float revelation = 1.0f;
    }

    /** Accepts old scalar/source rate shapes and writes one canonical form. */
    public static final class LeyLineRatesAdapter
            implements com.google.gson.JsonDeserializer<LeyLineRates>,
                    com.google.gson.JsonSerializer<LeyLineRates> {
        @Override
        public LeyLineRates deserialize(
                JsonElement json,
                java.lang.reflect.Type typeOfT,
                com.google.gson.JsonDeserializationContext context) {
            var rates = new LeyLineRates();
            if (json == null || json.isJsonNull()) return rates;
            if (json.isJsonPrimitive()) {
                rates.global = json.getAsFloat();
                return rates;
            }

            JsonObject object = json.getAsJsonObject();
            rates.global = object.has("global") ? object.get("global").getAsFloat() : 1.0f;
            if (object.has("wealth")) rates.wealth = object.get("wealth").getAsFloat();
            else if (object.has("mora")) rates.wealth = object.get("mora").getAsFloat();

            if (object.has("revelation")) rates.revelation = object.get("revelation").getAsFloat();
            else if (object.has("experienceBooks")) {
                rates.revelation = object.get("experienceBooks").getAsFloat();
            } else if (object.has("exp")) rates.revelation = object.get("exp").getAsFloat();
            return rates;
        }

        @Override
        public JsonElement serialize(
                LeyLineRates src,
                java.lang.reflect.Type typeOfSrc,
                com.google.gson.JsonSerializationContext context) {
            if (src == null) return com.google.gson.JsonNull.INSTANCE;
            JsonObject object = new JsonObject();
            object.addProperty("global", src.global);
            object.addProperty("wealth", src.wealth);
            object.addProperty("revelation", src.revelation);
            return object;
        }
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
        public ChestReward common = new ChestReward();
        public ChestReward exquisite = new ChestReward();
        public ChestReward precious = new ChestReward();
        public ChestReward luxurious = new ChestReward();

        private void normalize() {
            if (common == null) common = new ChestReward();
            if (exquisite == null) exquisite = new ChestReward();
            if (precious == null) precious = new ChestReward();
            if (luxurious == null) luxurious = new ChestReward();
        }
    }

    /** Set enabled=true to replace the original drop table for that chest tier. */
    public static final class ChestReward {
        public boolean enabled = false;
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
