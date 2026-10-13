package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonEntryBlockReasonOuterClass.DungeonEntryBlockReason;
import emu.grasscutter.net.proto.DungeonEntryCondOuterClass.DungeonEntryCond;
import emu.grasscutter.net.proto.GetDungeonEntryExploreConditionRspOuterClass.GetDungeonEntryExploreConditionRsp;

public class PacketGetDungeonEntryExploreConditionRsp extends BasePacket {
    public PacketGetDungeonEntryExploreConditionRsp(int entryConfigId) {
        super(PacketOpcodes.GetDungeonEntryExploreConditionRsp);

        DungeonEntryData data = GameData.getDungeonEntryDataMap().get(entryConfigId);
        var rsp = GetDungeonEntryExploreConditionRsp.newBuilder();
        if (data == null) {
            rsp.setRetcode(1);
        } else {
            // The 7.1 resource uses a level/quest OR gate for guarded entrances.
            // Prefer the level requirement for the blocked UI, as in the original packet.
            int level = data.getLevelCondition();
            int quest = data.getQuestCondition();
            var reason = DungeonEntryBlockReason.DungeonEntryBlockReason_DUNGEON_ENTRY_REASON_LEVEL;
            int param = level;
            if (level == 0 && quest > 0) {
                // Resolve by name so an enum absent from a protocol variant is not a hard dependency.
                for (var candidate : DungeonEntryBlockReason.values()) {
                    if (candidate.name().endsWith("DUNGEON_ENTRY_REASON_QUEST")) {
                        reason = candidate;
                        param = quest;
                        break;
                    }
                }
            }
            rsp.setRetcode(0)
                    .setDungeonEntryCond(
                            DungeonEntryCond.newBuilder().setCondReason(reason).setParam1(param));
        }
        this.setData(rsp.build());
    }
}
