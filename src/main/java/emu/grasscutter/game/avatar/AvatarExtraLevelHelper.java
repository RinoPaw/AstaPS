package emu.grasscutter.game.avatar;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.avatar.AvatarPromoteData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarInfoOuterClass;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarExtraLevelUpgradeRsp;
import emu.grasscutter.server.packet.send.PacketAvatarPromoteRsp;
import emu.grasscutter.server.packet.send.PacketAvatarPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarUpgradeRsp;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2FloatArrayMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AvatarExtraLevelHelper {
    private static List<AvatarExtraLevelConfig> configs = Collections.emptyList();
    private static boolean configsLoaded;

    private AvatarExtraLevelHelper() {}

    public static void ensureConfigsLoaded() {
        if (configsLoaded) {
            return;
        }
        configsLoaded = true;

        Path path = FileUtils.getResourcePath("ExcelBinOutput/AvatarExtraLevelExcelConfigData.json");
        if (!Files.isRegularFile(path, new LinkOption[0])) {
            Grasscutter.getLogger().warn("Missing AvatarExtraLevelExcelConfigData.json at {}", path);
            configs = Collections.emptyList();
            return;
        }

        try {
            List<AvatarExtraLevelConfig> loaded =
                    JsonUtils.loadToList(path, AvatarExtraLevelConfig.class);
            configs = loaded == null ? Collections.emptyList() : loaded;
            Grasscutter.getLogger().info("Loaded {} avatar extra level upgrade rows", configs.size());
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .error("Failed to load AvatarExtraLevelExcelConfigData.json", exception);
            configs = Collections.emptyList();
        }
    }

    public static void applyAvatarInfoExtraLevel(
            AvatarInfoOuterClass.AvatarInfo.Builder builder, Avatar avatar) {
        if (builder == null || avatar == null || avatar.getPromoteLevel() < 6) {
            return;
        }

        builder.setJMFFNNBEHGG(getExtraLevelTier(avatar));
        builder.setLDHKKNPGIMH(getEffectiveMaxLevel(avatar));
    }

    public static int getEffectiveMaxLevel(Avatar avatar) {
        if (avatar == null) {
            return 90;
        }
        if (avatar.getPromoteLevel() < 6) {
            AvatarPromoteData promoteData =
                    GameData.getAvatarPromoteData(
                            avatar.getAvatarData().getAvatarPromoteId(), avatar.getPromoteLevel());
            return promoteData == null ? 90 : promoteData.getUnlockMaxLevel();
        }

        int level = avatar.getLevel();
        if (level >= 95) {
            return 100;
        }
        if (level >= 90) {
            return 95;
        }

        AvatarPromoteData promoteData =
                GameData.getAvatarPromoteData(
                        avatar.getAvatarData().getAvatarPromoteId(), avatar.getPromoteLevel());
        return promoteData == null ? 90 : promoteData.getUnlockMaxLevel();
    }

    public static int getExtraLevelTier(Avatar avatar) {
        if (avatar == null) {
            return 0;
        }
        if (avatar.getLevel() >= 100) {
            return 2;
        }
        return avatar.getLevel() >= 95 ? 1 : 0;
    }

    public static boolean upgradeAvatar(Player player, Avatar avatar) {
        if (player == null || avatar == null) {
            return false;
        }

        ensureConfigsLoaded();
        if (!isEligibleForUpgrade(avatar)) {
            return false;
        }

        AvatarExtraLevelConfig config = findUpgradeRow(avatar.getLevel());
        return config != null && upgrade(player, avatar, config, 0);
    }

    public static boolean onPromoteReq(Player player, long guid) {
        logPromoteRequest(player, guid, PacketOpcodes.AvatarPromoteReq);
        return upgradeByGuid(player, guid);
    }

    public static void logPromoteRequest(Player player, long guid, int opcode) {
        if (player == null) {
            return;
        }

        Avatar avatar = player.getAvatars().getAvatarByGuid(guid);
        Grasscutter.getLogger()
                .info(
                        "AvatarExtraLevel AvatarPromoteReq opcode={} guid={} avatar={} level={} promote={} eligible={} reason={}",
                        opcode,
                        guid,
                        avatar == null ? 0 : avatar.getAvatarId(),
                        avatar == null ? -1 : avatar.getLevel(),
                        avatar == null ? -1 : avatar.getPromoteLevel(),
                        isEligibleForUpgrade(avatar),
                        describeEligibility(avatar));
    }

    public static boolean upgradeByGuid(Player player, long guid) {
        ensureConfigsLoaded();
        Avatar avatar = player == null ? null : player.getAvatars().getAvatarByGuid(guid);
        if (avatar == null || !isEligibleForUpgrade(avatar)) {
            return false;
        }

        AvatarExtraLevelConfig config = findUpgradeRow(avatar.getLevel());
        if (config == null) {
            Grasscutter.getLogger()
                    .warn(
                            "AvatarExtraLevel upgradeByGuid avatar={} level={} has no config row",
                            avatar.getAvatarId(),
                            avatar.getLevel());
            return false;
        }

        return upgrade(player, avatar, config, PacketOpcodes.AvatarPromoteReq);
    }

    public static void resyncAfterLogin(Player player) {
        if (player == null || player.getAvatars() == null) {
            return;
        }

        ensureConfigsLoaded();
        player.sendPacket(new PacketAvatarDataNotify(player));
        Grasscutter.getLogger()
                .info(
                        "AvatarExtraLevel login resync uid={} avatars={}",
                        player.getUid(),
                        player.getAvatars().getAvatarCount());
    }

    public static String describeEligibilityPublic(Avatar avatar) {
        return describeEligibility(avatar);
    }

    /** Dead bridge kept until the old domain helper method is removed; it no longer probes packets. */
    @Deprecated(forRemoval = true)
    public static boolean tryHandleUnregisteredPacket(Player player, int opcode, byte[] payload) {
        return false;
    }

    private static boolean upgrade(
            Player player,
            Avatar avatar,
            AvatarExtraLevelConfig config,
            int requestOpcode) {
        if (config.getToLevel() <= avatar.getLevel()) {
            return false;
        }

        ItemParamData[] costs = normalizeCosts(config.getCostItems());
        if (costs.length == 0) {
            Grasscutter.getLogger()
                    .warn(
                            "Avatar extra level {}->{} has no valid cost items",
                            config.getFromLevel(),
                            config.getToLevel());
            return false;
        }

        if (!player.getInventory().payItems(costs)) {
            player.sendMessage(
                    player, "Not enough masterless stella (104300) for extra level breakthrough.");
            sendFailure(player, avatar, config.getFromLevel());
            return true;
        }

        int oldLevel = avatar.getLevel();
        Int2FloatArrayMap oldFightProperties =
                new Int2FloatArrayMap((Int2FloatMap) avatar.getFightProperties());
        avatar.setLevel(config.getToLevel());
        avatar.setExp(0);
        avatar.recalcStats(true);
        avatar.save();

        player.sendPacket(new PacketAvatarPropNotify(avatar));
        player.sendPacket(new PacketAvatarUpgradeRsp(avatar, oldLevel, oldFightProperties));
        player.sendPacket(new PacketAvatarDataNotify(player));
        if (requestOpcode == PacketOpcodes.AvatarPromoteReq) {
            player.sendPacket(new PacketAvatarPromoteRsp(avatar));
        } else if (requestOpcode > 0) {
            sendSuccess(player, avatar, oldLevel);
        }

        player.sendMessage(
                player,
                String.format(
                        Locale.US,
                        "Extra level breakthrough: avatar %d %d -> %d",
                        avatar.getAvatarId(),
                        oldLevel,
                        avatar.getLevel()));
        return true;
    }

    private static void sendSuccess(Player player, Avatar avatar, int oldLevel) {
        player.sendPacket(
                new PacketAvatarExtraLevelUpgradeRsp(
                        avatar.getGuid(), oldLevel, avatar.getLevel()));
    }

    private static void sendFailure(Player player, Avatar avatar, int level) {
        player.sendPacket(new PacketAvatarExtraLevelUpgradeRsp(avatar.getGuid(), level, level, 1));
    }

    private static ItemParamData[] normalizeCosts(List<ItemParamData> costs) {
        if (costs == null || costs.isEmpty()) {
            return new ItemParamData[0];
        }

        return costs.stream()
                .filter(cost -> cost != null && cost.getId() > 0 && cost.getCount() > 0)
                .map(cost -> new ItemParamData(cost.getId(), cost.getCount()))
                .toArray(ItemParamData[]::new);
    }

    private static AvatarExtraLevelConfig findUpgradeRow(int level) {
        for (AvatarExtraLevelConfig config : configs) {
            if (config.getFromLevel() == level) {
                return config;
            }
        }
        return null;
    }

    private static String describeEligibility(Avatar avatar) {
        if (avatar == null || avatar.getAvatarData() == null) {
            return "avatar-missing";
        }
        if (avatar.getPromoteLevel() < 6) {
            return "promote<6 (need full ascension)";
        }

        int level = avatar.getLevel();
        if (level != 90 && level != 95) {
            return "level must be 90 or 95, got " + level;
        }
        if (level == 90) {
            AvatarPromoteData promoteData =
                    GameData.getAvatarPromoteData(
                            avatar.getAvatarData().getAvatarPromoteId(), avatar.getPromoteLevel());
            if (promoteData == null) {
                return "missing promote excel row";
            }
            if (level != promoteData.getUnlockMaxLevel()) {
                return "level " + level + " != unlockMaxLevel " + promoteData.getUnlockMaxLevel();
            }
        }
        if (level == 95 && getExtraLevelTier(avatar) < 1) {
            return "tier must be >=1 for 95->100, got " + getExtraLevelTier(avatar);
        }
        return findUpgradeRow(level) == null
                ? "no AvatarExtraLevelExcel row for level " + level
                : "ok";
    }

    private static boolean isEligibleForUpgrade(Avatar avatar) {
        return "ok".equals(describeEligibility(avatar));
    }
}
