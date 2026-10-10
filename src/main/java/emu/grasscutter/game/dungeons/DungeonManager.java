package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.game.activity.trialavatar.TrialAvatarActivityHandler;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.event.player.PlayerFinishDungeonEvent;
import emu.grasscutter.server.packet.send.*;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.stream.*;
import javax.annotation.Nullable;
import lombok.*;

/**
 * TODO handle time limits TODO handle respawn points TODO handle team wipes and respawns TODO check
 * monster level and levelConfigMap
 */
public final class DungeonManager {
    @Getter private final Scene scene;
    @Getter private final DungeonData dungeonData;
    @Getter private final DungeonPassConfigData passConfigData;

    @Getter private final int[] finishedConditions;
    private final IntSet rewardedPlayers = new IntOpenHashSet();
    private final Set<Integer> activeDungeonWayPoints = new HashSet<>();
    private boolean ended = false;
    private int newestWayPoint = 0;
    @Getter private int startSceneTime = 0;
    @Setter @Getter private boolean towerDungeon = false;

    DungeonTrialTeam trialTeam = null;

    public DungeonManager(@NonNull Scene scene, @NonNull DungeonData dungeonData) {
        this.scene = scene;
        this.dungeonData = dungeonData;
        if (dungeonData.getPassCond() == 0) {
            this.passConfigData = new DungeonPassConfigData();
            this.passConfigData.setConds(new ArrayList<>());
        } else {
            this.passConfigData = GameData.getDungeonPassConfigDataMap().get(dungeonData.getPassCond());
        }
        this.finishedConditions = new int[this.passConfigData.getConds().size()];
    }

    public void triggerEvent(DungeonPassConditionType conditionType, int... params) {
        if (ended) {
            return;
        }
        for (int i = 0; i < passConfigData.getConds().size(); i++) {
            var cond = passConfigData.getConds().get(i);
            if (conditionType == cond.getCondType()) {
                if (getScene().getWorld().getServer().getDungeonSystem().triggerCondition(cond, params)) {
                    finishedConditions[i] = 1;
                }
            }
        }

        if (isFinishedSuccessfully()) {
            // Spiral Abyss mid-half: upper challenge success can satisfy pass conditions before
            // the lower team is applied. Settling now advances the chamber and cancels the swap.
            if (isTowerDungeon() && !scene.getPlayers().isEmpty()) {
                var tower = scene.getPlayers().get(0).getTowerManager();
                if (tower != null && tower.isMidHalfCutscenePending()) {
                    Grasscutter.getLogger()
                            .info(
                                    "Tower finishDungeon deferred uid={} - mid-half team swap pending",
                                    scene.getPlayers().get(0).getUid());
                    return;
                }
                if (scene.getLoadedGroups().stream()
                        .anyMatch(
                                g -> {
                                    var variables = scene.getScriptManager().getVariables(g.id);
                                    return variables != null
                                            && variables.containsKey("stage")
                                            && Integer.valueOf(1).equals(variables.get("stage"));
                                })) {
                    Grasscutter.getLogger()
                            .info(
                                    "Tower finishDungeon deferred uid={} - lua stage=1 (lower half pending)",
                                    scene.getPlayers().get(0).getUid());
                    return;
                }
            }
            // Set ended now because calling EVENT_DUNGEON_SETTLE
            // during finishDungeon() may cause reentrance into
            // this function, leading to double settles.
            ended = true;
            finishDungeon();
        }
    }

    public boolean isFinishedSuccessfully() {
        if (passConfigData.getConds() == null) return false;
        return LogicType.calculate(passConfigData.getLogicType(), finishedConditions);
    }

    public int getLevelForMonster(int id) {
        if (isTowerDungeon()) {
            // Tower dungeons have their own level setting in TowerLevelData
            return scene.getPlayers().get(0).getTowerManager().getCurrentMonsterLevel();
        } else {
            // TODO should use levelConfigMap? and how?
            return dungeonData.getShowLevel();
        }
    }

    public boolean activateRespawnPoint(int pointId) {
        val respawnPoint = GameData.getScenePointEntryById(scene.getId(), pointId);

        if (respawnPoint == null) {
            Grasscutter.getLogger().warn("trying to activate unknown respawn point {}", pointId);
            return false;
        }

        scene.broadcastPacket(
                new PacketDungeonWayPointNotify(
                        activeDungeonWayPoints.add(pointId), activeDungeonWayPoints));
        newestWayPoint = pointId;

        Grasscutter.getLogger().debug("[unimplemented respawn] activated respawn point {}", pointId);
        return true;
    }

    @Nullable public Position getRespawnLocation() {
        if (newestWayPoint == 0) { // validity is checked before setting it, so if != 0 its always valid
            return null;
        }
        var pointData = GameData.getScenePointEntryById(scene.getId(), newestWayPoint).getPointData();
        return pointData.getTranPos() != null ? pointData.getTranPos() : pointData.getPos();
    }

    public Position getRespawnRotation() {
        if (newestWayPoint == 0) { // validity is checked before setting it, so if != 0 its always valid
            return null;
        }
        val pointData = GameData.getScenePointEntryById(scene.getId(), newestWayPoint).getPointData();
        return pointData.getRot() != null ? pointData.getRot() : null;
    }

    public boolean getStatueDrops(Player player, boolean useCondensed, int groupId) {
        // The legacy claim entry point must use the same checked reward path. Preserve
        // its historical 2x condensed multiplier; the 7.1 interaction mode uses 3x.
        return DomainStatueDropService.claim(
                player,
                this,
                useCondensed
                        ? DomainStatueClaimHelper.ClaimMode.CONDENSE
                        : DomainStatueClaimHelper.ClaimMode.NORMAL_1X,
                groupId,
                useCondensed ? 2 : 1);
    }

    public boolean handleCost(Player player, boolean useCondensed) {
        int resinCost = dungeonData.getStatueCostCount() != 0 ? dungeonData.getStatueCostCount() : 20;
        if (resinCost == 0) {
            return true;
        }
        if (useCondensed) {
            // Check if condensed resin is usable here.
            // For this, we use the following logic for now:
            // The normal resin cost of the dungeon has to be 20.
            if (resinCost != 20) {
                return false;
            }

            // Spend the condensed resin and only proceed if the transaction succeeds.
            return player.getResinManager().useCondensedResin(1);
        } else if (dungeonData.getStatueCostID() == 106) {
            // Spend the resin and only proceed if the transaction succeeds.
            return player.getResinManager().useResin(resinCost);
        }
        return true;
    }

    public void applyTrialTeam(Player player) {
        if (getDungeonData() == null) return;

        // TPS dungeons only allow the TPS traveler, which differs per player (boy or girl).
        var tpsTeam = TpsAvatarSystem.getTrialTeam(player, this.scene);
        if (tpsTeam != null) {
            this.trialTeam = tpsTeam;
            player.getTeamManager().addTrialAvatars(tpsTeam.getTrialAvatarIds());
            return;
        }

        // Types this server does not know load as null.
        if (getDungeonData().getType() == null) return;

        switch (getDungeonData().getType()) {
                // case DUNGEON_PLOT is handled by quest execs
            case DUNGEON_ACTIVITY -> {
                switch (getDungeonData().getPlayType()) {
                    case DUNGEON_PLAY_TYPE_TRIAL_AVATAR -> {
                        val activityHandler =
                                player
                                        .getActivityManager()
                                        .getActivityHandlerAs(
                                                ActivityType.NEW_ACTIVITY_TRIAL_AVATAR, TrialAvatarActivityHandler.class);
                        activityHandler.ifPresent(
                                trialAvatarActivityHandler ->
                                        this.trialTeam = trialAvatarActivityHandler.getTrialAvatarDungeonTeam());
                    }
                }
            }
            case DUNGEON_ELEMENT_CHALLENGE -> {} // TODO
        }

        if (this.trialTeam != null) {
            player.getTeamManager().addTrialAvatars(trialTeam.trialAvatarIds);
        }
    }

    public void unsetTrialTeam(Player player) {
        if (this.trialTeam == null) return;

        player.getTeamManager().removeTrialAvatar();
        this.trialTeam = null;
    }

    public void startDungeon() {
        this.startSceneTime = scene.getSceneTimeSeconds();
        scene
                .getPlayers()
                .forEach(
                        p -> {
                            p.getQuestManager()
                                    .queueEvent(QuestContent.QUEST_CONTENT_ENTER_DUNGEON, dungeonData.getId());
                            applyTrialTeam(p);
                        });
    }

    public void finishDungeon() {
        this.notifyEndDungeon(true);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.COMPLETED);

        // Call PlayerFinishDungeonEvent.
        new PlayerFinishDungeonEvent(this.getScene().getPlayers(), this.getScene(), this).call();

        // jump players to next dungeon if available
        if (this.dungeonData.getPassJumpDungeon() != 0) {
            for (var player : this.getScene().getPlayers()) {
                player
                        .getServer()
                        .getDungeonSystem()
                        .enterDungeon(player, 0, this.dungeonData.getPassJumpDungeon(), false);
            }
        }
    }

    public void quitDungeon() {
        this.notifyEndDungeon(false);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.QUIT);
    }

    public void failDungeon() {
        this.notifyEndDungeon(false);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.FAILED);
    }

    public void notifyEndDungeon(boolean successfully) {
        scene
                .getPlayers()
                .forEach(
                        p -> {
                            // Trigger the fail and success event.
                            if (successfully) {
                                var dungeonId = this.getDungeonData().getId();
                                p.getPlayerProgress().markDungeonAsComplete(dungeonId);
                                try {
                                    emu.grasscutter.game.player.InvestigationHandbookHelper.trigger(
                                            p,
                                            WatcherTriggerType.TRIGGER_TAKE_DUNGEON_FIRST_PASS_REWARD,
                                            dungeonId,
                                            1);
                                } catch (Throwable ignored) {
                                }
                            } else {
                                p.getQuestManager()
                                        .queueEvent(QuestContent.QUEST_CONTENT_FAIL_DUNGEON, dungeonData.getId());
                            }

                            // Battle pass trigger
                            if (dungeonData.getType().isCountsToBattlepass() && successfully) {
                                p.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_FINISH_DUNGEON);
                            }
                        });
        var future =
                scene
                        .getScriptManager()
                        .callEvent(new ScriptArgs(0, EventType.EVENT_DUNGEON_SETTLE, successfully ? 1 : 0));
        // Note: There is a possible race condition with calling
        //       EVENT_DUNGEON_SETTLE here asynchronously:
        // 1. EVENT_DUNGEON_SETTLE triggers some Lua-side logic,
        //    which may happen after 2 (below) finishes.
        // 2. Some DungeonSettleListener could be comparing some
        //    Lua variable before its setting in 1 (above) finishes.
        // For safety, ensure all events have finished before returning.
        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (successfully) {
            try {
                WeeklyBossModelCleanup.sweepAfterSettle(scene);
            } catch (Throwable ignored) {
            }
        }
    }

    public void endDungeon(BaseDungeonResult.DungeonEndReason endReason) {
        if (scene.getDungeonSettleListeners() != null) {
            scene.getDungeonSettleListeners().forEach(o -> o.onDungeonSettle(this, endReason));
        }
        if (isTowerDungeon()) {
            scene.getPlayers().get(0).getTowerManager().onEnd();
        }
        ended = true;
    }

    public void restartDungeon() {
        this.scene.setKilledMonsterCount(0);
        this.rewardedPlayers.clear();
        Arrays.fill(finishedConditions, 0);
        this.ended = false;
        this.activeDungeonWayPoints.clear();
    }

    public void cleanUpScene() {
        this.scene.setDungeonManager(null);
        this.scene.setKilledMonsterCount(0);
    }
}
