package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {
    private static final int FIRST_MAIN_QUEST = 351;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME_OPTIONS.newAccountIntro;
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;
        boolean skipIntro = skipNewAccountIntro();

        if (freshAccount && intro.enabled && !skipIntro) {
            session.setState(SessionState.PICKING_CHARACTER);
            int notifyCmdId =
                    intro.doSetPlayerBornDataNotify > 0
                            ? intro.doSetPlayerBornDataNotify
                            : PacketOpcodes.DoSetPlayerBornDataNotify;
            session.send(new BasePacket(notifyCmdId));
            Grasscutter.getLogger()
                    .info(
                            "[intro] new account, waiting for character creation (notify cmdId={}).",
                            notifyCmdId);
            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        boolean playerBornNow = false;
        if (freshAccount) {
            if (skipIntro) {
                Grasscutter.getLogger()
                        .info(
                                "[intro-skip] new account uid={} bypassing native character/introduction flow.",
                                player.getUid());
            }
            createDefaultTraveler(player);
            playerBornNow = true;
        } else {
            BornDataHelper.ensureMainCharacter(player);
            var quest351 = player.getQuestManager().getMainQuestById(FIRST_MAIN_QUEST);
            if (quest351 != null && !quest351.getActiveQuests().isEmpty()) {
                Grasscutter.getLogger()
                        .info(
                                "[intro] existing login uid={} has active quest 351; QuestManager.onLogin will rewind it.",
                                player.getUid());
            }
        }

        player.onLogin();
        keepLegacyStarterStatueLocked(player);

        if (playerBornNow) {
            player.getQuestManager().onPlayerBorn();
            session.send(new PacketFinishedParentQuestNotify(player));
            session.send(new PacketQuestListNotify(player));
            session.send(new PacketQuestGlobalVarNotify(player));
        }

        session.send(new PacketPlayerLoginRsp(session));
    }

    private static boolean skipNewAccountIntro() {
        String value = System.getProperty("astaps.skipNewAccountIntro");
        if (value == null || value.isBlank()) {
            value = System.getenv("ASTAPS_SKIP_NEW_ACCOUNT_INTRO");
        }
        return value != null && Boolean.parseBoolean(value.trim());
    }

    private static void keepLegacyStarterStatueLocked(Player player) {
        // PlayerProgressManager still carries legacy starter seeds for scene 3 point 7 and area 1.
        // This probe branch deliberately removes both after login so the same Statue of the Seven
        // starts with its point locked and its map fog still present. Do not force-lock the point:
        // native unlock must remain free to add it normally during the current session.
        boolean removedPoint = player.getUnlockedScenePoints(3).remove(7);
        boolean removedArea = player.getUnlockedSceneAreas(3).remove(1);
        player.getForceLockedScenePoints(3).remove(7);
        if (removedPoint || removedArea) {
            player.save();
            Grasscutter.getLogger()
                    .info(
                            "[statue-probe] uid={} removed legacy starter unlocks point=3:7 area=3:1 pointRemoved={} areaRemoved={}.",
                            player.getUid(),
                            removedPoint,
                            removedArea);
        }
    }

    private static void createDefaultTraveler(Player player) {
        int avatarId = 10000007;
        Avatar mainCharacter = new Avatar(avatarId);

        if (!GAME_OPTIONS.questing.enabled) {
            mainCharacter.setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(704));
        }

        player.addAvatar(mainCharacter, false);
        player.setMainCharacterId(avatarId);
        player.setHeadImage(avatarId);
        var team = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
        team.clear();
        team.add(avatarId);
        player.save();
    }
}
