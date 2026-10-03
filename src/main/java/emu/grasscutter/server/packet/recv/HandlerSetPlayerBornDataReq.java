package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {
    private static final String SKIP_INTRO_PROPERTY = "astaps.skipNewAccountIntro";
    private static final String SKIP_INTRO_ENV = "ASTAPS_SKIP_NEW_ACCOUNT_INTRO";

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

            session.send(new PacketSetPlayerBornDataRsp());
            session.send(new PacketPlayerNicknameNotify(req.getNickName()));
            // Keep the native 7.1 intro gate, but publish the selected traveler before that intro
            // consumes player state. Full onLogin() remains delayed until the native pause cutover.
            session.send(new PacketAvatarDataNotify(player));

            session.setState(SessionState.ACTIVE);
            BornIntroGate.arm(session);

            if (shouldSkipNativeIntro()) {
                Grasscutter.getLogger()
                        .info(
                                "[intro-skip] born handshake complete for uid {}; skipping native post-born intro.",
                                player.getUid());

                // Reuse the already-tested cutover path instead of maintaining a second fresh-player
                // login sequence. Two synthetic false->true pause cycles are exactly the condition
                // BornIntroGate normally waits for before entering the world.
                BornIntroGate.notePause(session, false);
                BornIntroGate.notePause(session, true);
                BornIntroGate.notePause(session, false);
                BornIntroGate.notePause(session, true);
            } else {
                Grasscutter.getLogger()
                        .info(
                                "[intro] born handshake complete for uid {}; waiting for native pause-cycle cutover before world login.",
                                player.getUid());
            }
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

    private static boolean shouldSkipNativeIntro() {
        String configured = System.getProperty(SKIP_INTRO_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(SKIP_INTRO_ENV);
        }
        return configured != null && Boolean.parseBoolean(configured.trim());
    }
}
