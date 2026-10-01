package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerNicknameNotify;
import emu.grasscutter.server.packet.send.PacketSetPlayerBornDataRsp;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerBornDataReq req = SetPlayerBornDataReq.parseFrom(payload);
        Player player = session.getPlayer();

        synchronized (player) {
            if (player.getAvatars().getAvatarCount() != 0) {
                session.send(
                        new PacketSetPlayerBornDataRsp(
                                Retcode.RET_REPEAT_SET_PLAYER_BORN_DATA.getNumber()));
                return;
            }

            if (req.getNickName() == null || req.getNickName().isBlank()) {
                session.send(
                        new PacketSetPlayerBornDataRsp(Retcode.RET_NICKNAME_IS_EMPTY.getNumber()));
                return;
            }

            int avatarId = req.getAvatarId();
            int startingSkillDepot;
            if (avatarId == GameConstants.MAIN_CHARACTER_MALE) {
                startingSkillDepot = 504;
            } else if (avatarId == GameConstants.MAIN_CHARACTER_FEMALE) {
                startingSkillDepot = 704;
            } else {
                session.send(
                        new PacketSetPlayerBornDataRsp(Retcode.RET_AVATAR_ID_ERROR.getNumber()));
                return;
            }

            if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
                Grasscutter.getLogger()
                        .error("No avatar data found! Please check your ExcelBinOutput folder.");
                session.send(
                        new PacketSetPlayerBornDataRsp(Retcode.RET_NOT_FOUND_CONFIG.getNumber()));
                return;
            }

            player.setNickname(req.getNickName());

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

            // Quest 351 reads world time immediately. Only the questing-enabled path needs this
            // pre-login World/Scene bootstrap; with questing disabled, onLogin creates the world.
            if (GAME_OPTIONS.questing.enabled) {
                if (player.getWorld() == null) {
                    World world = new World(player);
                    world.addPlayer(player);
                }
                player.getQuestManager().onPlayerBorn();
            }

            // The 7.1 client expects the born response before the ordinary login/scene packet
            // stream. PlayerNicknameNotify completes nickname synchronization for the same flow.
            session.send(new PacketSetPlayerBornDataRsp());
            session.send(new PacketPlayerNicknameNotify(req.getNickName()));

            player.onLogin();

            Grasscutter.getLogger()
                    .info(
                            "[intro] character creation finished: {} picked avatar {}.",
                            req.getNickName(),
                            avatarId);
        }

        // Default mail
        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        Mail mail = new Mail();
        mail.mailContent.title = welcomeMail.title;
        mail.mailContent.sender = welcomeMail.sender;
        // Please credit Grasscutter if changing something here. We don't condone commercial use of the
        // project.
        mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mail.importance = 1;
        player.sendMail(mail);
    }
}
