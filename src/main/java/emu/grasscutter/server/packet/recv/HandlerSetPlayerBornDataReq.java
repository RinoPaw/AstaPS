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
    private static final int TEST_SET_PLAYER_BORN_DATA_RSP = 4761;

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

        Grasscutter.getLogger()
                .info(
                        "[born-rsp-4761] received SetPlayerBornDataReq cmdId={} avatarId={} nickname={}; testing rsp cmdId={}.",
                        PacketOpcodes.SetPlayerBornDataReq,
                        avatarId,
                        req.getNickName(),
                        TEST_SET_PLAYER_BORN_DATA_RSP);

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
                            "[born-rsp-4761] received SetPlayerBornDataReq for uid {} after an avatar already existed; refusing to overwrite it.",
                            player.getUid());
            session.close();
            return;
        }

        // Keep the tested born-order-b sequence: normal login/world creation first, then birth quests.
        // Packet logging is already ALL, so PlayerEnterSceneNotify will appear in the same trace.
        player.onLogin();
        player.getQuestManager().onPlayerBorn();

        Grasscutter.getLogger()
                .info(
                        "[born-rsp-4761] sending empty SetPlayerBornDataRsp candidate cmdId={} for uid={}.",
                        TEST_SET_PLAYER_BORN_DATA_RSP,
                        player.getUid());
        session.send(new BasePacket(TEST_SET_PLAYER_BORN_DATA_RSP));

        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        MailBuilder mailBuilder = new MailBuilder(player.getUid(), new Mail());
        mailBuilder.mail.mailContent.title = welcomeMail.title;
        mailBuilder.mail.mailContent.sender = welcomeMail.sender;
        mailBuilder.mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mailBuilder.mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mailBuilder.mail.importance = 1;
        player.sendMail(mailBuilder.mail);
    }
}
