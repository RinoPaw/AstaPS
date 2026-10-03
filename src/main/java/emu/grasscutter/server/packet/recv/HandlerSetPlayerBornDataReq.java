package emu.grasscutter.server.packet.recv;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;

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
            if (avatarId != GameConstants.MAIN_CHARACTER_MALE
                    && avatarId != GameConstants.MAIN_CHARACTER_FEMALE) {
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

            if (!BornDataHelper.completeBirth(player, avatarId, req.getNickName())) {
                session.send(
                        new PacketSetPlayerBornDataRsp(
                                Retcode.RET_REPEAT_SET_PLAYER_BORN_DATA.getNumber()));
                return;
            }

            // Arm native intro before acknowledging birth so immediate pause/cutscene packets are
            // observed by the correct gate. World creation still waits for the native 7.1 handoff;
            // Quest 351 later waits for PostEnterSceneRsp.
            session.setState(SessionState.ACTIVE);
            BornIntroGate.armNativeIntro(session);

            // Publish the selected Traveler before the born response initializes client-side intro
            // text/voice state.
            session.send(new PacketAvatarDataNotify(player));
            session.send(new PacketPlayerNicknameNotify(player.getNickname()));
            session.send(new PacketSetPlayerBornDataRsp());
            BornDataHelper.sendWelcomeMail(player);

            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} mode=select; avatar {} accepted, waiting for native intro cutover.",
                            player.getUid(),
                            avatarId);
        }
    }
}
