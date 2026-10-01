package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

public class PacketHomeAvatarRewardEventNotify extends BasePacket {
    public PacketHomeAvatarRewardEventNotify(Player homeOwner) {
        super(PacketOpcodes.HomeAvatarRewardEventNotify);

        // A brand-new player has no realm selected yet, so the fresh HomeWorld intentionally has no
        // HomeModuleManager on the first open-world login. An empty notify is valid until a realm is
        // selected and avoids coupling the native born handoff to teapot initialization.
        var homeWorld = homeOwner.getCurHomeWorld();
        var moduleManager = homeWorld == null ? null : homeWorld.getModuleManager();
        if (moduleManager != null) {
            this.setData(moduleManager.toRewardEventProto());
        }
    }
}
