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

            // Publish the selected traveler before acknowledging character creation. The 7.1 client
            // starts its native black-screen intro from the born-data response; matching Luna's
            // pre-response login ordering keeps traveler-dependent text and voice selection available
            // when that intro is initialized, while the full world login remains gated below.
            session.send(new PacketAvatarDataNotify(player));
            session.send(new PacketPlayerNicknameNotify(req.getNickName()));
            session.send(new PacketSetPlayerBornDataRsp());

            // 7.1 keeps the native second intro client-side after accepting 26105. Keep the session
            // ACTIVE, but do not establish World/Scene until the second observed false->true pause
            // cycle marks the native intro boundary. For repeated fresh-account testing, the switch
            // below synthesizes those two cycles and reuses the exact same world-entry path.
            session.setState(SessionState.ACTIVE);
            BornIntroGate.arm(session);
            if (skipNewAccountIntro()) {
                Grasscutter.getLogger()
                        .info(
                                "[intro-skip] born handshake complete for uid {}; skipping native post-born intro.",
                                player.getUid());
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

    private static boolean skipNewAccountIntro() {
        String value = System.getProperty("astaps.skipNewAccountIntro");
        if (value == null || value.isBlank()) {
            value = System.getenv("ASTAPS_SKIP_NEW_ACCOUNT_INTRO");
        }
        return value != null && Boolean.parseBoolean(value.trim());
    }
}
