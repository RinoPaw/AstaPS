package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetOnlinePlayerListRspOuterClass.GetOnlinePlayerListRsp;
import java.util.List;

public class PacketGetOnlinePlayerListRsp extends BasePacket {
    public PacketGetOnlinePlayerListRsp(Player session) {
        super(PacketOpcodes.GetOnlinePlayerListRsp);

        List<Player> players =
                Grasscutter.getGameServer().getPlayers().values().stream().limit(50).toList();

        GetOnlinePlayerListRsp.Builder proto = GetOnlinePlayerListRsp.newBuilder();
        for (Player player : players) {
            if (player == null || player.getUid() == session.getUid()) continue;

            try {
                proto.addPlayerInfoList(player.getOnlinePlayerInfo());
            } catch (RuntimeException ignored) {
                // Keep one malformed player from suppressing the entire online-player response.
            }
        }

        this.setData(proto);
    }
}
