package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

public class PacketGetMapMarkTipsRsp extends BasePacket {
    public PacketGetMapMarkTipsRsp() {
        super(PacketOpcodes.GetMapMarkTipsRsp);
        // 空列表成功回包，retcode 的默认值就是 0。
        this.setData(new byte[0]);
    }
}
