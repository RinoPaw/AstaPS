package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {
    private static final int TEST_PLAYER_NICKNAME_NOTIFY = 3064;
    private static final int NICKNAME_FIELD_NUMBER = 12;

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
            Grasscutter.getLogger()
                    .warn("[born-nickname-3064] invalid Traveler id {}.", avatarId);
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
                            "[born-nickname-3064] ignoring duplicate SetPlayerBornDataReq for uid {}; Traveler {} is already persisted.",
                            player.getUid(),
                            player.getMainCharacterId());
            return;
        }

        Grasscutter.getLogger()
                .info(
                        "[born-nickname-3064] RECV SetPlayerBornDataReq cmdId={} avatarId={} nickname={}",
                        PacketOpcodes.SetPlayerBornDataReq,
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

        byte[] nicknamePayload = buildNicknameNotify(req.getNickName());
        BasePacket nicknameNotify = new BasePacket(TEST_PLAYER_NICKNAME_NOTIFY);
        nicknameNotify.setData(nicknamePayload);

        Grasscutter.getLogger()
                .info(
                        "[born-nickname-3064] SEND PlayerNicknameNotify cmdId={} field={} nickname={} payload={}",
                        TEST_PLAYER_NICKNAME_NOTIFY,
                        NICKNAME_FIELD_NUMBER,
                        req.getNickName(),
                        HexFormat.of().formatHex(nicknamePayload));
        session.send(nicknameNotify);

        // Deliberately stop here. No guessed SetPlayerBornDataRsp and no onLogin/scene packets are
        // sent in this probe, so any change to the naming page is attributable to cmd 3064 alone.
    }

    private static byte[] buildNicknameNotify(String nickname) {
        byte[] utf8 = nickname.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(2 + utf8.length);

        // string field #12: (12 << 3) | wire-type 2 = 0x62.
        out.write((NICKNAME_FIELD_NUMBER << 3) | 2);
        writeVarint(out, utf8.length);
        out.writeBytes(utf8);
        return out.toByteArray();
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
