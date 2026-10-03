package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.GameConstants;
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
    private static final int STARTER_SCENE_ID = 3;
    private static final int STARTER_STATUE_POINT_ID = 7;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME_OPTIONS.newAccountIntro;
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;
        boolean skipIntro = freshAccount && intro.skip;

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

        boolean starterStatueUnlockedBeforeLogin =
                player.getUnlockedScenePoints(STARTER_SCENE_ID).contains(STARTER_STATUE_POINT_ID);
        boolean starterStatueForceLockedBeforeLogin =
                player.isScenePointForceLocked(STARTER_SCENE_ID, STARTER_STATUE_POINT_ID);

        boolean playerBornNow = false;
        if (freshAccount) {
            createDefaultTraveler(player);
            playerBornNow = true;
            if (skipIntro) {
                Grasscutter.getLogger()
                        .info(
                                "[intro-skip] new account uid={} created with default avatar={} nickname={}; native intro and quest 351 bootstrap skipped.",
                                player.getUid(),
                                player.getMainCharacterId(),
                                player.getNickname());
            }
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
        restoreStarterStatueState(
                player, starterStatueUnlockedBeforeLogin, starterStatueForceLockedBeforeLogin);

        // Explicit intro skip is a sandbox/test path: starting onPlayerBorn here would immediately
        // bootstrap quest 351 and replay the prologue the user just asked to bypass. The ordinary
        // auto-create path (newAccountIntro disabled, skip=false) keeps the historic quest bootstrap.
        if (playerBornNow && !skipIntro) {
            player.getQuestManager().onPlayerBorn();
            session.send(new PacketFinishedParentQuestNotify(player));
            session.send(new PacketQuestListNotify(player));
            session.send(new PacketQuestGlobalVarNotify(player));
        }

        session.send(new PacketPlayerLoginRsp(session));
    }

    private static void createDefaultTraveler(Player player) {
        int avatarId = GAME_OPTIONS.defaultAvatarId;
        if (avatarId != GameConstants.MAIN_CHARACTER_MALE
                && avatarId != GameConstants.MAIN_CHARACTER_FEMALE) {
            Grasscutter.getLogger()
                    .warn(
                            "Invalid gameOptions.defaultAvatarId {}; falling back to Lumine ({}).",
                            avatarId,
                            GameConstants.MAIN_CHARACTER_FEMALE);
            avatarId = GameConstants.MAIN_CHARACTER_FEMALE;
        }

        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .warn(
                            "No avatar data for configured default traveler {}; falling back to Lumine ({}).",
                            avatarId,
                            GameConstants.MAIN_CHARACTER_FEMALE);
            avatarId = GameConstants.MAIN_CHARACTER_FEMALE;
        }

        String nickname = GAME_OPTIONS.defaultNickname;
        if (nickname == null || nickname.isBlank()) {
            nickname = "Traveler";
        }

        Avatar mainCharacter = new Avatar(avatarId);
        if (!GAME_OPTIONS.questing.enabled) {
            int skillDepotId = avatarId == GameConstants.MAIN_CHARACTER_MALE ? 504 : 704;
            mainCharacter.setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(skillDepotId));
        }

        player.setNickname(nickname);
        player.addAvatar(mainCharacter, false);
        player.setMainCharacterId(avatarId);
        player.setHeadImage(avatarId);
        var team = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
        team.clear();
        team.add(avatarId);
        player.save();
    }

    /**
     * Legacy progress initialization still seeds scene 3 point 7 during Player.onLogin. Preserve the
     * persisted pre-login state so locked accounts stay locked while genuinely unlocked old accounts
     * are not regressed.
     */
    private static void restoreStarterStatueState(
            Player player, boolean wasUnlocked, boolean wasForceLocked) {
        var unlocked = player.getUnlockedScenePoints(STARTER_SCENE_ID);
        var forceLocked = player.getForceLockedScenePoints(STARTER_SCENE_ID);
        boolean changed =
                unlocked.contains(STARTER_STATUE_POINT_ID) != wasUnlocked
                        || forceLocked.contains(STARTER_STATUE_POINT_ID) != wasForceLocked;

        if (wasUnlocked) unlocked.add(STARTER_STATUE_POINT_ID);
        else unlocked.remove(STARTER_STATUE_POINT_ID);

        if (wasForceLocked) forceLocked.add(STARTER_STATUE_POINT_ID);
        else forceLocked.remove(STARTER_STATUE_POINT_ID);

        if (changed) player.save();
    }
}
