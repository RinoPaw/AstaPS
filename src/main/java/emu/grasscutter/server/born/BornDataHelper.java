/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import java.util.List;

public final class BornDataHelper {
    private BornDataHelper() {
    }

    /**
     * Repairs only metadata for an account that already owns avatars.
     *
     * <p>A player with no avatars is a new/incomplete account and must go through the real
     * SetPlayerBornData handshake. Creating a Traveler here would hide a protocol failure and make
     * the account look initialized when the client never selected a character.
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
                            "Player uid={} has no avatars; refusing automatic Traveler creation. The account must complete SetPlayerBornData.",
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

    private static int resolveMainAvatarId(Player player) {
        if (player.getAvatars().getAvatarById(10000005) != null) {
            return 10000005;
        }
        if (player.getAvatars().getAvatarById(10000007) != null) {
            return 10000007;
        }
        for (Avatar avatar : player.getAvatars()) {
            if (avatar == null) continue;
            return avatar.getAvatarId();
        }
        return 0;
    }
}
