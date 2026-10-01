package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBackMyWorldRsp;

@Opcodes(MultiplayerProtocol71.BACK_MY_WORLD_REQ)
public class HandlerBackMyWorldReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();

        if (player.getWorld() != null && player.getWorld().isMultiplayer()) {
            boolean result = session.getServer().getMultiplayerSystem().leaveCoop(player);
            session.send(new PacketBackMyWorldRsp(result ? 0 : RetcodeOuterClass.Retcode.RET_FAIL_VALUE));
            return;
        }

        int prevScene = player.getPrevScene();

        // Sanity check for switching between teapot realms
        if (prevScene >= 2000 && prevScene <= 2400) {
            prevScene = 3;
        }

        boolean result = session.getServer().getHomeWorldMPSystem().leaveCoop(player, prevScene);
        session.send(new PacketBackMyWorldRsp(result ? 0 : RetcodeOuterClass.Retcode.RET_FAIL_VALUE));
    }
}
