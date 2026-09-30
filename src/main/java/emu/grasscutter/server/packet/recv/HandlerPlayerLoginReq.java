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
    private static final int DO_SET_PLAYER_BORN_DATA_NOTIFY_CMD_ID = 22899;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();

        if (player.getAvatars().getAvatarCount() == 0) {
            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new BasePacket(DO_SET_PLAYER_BORN_DATA_NOTIFY_CMD_ID));
            Grasscutter.getLogger()
                    .info(
                            "[BORN] fresh account: sent DoSetPlayerBornDataNotify cmdId={}; waiting for SetPlayerBornDataReq.",
                            DO_SET_PLAYER_BORN_DATA_NOTIFY_CMD_ID);

            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        BornDataHelper.ensureMainCharacter(player);
        player.onLogin();
        session.send(new PacketPlayerLoginRsp(session));
    }
}
