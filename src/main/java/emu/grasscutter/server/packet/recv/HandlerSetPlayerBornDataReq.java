package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {
    private static final int TEST_BORN_RSP_CMD_ID = 4385;
    private static final int PLAYER_NICKNAME_NOTIFY_CMD_ID = 3064;
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
            Grasscutter.getLogger().warn("[BORN-ACK-351] invalid Traveler id {}.", avatarId);
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
                            "[BORN-ACK-351] ignoring duplicate SetPlayerBornDataReq for uid {}; Traveler {} is already persisted.",
                            player.getUid(),
                            player.getMainCharacterId());
            return;
        }

        Grasscutter.getLogger()
                .info(
                        "[BORN-ACK-351] 1 RECV SetPlayerBornDataReq cmdId={} uid={} avatarId={} nickname={}",
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

        GameServerPacketHandler.beginBornIntroTrace(session);

        // Starlight's born lifecycle acknowledges the client's selection first, then emits its
        // PlayerBorn lifecycle. Test that ordering here while keeping our existing quest bootstrap
        // otherwise unchanged.
        Grasscutter.getLogger()
                .info(
                        "[BORN-ACK-351] 2 SEND SetPlayerBornDataRsp cmdId={} payload=<empty>",
                        TEST_BORN_RSP_CMD_ID);
        session.send(new BasePacket(TEST_BORN_RSP_CMD_ID));

        byte[] nicknamePayload = buildNicknameNotify(req.getNickName());
        BasePacket nicknameNotify = new BasePacket(PLAYER_NICKNAME_NOTIFY_CMD_ID);
        nicknameNotify.setData(nicknamePayload);
        Grasscutter.getLogger()
                .info(
                        "[BORN-ACK-351] 3 SEND PlayerNicknameNotify cmdId={} field={} payload={}",
                        PLAYER_NICKNAME_NOTIFY_CMD_ID,
                        NICKNAME_FIELD_NUMBER,
                        HexFormat.of().formatHex(nicknamePayload));
        session.send(nicknameNotify);

        // Quest startup reads world time immediately. Establish only the minimum world/scene
        // context it needs, matching the previous experiment so packet ordering is the sole change.
        Grasscutter.getLogger().info("[BORN-ACK-351] 4 world bootstrap BEGIN");
        if (player.getWorld() == null) {
            World world = new World(player);
            world.addPlayer(player);
        }
        Grasscutter.getLogger()
                .info(
                        "[BORN-ACK-351] 5 world bootstrap END world={} scene={}",
                        player.getWorld() != null,
                        player.getScene() != null);

        Grasscutter.getLogger().info("[BORN-ACK-351] 6 questManager.onPlayerBorn BEGIN");
        player.getQuestManager().onPlayerBorn();
        Grasscutter.getLogger().info("[BORN-ACK-351] 7 questManager.onPlayerBorn END");

        Grasscutter.getLogger()
                .info(
                        "[BORN-ACK-351] 8 player.onLogin BEGIN sceneLoadState={} enterSceneToken={}",
                        player.getSceneLoadState(),
                        player.getEnterSceneToken());
        player.onLogin();
        Grasscutter.getLogger()
                .info(
                        "[BORN-ACK-351] 9 player.onLogin END sceneLoadState={} enterSceneToken={}",
                        player.getSceneLoadState(),
                        player.getEnterSceneToken());
    }

    private static byte[] buildNicknameNotify(String nickname) {
        byte[] utf8 = nickname.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(2 + utf8.length);
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
