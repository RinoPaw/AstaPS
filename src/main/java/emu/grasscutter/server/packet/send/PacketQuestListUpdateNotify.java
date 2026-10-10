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

        // Quest events still start quests with questing off; keep their unfinished states off the
        // client, the same as the login quest list does.
        var builder = QuestListUpdateNotify.newBuilder();
        boolean questingActive = QuestManager.isQuestingActive();
        if (includesQuestState(questingActive, quest.getState())) {
            builder.addQuestList(quest.toProto());
        }
        if (quest.getSubQuestId() == 35601
                || quest.getSubQuestId() == 35602
                || quest.getSubQuestId() == 35603) {
            emu.grasscutter.Grasscutter.getLogger().info(
                    "[AmberGuide] QuestListUpdateNotify uid={} quest={} state={} entries={} "
                            + "questingActive={} questingEnabled={} worldScriptsEnabled={}",
                    quest.getOwner().getUid(), quest.getSubQuestId(), quest.getState(),
                    builder.getQuestListCount(), questingActive,
                    emu.grasscutter.config.Configuration.GAME_OPTIONS.questing.enabled,
                    emu.grasscutter.config.Configuration.SERVER.game.enableScriptInBigWorld);
        }
        QuestListUpdateNotify proto = builder.build();

        this.setData(proto);
    }

    static boolean includesQuestState(boolean questingActive, QuestState state) {
        return questingActive || state == QuestState.QUEST_STATE_FINISHED;
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
