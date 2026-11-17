package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

/** 合成角色限制列表需要先初始化，空快照表示没有限制。 */
public final class PacketCraftingAvatarDataNotify extends BasePacket {
    public PacketCraftingAvatarDataNotify() {
        super(PacketOpcodes.CraftingAvatarDataNotify);
        setData(new byte[0]);
    }
}
