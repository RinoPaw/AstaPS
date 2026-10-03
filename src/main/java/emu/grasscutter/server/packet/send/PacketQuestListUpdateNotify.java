package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestListUpdateNotifyOuterClass.QuestListUpdateNotify;
import emu.grasscutter.net.proto.QuestOuterClass.Quest;
import emu.grasscutter.utils.Utils;
import java.util.List;

public class PacketQuestListUpdateNotify extends BasePacket {

    public PacketQuestListUpdateNotify(GameQuest quest) {
        super(PacketOpcodes.QuestListUpdateNotify);

        // Questing-off normally hides unfinished quest state from the client so old accounts do not
        // replay story content. Statue activation is the deliberate exception on this probe branch:
        // quest 303 drives normal statue activation talks, and quest 352 drives the starter statue.
        // Hiding those unfinished children removes the client's activation interaction entirely.
        boolean statueActivationQuest = quest.getMainQuestId() == 303 || quest.getMainQuestId() == 352;
        var builder = QuestListUpdateNotify.newBuilder();
        if (QuestManager.isQuestingActive()
                || statueActivationQuest
                || quest.getState() == QuestState.QUEST_STATE_FINISHED) {
            builder.addQuestList(quest.toProto());
        }
        QuestListUpdateNotify proto = builder.build();

        this.setData(proto);
    }

    /**
     * Push already-built quest protos.
     *
     * <p>The current caller is the legacy statue compatibility batch. On the native-unlock probe
     * branch, do not tell the client that every 303xx activation quest is already FINISHED before
     * the real quest snapshot arrives; doing so suppresses the locked-statue activation surface.
     */
    public PacketQuestListUpdateNotify(List<Quest> quests) {
        super(PacketOpcodes.QuestListUpdateNotify);
        var proto = QuestListUpdateNotify.newBuilder();
        for (Quest quest : quests) {
            if (quest.getParentQuestId() == 303
                    && quest.getState() == QuestState.QUEST_STATE_FINISHED.getValue()) {
                continue;
            }
            proto.addQuestList(quest);
        }

        this.setData(proto);
    }

    /** Push a single forged quest entry. */
    public PacketQuestListUpdateNotify(int questId, int parentQuestId, int state) {
        super(PacketOpcodes.QuestListUpdateNotify);

        this.setData(
                QuestListUpdateNotify.newBuilder()
                        .addQuestList(forgeQuest(questId, parentQuestId, state)));
    }

    /** Build a client-only quest entry when no real server quest object is available. */
    public static Quest forgeQuest(int questId, int parentQuestId, int state) {
        int now = Utils.getCurrentSeconds();
        return Quest.newBuilder()
                .setQuestId(questId)
                .setState(state)
                .setParentQuestId(parentQuestId)
                .setStartTime(now)
                .setStartGameTime(438)
                .setAcceptTime(now)
                .build();
    }
}
