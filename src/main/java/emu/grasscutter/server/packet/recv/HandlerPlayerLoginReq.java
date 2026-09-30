package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {
    private static final int DO_SET_PLAYER_BORN_DATA_NOTIFY = 22899;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;

        if (freshAccount && GAME_OPTIONS.newAccountIntro.enabled) {
            // Keep the fresh account outside the world until the 7.1 client finishes its native
            // Traveler-selection flow. SetPlayerBornDataReq is only accepted in this session state.
            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new BasePacket(DO_SET_PLAYER_BORN_DATA_NOTIFY));
            Grasscutter.getLogger()
                    .info(
                            "[intro] new account, waiting for character creation (notify cmdId={}).",
                            DO_SET_PLAYER_BORN_DATA_NOTIFY);
            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        if (freshAccount) {
            // Intro disabled: preserve the existing automatic-Traveler path.
            createDefaultTraveler(player);

            // Quest 351 reads world time when it starts, so questing-enabled fresh accounts need
            // their own world before the one-time born quest lifecycle runs.
            if (GAME_OPTIONS.questing.enabled) {
                if (player.getWorld() == null) {
                    World world = new World(player);
                    world.addPlayer(player);
                }
                player.getQuestManager().onPlayerBorn();
            }
        } else {
            // Repair existing accounts that have avatars but lost their main-character marker.
            BornDataHelper.ensureMainCharacter(player);
        }

        player.onLogin();
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
