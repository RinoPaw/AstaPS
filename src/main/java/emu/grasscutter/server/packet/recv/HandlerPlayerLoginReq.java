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
    private static final int DO_SET_PLAYER_BORN_DATA_NOTIFY = 22899;
    private static final int FIRST_MAIN_QUEST = 351;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;

        if (freshAccount && GAME_OPTIONS.newAccountIntro.enabled) {
            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new BasePacket(DO_SET_PLAYER_BORN_DATA_NOTIFY));
            Grasscutter.getLogger()
                    .info(
                            "[intro] new account, waiting for character creation (notify cmdId={}).",
                            DO_SET_PLAYER_BORN_DATA_NOTIFY);
            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        boolean playerBornNow = false;
        if (freshAccount) {
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

        if (playerBornNow) {
            player.getQuestManager().onPlayerBorn();
            session.send(new PacketFinishedParentQuestNotify(player));
            session.send(new PacketQuestListNotify(player));
            session.send(new PacketQuestGlobalVarNotify(player));
        }

        session.send(new PacketPlayerLoginRsp(session));
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
