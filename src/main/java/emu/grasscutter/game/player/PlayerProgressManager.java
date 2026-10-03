package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.data.excels.OpenStateData.OpenStateCondType;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// @Entity
public final class PlayerProgressManager extends BasePlayerDataManager {
    /******************************************************************************************************************
     ******************************************************************************************************************
     * OPEN STATES
     ******************************************************************************************************************
     *****************************************************************************************************************/

    public static final Set<Integer> BLACKLIST_OPEN_STATES = Set.of();

    public static final Set<Integer> IGNORED_OPEN_STATES =
            Set.of(
                    1404 // OPEN_STATE_MENGDE_INFUSEDCRYSTAL, causes quest 'Mine Craft' to be given to the
                    // player at the start of the game.
                    // This should be removed when city reputation is implemented.
                    );

    // Open states 7.0 added that sit behind OPEN_STATE_COND_QUEST and nothing else. With questing
    // off nothing ever satisfies that condition, so the features stay locked for good rather than
    // just unlocking late - the Nod-Krai and Natlan menu entries among them. Unlocked outright.
    public static final Set<Integer> QUEST_GATED_7_0_OPEN_STATES =
            Set.of(6701, 6702, 6706, 7011, 7014, 7015, 7016, 7021, 7025, 7055, 7056, 7059);

    // Set of open states that are set per default for all accounts. Can be overwritten by an entry in
    // `map`.
    public static final Set<Integer> DEFAULT_OPEN_STATES =
            GameData.getOpenStateList().stream()
                    .filter(
                            s ->
                                    s.isDefaultState() && !s.isAllowClientOpen() // Actual default-opened states.
                                            || ((s.getCond().size() == 1)
                                                    && (s.getCond().get(0).getCondType()
                                                            == OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL)
                                                    && (s.getCond().get(0).getParam() == 1))
                                            // All states whose unlock we don't handle correctly yet.
                                            || (s.getCond().stream()
                                                    .anyMatch(
                                                            c ->
                                                                    c.getCondType() == OpenStateCondType.OPEN_STATE_OFFERING_LEVEL
                                                                            || c.getCondType()
                                                                                    == OpenStateCondType.OPEN_STATE_CITY_REPUTATION_LEVEL))
                                            || QUEST_GATED_7_0_OPEN_STATES.contains(s.getId())
                                            // Always unlock OPEN_STATE_PAIMON, otherwise the player will not have a
                                            // working chat.
                                            || s.getId() == 1)
                    .map(OpenStateData::getId)
                    .filter(s -> !BLACKLIST_OPEN_STATES.contains(s))
                    .filter(s -> !IGNORED_OPEN_STATES.contains(s))
                    .collect(Collectors.toSet());

    public PlayerProgressManager(Player player) {
        super(player);
    }

    /**********
     * Handler for player login.
     **********/
    public void onPlayerLogin() {
        // Try unlocking open states on player login. This handles accounts where unlock conditions were
        // already met before certain open state unlocks were implemented.
        this.tryUnlockOpenStates(false);

        if (!GAME_OPTIONS.questing.enabled) {
            // Questing-off accounts still use the whole open-state surface.
            this.setOpenState(47, 1, false);
            this.setOpenState(48, 1, false);
            this.setOpenState(1101, 1, false);
            this.setOpenState(1102, 1, false);

            for (var openState : GameData.getOpenStateList()) {
                int id = openState.getId();
                if (BLACKLIST_OPEN_STATES.contains(id) || IGNORED_OPEN_STATES.contains(id)) {
                    continue;
                }
                this.player.getOpenStates().put(id, 1);
            }
        }

        player.getSession().send(new PacketOpenStateUpdateNotify(this.player));

        emu.grasscutter.game.entity.gadget.OfferingHelper.onPlayerLogin(this.player);

        // Quest 303 owns Statue-of-the-Seven activation. Keep every unfinished child active so the
        // matching COMPLETE_TALK event can finish it and execute its unlock point/area actions.
        this.addStatueQuestsOnLogin();

        var sots = this.player.getSotsManager();
        if (sots.getMaxVolume() <= 0) {
            sots.setMaxVolume(Math.min(8500000, 5000 * 100));
        }
        if (sots.getCurrentVolume() <= 0) {
            sots.setCurrentVolume(sots.getMaxVolume());
        }
    }

    /**********
     * Direct getters and setters for open states.
     **********/
    public int getOpenState(int openState) {
        return this.player.getOpenStates().getOrDefault(openState, 0);
    }

    private void setOpenState(int openState, int value, boolean sendNotify) {
        int previousValue = this.player.getOpenStates().getOrDefault(openState, -1 /* non-existent */);

        if (value != previousValue) {
            this.player.getOpenStates().put(openState, value);

            this.player
                    .getQuestManager()
                    .queueEvent(QuestCond.QUEST_COND_OPEN_STATE_EQUAL, openState, value);

            if (sendNotify) {
                player.getSession().send(new PacketOpenStateChangeNotify(openState, value));
            }
        }
    }

    private void setOpenState(int openState, int value) {
        this.setOpenState(openState, value, true);
    }

    /**********
     * Condition checking for setting open states.
     **********/
    private boolean areConditionsMet(OpenStateData openState) {
        for (var condition : openState.getCond()) {
            switch (condition.getCondType()) {
                case OPEN_STATE_COND_PLAYER_LEVEL -> {
                    if (this.player.getLevel() < condition.getParam()) {
                        return false;
                    }
                }
                case OPEN_STATE_COND_QUEST -> {
                    var quest = this.player.getQuestManager().getQuestById(condition.getParam());
                    if (quest == null || quest.getState() != QuestState.QUEST_STATE_FINISHED) {
                        return false;
                    }
                }
                case OPEN_STATE_COND_PARENT_QUEST -> {
                    var mainQuest = this.player.getQuestManager().getMainQuestById(condition.getParam());
                    if (mainQuest == null
                            || mainQuest.getState() != ParentQuestState.PARENT_QUEST_STATE_FINISHED) {
                        return false;
                    }
                }
                case OPEN_STATE_OFFERING_LEVEL, OPEN_STATE_CITY_REPUTATION_LEVEL -> {}
            }
        }

        return true;
    }

    /**********
     * Setting open states from the client (via `SetOpenStateReq`).
     **********/
    public void setOpenStateFromClient(int openState, int value) {
        OpenStateData data = GameData.getOpenStateDataMap().get(openState);
        if (data == null) {
            this.player.sendPacket(new PacketSetOpenStateRsp(Retcode.RET_FAIL));
            return;
        }

        if (!data.isAllowClientOpen() || !this.areConditionsMet(data)) {
            this.player.sendPacket(new PacketSetOpenStateRsp(Retcode.RET_FAIL));
            return;
        }

        this.setOpenState(openState, value);
        this.player.sendPacket(new PacketSetOpenStateRsp(openState, value));
    }

    /** This force sets an open state, ignoring all conditions and permissions */
    public void forceSetOpenState(int openState, int value) {
        this.setOpenState(openState, value);
    }

    /**********
     * Triggered unlocking of open states (unlock states whose conditions have been met.)
     **********/
    public void tryUnlockOpenStates(boolean sendNotify) {
        var lockedStates =
                GameData.getOpenStateList().stream()
                        .filter(s -> this.player.getOpenStates().getOrDefault(s, 0) == 0)
                        .toList();

        for (var state : lockedStates) {
            if (!state.isAllowClientOpen()
                    && this.areConditionsMet(state)
                    && !BLACKLIST_OPEN_STATES.contains(state.getId())
                    && !IGNORED_OPEN_STATES.contains(state.getId())) {
                this.setOpenState(state.getId(), 1, sendNotify);
            }
        }
    }

    public void tryUnlockOpenStates() {
        this.tryUnlockOpenStates(true);
    }

    /******************************************************************************************************************
     ******************************************************************************************************************
     * MAP AREAS AND POINTS
     ******************************************************************************************************************
     *****************************************************************************************************************/
    private void addStatueQuestsOnLogin() {
        var statueMainQuest = GameData.getMainQuestDataMap().get(303);
        var statueSubQuests = statueMainQuest.getSubQuests();

        var statueGameMainQuest = this.player.getQuestManager().getMainQuestById(303);
        if (statueGameMainQuest == null) {
            this.player.getQuestManager().addQuest(30302);
            statueGameMainQuest = this.player.getQuestManager().getMainQuestById(303);
        }

        for (var subData : statueSubQuests) {
            var subGameQuest = statueGameMainQuest.getChildQuestById(subData.getSubId());
            if (subGameQuest != null && subGameQuest.getState() == QuestState.QUEST_STATE_UNSTARTED) {
                this.player.getQuestManager().addQuest(subData.getSubId());
            }
        }
    }

    /** Compatibility entry point; TransPointUnlockHelper owns unlock behavior and rewards. */
    public boolean unlockTransPoint(int sceneId, int pointId, boolean isStatue) {
        return TransPointUnlockHelper.unlock(this.player, sceneId, pointId, isStatue);
    }

    public void unlockSceneArea(int sceneId, int areaId) {
        this.player.getUnlockedSceneAreas(sceneId).add(areaId);
        this.player.sendPacket(new PacketSceneAreaUnlockNotify(sceneId, areaId));
        InvestigationHandbookHelper.trigger(
                this.player,
                emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_UNLOCK_AREA,
                areaId,
                1);
    }

    /** Give replace costume to player (Amber, Jean, Mona, Rosaria) */
    public void addReplaceCostumes() {
        var currentPlayerCostumes = player.getCostumeList();
        GameData.getAvatarReplaceCostumeDataMap()
                .keySet()
                .forEach(
                        costumeId -> {
                            if (GameData.getAvatarCostumeDataMap().get(costumeId) == null
                                    || currentPlayerCostumes.contains(costumeId)) {
                                return;
                            }
                            this.player.addCostume(costumeId);
                        });
    }

    /** Quest progress */
    public void addQuestProgress(int id, int count) {
        var newCount = player.getPlayerProgress().addToCurrentProgress(String.valueOf(id), count);
        player.save();
        player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, id, newCount);
    }

    /** Item history */
    public void addItemObtainedHistory(int id, int count) {
        var newCount = player.getPlayerProgress().addToItemHistory(id, count);
        player.save();
        player.getQuestManager().queueEvent(QuestCond.QUEST_COND_HISTORY_GOT_ANY_ITEM, id, newCount);
    }

    /******************************************************************************************************************
     ******************************************************************************************************************
     * SCENETAGS
     ******************************************************************************************************************
     *****************************************************************************************************************/
    public void addSceneTag(int sceneId, int sceneTagId) {
        player.getSceneTags().computeIfAbsent(sceneId, k -> new HashSet<>()).add(sceneTagId);
        player.sendPacket(new PacketPlayerWorldSceneInfoListNotify(player));
    }

    public void delSceneTag(int sceneId, int sceneTagId) {
        if (player.getSceneTags().get(sceneId) == null) {
            return;
        }
        player.getSceneTags().get(sceneId).remove(sceneTagId);
        player.sendPacket(new PacketPlayerWorldSceneInfoListNotify(player));
    }

    public boolean checkSceneTag(int sceneId, int sceneTagId) {
        return player.getSceneTags().get(sceneId).contains(sceneTagId);
    }
}
