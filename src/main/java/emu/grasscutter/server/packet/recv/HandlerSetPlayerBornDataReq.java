package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {
    private static final int TEST_BORN_RSP_CMD_ID = 4385;

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
            Grasscutter.getLogger().warn("[BORN-4385] invalid Traveler id {}.", avatarId);
            return;
        }

        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .error("No avatar data found! Please check your ExcelBinOutput folder.");
            return;
        }

        Player player = session.getPlayer();
        if (player.getAvatars().getAvatarCount() != 0) {
            Grasscutter.getLogger()
                    .warn(
                            "[BORN-4385] ignoring duplicate SetPlayerBornDataReq for uid {}; Traveler {} is already persisted.",
                            player.getUid(),
                            player.getMainCharacterId());
            return;
        }

        Grasscutter.getLogger()
                .info(
                        "[BORN-4385] RECV SetPlayerBornDataReq cmdId={} uid={} avatarId={} nickname={}",
                        PacketOpcodes.SetPlayerBornDataReq,
                        player.getUid(),
                        avatarId,
                        req.getNickName());

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

        // 7.1 client static trace:
        //   CmdId 4385 GetCmdId() == 0x1121
        //   its sole protobuf field is int32 field #7, stored at +0x18
        //   handler treats +0x18 as retcode and, on zero, enters the born/UI completion manager.
        // Empty payload therefore represents retcode == 0 and keeps this probe isolated from all
        // login, scene and nickname synchronization.
        Grasscutter.getLogger()
                .info(
                        "[BORN-4385] SEND candidate SetPlayerBornDataRsp cmdId={} payload=<empty>; no onLogin/9582/3064",
                        TEST_BORN_RSP_CMD_ID);
        session.send(new BasePacket(TEST_BORN_RSP_CMD_ID));
        Grasscutter.getLogger().info("[BORN-4385] send() returned");
    }
}
