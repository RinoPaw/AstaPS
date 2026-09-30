package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {
    private static final int TEST_DO_SET_PLAYER_BORN_DATA_NOTIFY = 22899;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();

        if (player.getAvatars().getAvatarCount() == 0) {
            // Dedicated 7.1 PlayerNicknameNotify=3064 page-completion probe.
            Grasscutter.getConfig().server.game.logPackets = Grasscutter.ServerDebugMode.ALL;

            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new BasePacket(TEST_DO_SET_PLAYER_BORN_DATA_NOTIFY));
            Grasscutter.getLogger()
                    .info(
                            "[born-nickname-3064] fresh account: sent DoSetPlayerBornDataNotify cmdId={}; waiting for SetPlayerBornDataReq.",
                            TEST_DO_SET_PLAYER_BORN_DATA_NOTIFY);

            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        BornDataHelper.ensureMainCharacter(player);
        player.onLogin();
        session.send(new PacketPlayerLoginRsp(session));
    }
}
