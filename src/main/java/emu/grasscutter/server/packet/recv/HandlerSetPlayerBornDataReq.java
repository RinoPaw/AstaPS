package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.command.commands.SendMailCommand.MailBuilder;
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

        // Sanity checks
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

        // Make sure resources folder is set
        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .error("No avatar data found! Please check your ExcelBinOutput folder.");
            session.send(new PacketSetPlayerBornDataRsp(-1));
            session.close();
            return;
        }

        // Validate the reply side of the handshake before changing persistent player data. A
        // negative value in the 7.1 opcode tables is an unresolved placeholder, not a signed
        // on-wire CmdId.
        int rspCmdId = GAME_OPTIONS.newAccountIntro.setPlayerBornDataRsp;
        if (rspCmdId <= 0) {
            Grasscutter.getLogger()
                    .error(
                            "[intro] SetPlayerBornDataRsp CmdId is unknown ({}); character creation aborted before changing account data.",
                            rspCmdId);
            session.close();
            return;
        }

        // Get player object
        Player player = session.getPlayer();
        player.setNickname(req.getNickName());

        // Create avatar only from the client's explicit selection.
        if (player.getAvatars().getAvatarCount() == 0) {
            Avatar mainCharacter = new Avatar(avatarId);

            // Check if the default Anemo skill should be given.
            if (!GAME_OPTIONS.questing.enabled) {
                mainCharacter.setSkillDepotData(
                        GameData.getAvatarSkillDepotDataMap().get(startingSkillDepot));
            }

            // Manually handle adding to team
            player.addAvatar(mainCharacter, false);
            player.setMainCharacterId(avatarId);
            player.setHeadImage(avatarId);
            player
                    .getTeamManager()
                    .getCurrentSinglePlayerTeamInfo()
                    .getAvatars()
                    .add(mainCharacter.getAvatarId());
            player.save(); // TODO save player team in different object
        } else {
            Grasscutter.getLogger()
                    .error(
                            "[intro] received SetPlayerBornDataReq for uid {} after an avatar already existed; refusing to overwrite it.",
                            player.getUid());
            session.close();
            return;
        }

        // Login first so quest start() can safely read World state and register scene triggers.
        player.onLogin();

        // The character was just created: start the quests a new account begins with.
        player.getQuestManager().onPlayerBorn();

        Grasscutter.getLogger()
                .info(
                        "[intro] character creation finished: {} picked avatar {} (rsp cmdId={}).",
                        req.getNickName(),
                        avatarId,
                        rspCmdId);
        session.send(new BasePacket(rspCmdId));

        // Default mail
        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        MailBuilder mailBuilder = new MailBuilder(player.getUid(), new Mail());
        mailBuilder.mail.mailContent.title = welcomeMail.title;
        mailBuilder.mail.mailContent.sender = welcomeMail.sender;
        // Please credit Grasscutter if changing something here. We don't condone commercial use of the
        // project.
        mailBuilder.mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mailBuilder.mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mailBuilder.mail.importance = 1;
        player.sendMail(mailBuilder.mail);
    }
}
