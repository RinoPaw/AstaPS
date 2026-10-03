package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestListNotifyOuterClass.QuestListNotify;

public class PacketQuestListNotify extends BasePacket {

    public PacketQuestListNotify(Player player) {
        super(PacketOpcodes.QuestListNotify, true);

        QuestListNotify.Builder proto = QuestListNotify.newBuilder();

        // With questing off, keep ordinary unfinished story quests hidden so old accounts do not
        // replay story content. Statue activation quests are different: the stock client needs the
        // active 303/352 child state in order to expose the locked-statue interaction.
        var questingEnabled = emu.grasscutter.game.quest.QuestManager.isQuestingActive();

        player
                .getQuestManager()
                .forEachQuest(
                        quest -> {
                            var state = quest.getState();
                            if (state == QuestState.QUEST_STATE_UNSTARTED) return;
                            boolean statueActivationQuest =
                                    quest.getMainQuestId() == 303 || quest.getMainQuestId() == 352;
                            if (!questingEnabled
                                    && !statueActivationQuest
                                    && state != QuestState.QUEST_STATE_FINISHED) return;
                            proto.addQuestList(quest.toProto());
                        });

        this.setData(proto);
    }
}
