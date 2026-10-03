package emu.grasscutter.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.utils.JsonUtils;

/** Restores the native Quest-303 statue unlock chain when the bundled quest dump is incomplete. */
public final class StatueUnlockQuestBridge {
    private static final int MAIN_QUEST_ID = 303;
    private static final int SCENE_ID = 3;
    private static final Object QUEST_DATA_LOCK = new Object();

    private StatueUnlockQuestBridge() {}

    /**
     * Prepare one Statue-of-the-Seven activation talk for the normal quest event path.
     *
     * <p>Older private-server compatibility code exposes statue Talk options by presenting the
     * 303xx gate as FINISHED. That makes the client interaction available, but a FINISHED server
     * quest cannot consume QUEST_CONTENT_COMPLETE_TALK and therefore cannot execute its unlock
     * finishExec. Immediately before the activation talk we restore only that locked statue's quest
     * to UNFINISHED. TalkManager then drives the normal COMPLETE_TALK -> GameQuest.finish() chain.
     */
    public static boolean prepareForTalk(Player player, int talkId) {
        if (player == null || !StatueTalkQuests.isUnlockQuest(talkId)) return false;

        int pointId = StatueTalkQuests.pointForQuest(talkId);
        if (pointId <= 0) return false;

        boolean alreadyUnlocked =
                !player.isScenePointForceLocked(SCENE_ID, pointId)
                        && player.getUnlockedScenePoints(SCENE_ID).contains(pointId);
        if (alreadyUnlocked) return true;

        var questData = ensureQuestData(talkId, pointId);
        if (questData == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Cannot prepare statue unlock quest uid={} talk={} point={}: no quest data",
                            player.getUid(),
                            talkId,
                            pointId);
            return false;
        }

        var questManager = player.getQuestManager();
        var mainQuest = questManager.getMainQuestById(MAIN_QUEST_ID);
        if (mainQuest == null) {
            if (GameData.getMainQuestDataMap().get(MAIN_QUEST_ID) == null) {
                Grasscutter.getLogger()
                        .warn(
                                "Cannot prepare statue unlock quest uid={} talk={}: main quest 303 is missing",
                                player.getUid(),
                                talkId);
                return false;
            }
            mainQuest = questManager.addMainQuest(questData);
        }

        GameQuest quest = mainQuest.getChildQuestById(talkId);
        if (quest == null) {
            // Missing children are compatibility-only and are removed after finish so an incomplete
            // resource dump can never leave an unknown quest id persisted in MongoDB.
            quest = new EphemeralStatueQuest(mainQuest, questData);
            mainQuest.getChildQuests().put(talkId, quest);
        } else {
            quest.setConfig(questData);
        }

        // A client-only forged gate may have used this id earlier in the session. From this point on
        // the real GameQuest owns it.
        player.getForgedStatueTalkQuests().remove(talkId);

        if (quest.getState() != QuestState.QUEST_STATE_UNFINISHED) {
            QuestState oldState = quest.getState();
            quest.setState(QuestState.QUEST_STATE_UNSTARTED);
            quest.setFinishTime(0);
            quest.start();
            Grasscutter.getLogger()
                    .debug(
                            "Prepared statue unlock quest uid={} quest={} point={} state={} -> UNFINISHED",
                            player.getUid(),
                            talkId,
                            pointId,
                            oldState);
        }

        return true;
    }

    private static QuestData ensureQuestData(int questId, int pointId) {
        var existing = GameData.getQuestDataMap().get(questId);
        if (existing != null) return existing;

        synchronized (QUEST_DATA_LOCK) {
            existing = GameData.getQuestDataMap().get(questId);
            if (existing != null) return existing;

            var root = new JsonObject();
            root.addProperty("subId", questId);
            root.addProperty("mainId", MAIN_QUEST_ID);
            root.addProperty("order", questId - 30301);
            root.addProperty("acceptCondComb", "LOGIC_NONE");
            root.addProperty("finishCondComb", "LOGIC_NONE");
            root.addProperty("failCondComb", "LOGIC_NONE");
            root.add("acceptCond", new JsonArray());
            root.add("failCond", new JsonArray());
            root.add("beginExec", new JsonArray());
            root.add("failExec", new JsonArray());
            root.add("gainItems", new JsonArray());
            root.add("trialAvatarList", new JsonArray());

            var finishCond = new JsonArray();
            var talkCond = new JsonObject();
            talkCond.addProperty("type", "QUEST_CONTENT_COMPLETE_TALK");
            var talkParams = new JsonArray();
            talkParams.add(questId);
            talkParams.add(0);
            talkCond.add("param", talkParams);
            talkCond.addProperty("param_str", "");
            finishCond.add(talkCond);
            root.add("finishCond", finishCond);

            var finishExec = new JsonArray();
            finishExec.add(exec("QUEST_EXEC_UNLOCK_POINT", SCENE_ID, pointId));
            for (int areaId : StatueTalkQuests.unlockAreasForQuest(questId)) {
                finishExec.add(exec("QUEST_EXEC_UNLOCK_AREA", SCENE_ID, areaId));
            }
            if (StatueTalkQuests.addsQuestProgress31801(questId)) {
                finishExec.add(exec("QUEST_EXEC_ADD_QUEST_PROGRESS", 31801, 1));
            }
            root.add("finishExec", finishExec);

            var data = JsonUtils.decode(root, QuestData.class);
            if (data == null) return null;
            // Deliberately do not call QuestData.onLoad(): these compatibility rows must not enter
            // the global accept-condition cache, where enableQuests() could try to auto-start a
            // child absent from the bundled MainQuest/303 resource.
            GameData.getQuestDataMap().put(questId, data);
            Grasscutter.getLogger()
                    .debug(
                            "Synthesized missing 7.1 statue quest {} point={} areas={}",
                            questId,
                            pointId,
                            java.util.Arrays.toString(StatueTalkQuests.unlockAreasForQuest(questId)));
            return data;
        }
    }

    private static JsonObject exec(String type, int... params) {
        var object = new JsonObject();
        object.addProperty("type", type);
        var array = new JsonArray();
        for (int param : params) array.add(Integer.toString(param));
        object.add("param", array);
        return object;
    }

    private static final class EphemeralStatueQuest extends GameQuest {
        EphemeralStatueQuest(GameMainQuest mainQuest, QuestData questData) {
            super(mainQuest, questData);
        }

        @Override
        public void save() {
            // The resource dump does not contain this child, so do not persist it into MainQuest 303.
        }

        @Override
        public void finish() {
            super.finish();
            getMainQuest().getChildQuests().remove(getSubQuestId(), this);
        }
    }
}
