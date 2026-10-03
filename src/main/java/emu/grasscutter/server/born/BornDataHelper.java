package emu.grasscutter.server.born;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import java.util.Arrays;
import java.util.List;

/** Owns the persistent transition from an empty account to a born player. */
public final class BornDataHelper {
    private BornDataHelper() {}

    /** Resolves the configured Traveler used by the automatic/skip-intro birth path. */
    public static int resolveAutomaticAvatarId() {
        int avatarId = GAME_OPTIONS.defaultAvatarId;
        if (avatarId != GameConstants.MAIN_CHARACTER_MALE
                && avatarId != GameConstants.MAIN_CHARACTER_FEMALE) {
            Grasscutter.getLogger()
                    .warn(
                            "Invalid gameOptions.defaultAvatarId {}; falling back to Lumine ({}).",
                            avatarId,
                            GameConstants.MAIN_CHARACTER_FEMALE);
            return GameConstants.MAIN_CHARACTER_FEMALE;
        }
        return avatarId;
    }

    /** Resolves the configured nickname used by the automatic/skip-intro birth path. */
    public static String resolveAutomaticNickname() {
        String nickname = GAME_OPTIONS.defaultNickname;
        return nickname == null || nickname.isBlank() ? "Traveler" : nickname;
    }

    /**
     * Completes the one-time persistent birth transition shared by native selection and automatic
     * birth.
     *
     * <p>This method creates only the Traveler and player metadata. World creation, scene entry, and
     * Quest 351 bootstrap deliberately stay outside this helper so every birth mode can share the
     * same scene-ready lifecycle.
     */
    public static boolean completeBirth(Player player, int avatarId, String nickname) {
        synchronized (player) {
            if (player.getAvatars().getAvatarCount() != 0) {
                return false;
            }

            int startingSkillDepot = startingSkillDepotFor(avatarId);
            if (startingSkillDepot == 0) {
                Grasscutter.getLogger()
                        .error(
                                "Refusing to birth uid={} with invalid Traveler avatarId {}.",
                                player.getUid(),
                                avatarId);
                return false;
            }

            if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
                Grasscutter.getLogger()
                        .error(
                                "No avatar data for Traveler {} while birthing uid={}; check ExcelBinOutput.",
                                avatarId,
                                player.getUid());
                return false;
            }

            String resolvedNickname =
                    nickname == null || nickname.isBlank() ? "Traveler" : nickname;
            player.setNickname(resolvedNickname);

            Avatar mainCharacter = new Avatar(avatarId);
            if (!GAME_OPTIONS.questing.enabled) {
                mainCharacter.setSkillDepotData(
                        GameData.getAvatarSkillDepotDataMap().get(startingSkillDepot));
            }

            player.addAvatar(mainCharacter, false);
            player.setMainCharacterId(avatarId);
            player.setHeadImage(avatarId);

            var team = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
            team.clear();
            team.add(avatarId);
            player.save();
            return true;
        }
    }

    /** Sends the standard one-time welcome mail after either birth path succeeds. */
    public static void sendWelcomeMail(Player player) {
        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        Mail mail = new Mail();
        mail.mailContent.title = welcomeMail.title;
        mail.mailContent.sender = welcomeMail.sender;
        mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mail.importance = 1;
        player.sendMail(mail);
    }

    /**
     * Repairs only metadata for an account that already owns avatars.
     *
     * <p>A player with no avatars is a new/incomplete account. Its birth must be performed explicitly
     * through {@link #completeBirth(Player, int, String)} by either the native-selection handler or
     * the automatic-birth path.
     */
    public static void ensureMainCharacter(Player player) {
        if (player.getMainCharacterId() != 0 && player.getAvatars().getAvatarCount() > 0) {
            if (player.getNickname() == null || player.getNickname().isBlank()) {
                String nickname =
                        BornDataConfig.getNickname(
                                player.getAccount() != null
                                        ? player.getAccount().getUsername()
                                        : "Traveler");
                player.setNickname(nickname);
                player.save();
            }
            return;
        }

        if (player.getAvatars().getAvatarCount() == 0) {
            Grasscutter.getLogger()
                    .error(
                            "Player uid={} has no avatars; refusing implicit Traveler creation outside the born flow.",
                            player.getUid());
            return;
        }

        repairMainCharacterFromExistingAvatars(player);
    }

    private static void repairMainCharacterFromExistingAvatars(Player player) {
        int avatarId = resolveMainAvatarId(player);
        if (avatarId == 0) {
            Grasscutter.getLogger()
                    .error(
                            "Broken player uid={} reports avatars but none can be resolved; refusing to invent a Traveler.",
                            player.getUid());
            return;
        }

        if (player.getNickname() == null || player.getNickname().isBlank()) {
            player.setNickname(
                    BornDataConfig.getNickname(
                            player.getAccount() != null
                                    ? player.getAccount().getUsername()
                                    : "Traveler"));
        }
        player.setMainCharacterId(avatarId);
        player.setHeadImage(avatarId);
        List<Integer> list = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
        if (!list.contains(avatarId)) {
            list.add(avatarId);
        }
        player.save();
    }

    private static int startingSkillDepotFor(int avatarId) {
        if (avatarId == GameConstants.MAIN_CHARACTER_MALE) return 504;
        if (avatarId == GameConstants.MAIN_CHARACTER_FEMALE) return 704;
        return 0;
    }

    private static int resolveMainAvatarId(Player player) {
        if (player.getAvatars().getAvatarById(GameConstants.MAIN_CHARACTER_MALE) != null) {
            return GameConstants.MAIN_CHARACTER_MALE;
        }
        if (player.getAvatars().getAvatarById(GameConstants.MAIN_CHARACTER_FEMALE) != null) {
            return GameConstants.MAIN_CHARACTER_FEMALE;
        }
        for (Avatar avatar : player.getAvatars()) {
            if (avatar == null) continue;
            return avatar.getAvatarId();
        }
        return 0;
    }
}
