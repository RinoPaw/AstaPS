package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
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

        // Quest events still start quests with questing off; keep their unfinished states off the
        // client, the same as the login quest list does.
        var builder = QuestListUpdateNotify.newBuilder();
        if (QuestManager.isQuestingActive() || quest.getState() == QuestState.QUEST_STATE_FINISHED) {
            // 7.1 re-enters the opening plot when it is told that 35100 became UNFINISHED after
            // finishing 35104. Keep 35100 server-side so its real region trigger (1053) remains
            // active, but do not expose this transient state to the client. Once the player really
            // reaches Paimon, 35100 finishes through the region trigger and normal quest sync
            // resumes with 35101.
            if (quest.getSubQuestId() == 35100
                    && quest.getState() == QuestState.QUEST_STATE_UNFINISHED) {
                Grasscutter.getLogger()
                        .info(
                                "[quest351-replay-fix] suppress start QuestListUpdate uid={} sub=35100",
                                quest.getOwner().getUid());
            } else {
                builder.addQuestList(quest.toProto());
            }
        }
        QuestListUpdateNotify proto = builder.build();

        this.setData(proto);
    }

    /**
     * Push already-built quest protos.
     *
     * <p>This used to take {@code List<GameQuest>}, but the statue Talk gates have no QuestExcel row
     * to build a {@link GameQuest} from - they are forged straight into protos by
     * {@link #forgeQuest}. Erasure allows only one {@code List} overload, and nothing called the
     * {@code GameQuest} one (single quests go through the constructor above), so this takes protos.
     */
    public PacketQuestListUpdateNotify(List<Quest> quests) {
        super(PacketOpcodes.QuestListUpdateNotify);
        var proto = QuestListUpdateNotify.newBuilder();
        for (Quest quest : quests) {
            proto.addQuestList(quest);
        }

        this.setData(proto);
    }

    /** Push a single forged quest entry. */
    public PacketQuestListUpdateNotify(int questId, int parentQuestId, int state) {
        super(PacketOpcodes.QuestListUpdateNotify);

        this.setData(QuestListUpdateNotify.newBuilder().addQuestList(forgeQuest(questId, parentQuestId, state)));
    }

    /**
     * Build a client-only quest entry for a quest the server has no excel row for.
     *
     * <p>Statue Talk options are gated behind 303xx quests that private-server resources do not
     * ship. The client only checks the state it was told, so handing it a FINISHED entry opens the
     * Talk without a playthrough. Fields mirror {@link GameQuest#toProto()} so the forged entry is
     * indistinguishable from a real one - including the fixed {@code startGameTime} 438 that the
     * real path also sends.
     *
     * @param questId sub-quest id
     * @param parentQuestId owning main-quest id
     * @param state {@code QuestState} value, e.g. 3 for {@code QUEST_STATE_FINISHED}
     */
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
