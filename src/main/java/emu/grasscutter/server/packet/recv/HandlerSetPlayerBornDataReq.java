package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerBornDataRsp;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerBornDataReq req = SetPlayerBornDataReq.parseFrom(payload);

        int avatarId = req.getAvatarId();
        int startingSkillDepot;
        if (avatarId == GameConstants.MAIN_CHARACTER_MALE) {
            startingSkillDepot = 504;
        } else if (avatarId == GameConstants.MAIN_CHARACTER_FEMALE) {
            startingSkillDepot = 704;
        } else {
            session.send(new PacketSetPlayerBornDataRsp(-1));
            return;
        }

        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .error("No avatar data found! Please check your ExcelBinOutput folder.");
            session.send(new PacketSetPlayerBornDataRsp(-1));
            session.close();
            return;
        }

        int rspCmdId = GAME_OPTIONS.newAccountIntro.setPlayerBornDataRsp;
        if (rspCmdId <= 0) {
            Grasscutter.getLogger()
                    .error(
                            "[intro] SetPlayerBornDataRsp CmdId is unknown ({}); character creation aborted before changing account data.",
                            rspCmdId);
            session.close();
            return;
        }

        Player player = session.getPlayer();
        player.setNickname(req.getNickName());

        if (player.getAvatars().getAvatarCount() == 0) {
            Avatar mainCharacter = new Avatar(avatarId);

            if (!GAME_OPTIONS.questing.enabled) {
                mainCharacter.setSkillDepotData(
                        GameData.getAvatarSkillDepotDataMap().get(startingSkillDepot));
            }

            player.addAvatar(mainCharacter, false);
            player.setMainCharacterId(avatarId);
            player.setHeadImage(avatarId);
            player
                    .getTeamManager()
                    .getCurrentSinglePlayerTeamInfo()
                    .getAvatars()
                    .add(mainCharacter.getAvatarId());
            player.save();
        } else {
            Grasscutter.getLogger()
                    .error(
                            "[intro] received SetPlayerBornDataReq for uid {} after an avatar already existed; refusing to overwrite it.",
                            player.getUid());
            session.close();
            return;
        }

        player.getQuestManager().onPlayerBorn();
        player.onLogin();

        Grasscutter.getLogger()
                .info(
                        "[intro] character creation finished: {} picked avatar {} (rsp cmdId={}).",
                        req.getNickName(),
                        avatarId,
                        rspCmdId);
        session.send(new BasePacket(rspCmdId));

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
}
