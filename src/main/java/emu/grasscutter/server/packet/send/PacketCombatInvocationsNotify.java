package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.CombatSequence;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import java.util.List;

public class PacketCombatInvocationsNotify extends BasePacket {

    public PacketCombatInvocationsNotify(CombatInvokeEntry entry) {
        super(PacketOpcodes.CombatInvocationsNotify, true);

        CombatInvocationsNotify proto =
                CombatInvocationsNotify.newBuilder()
                        .setClientSequenceId(CombatSequence.next())
                        .addInvokeList(entry)
                        .build();

        this.setData(proto);
    }

    public PacketCombatInvocationsNotify(List<CombatInvokeEntry> entries) {
        super(PacketOpcodes.CombatInvocationsNotify, true);

        CombatInvocationsNotify proto =
                CombatInvocationsNotify.newBuilder()
                        .setClientSequenceId(CombatSequence.next())
                        .addAllInvokeList(entries)
                        .build();

        this.setData(proto);
    }
}
