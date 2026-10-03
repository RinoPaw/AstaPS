package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME;
import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.TowerFloorData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import lombok.*;

public class TowerManager extends BasePlayerManager {
    /**
     * Mid-half transition cutscene: {@code Cs_TowerV2_Stands_Down_Convert}. It used to be 59, which
     * is an Enkanomiya cutscene, together with a ShowLoadingScreen whose random LoadingTips text
     * (including client test strings) showed up between the halves. The official mid-level notify
     * that shows the proper text has no known 7.1 CmdId.
     */
    private static final int MID_HALF_CUTSCENE_ID = 150;
    /**
     * Minimum real-time seconds before the team swap. The swap normally happens when the cutscene
     * finishes; {@link #MID_HALF_FALLBACK_SECONDS} covers a cutscene that never reports back.
     * {@link emu.grasscutter.server.scheduler.ServerTaskScheduler} delays are in <b>seconds</b>
     * (one scheduler tick ≈ one wall-clock second), not {@code tickRateMs} game-loop ticks.
     */
    private static final int MID_HALF_TRANSITION_SECONDS = 1;
    private static final int MID_HALF_FALLBACK_SECONDS = 10;

    private static final List<DungeonSettleListener> towerDungeonSettleListener =
            List.of(new TowerDungeonSettleListener());

    private int currentPossibleStars = 0;
    @Getter private boolean inProgress;
    @Getter private int currentTimeLimit;
    private boolean forceChamberOneOnEnter;
    /**
     * After "reconfigure team", Lua must not ActiveChallenge again — that restarts the timer and blocks
     * the in-dungeon team UI (logs show getCurLevelStars resume with remaining≈full).
     */
    @Getter private boolean awaitingTeamReconfigure;
    /** Last chamber star count computed while a challenge was still available. */
    private int lastSettledStars = 0;

    /** Pending lower-half swap while cutscene 59 / loading tip is showing. */
    @Getter private boolean midHalfCutscenePending;
    private int pendingMidHalfTeamId = -1;
    private Position pendingMidHalfPos;
    private Position pendingMidHalfRot;
    /** Wall-clock earliest time the mid-half swap may run (CutSceneFinish is often instant). */
    private long midHalfEarliestSwapMs;
    /**
     * Snapshot of abyss team GUIDs at mid-half start. {@link TowerData#abyssTeamGuids} can be
     * cleared by a premature chamber settle / exit race before the timed swap runs.
     */
    private List<List<Long>> pendingMidHalfTeamGuids;
    /**
     * Remaining seconds after the upper half success ({@code TPL_TIME}). Kept on the manager so lower-half
     * {@code ActiveChallenge} cannot fall back to elapsed/~27s if the lua group var is raced.
     */
    @Getter private int abyssCarryRemainingSeconds;

    /**
     * Active ConfigLevelEntity names for this tower run (abyssal moon blessing + floor LevelEntity). Empty outside
     * a tower dungeon handoff.
     */
    @Getter private final List<String> activeLevelEntityConfigs = new ArrayList<>();

    public TowerManager(Player player) {
        super(player);
    }

    public TowerData getTowerData() {
        return this.getPlayer().getTowerData();
    }

    public int getSkipToFloorIndex() {
        int skip = getTowerData().skipToFloorIndex;
        return skip < 9 ? 9 : skip;
    }

    public int getSkipFloorState() {
        return getTowerData().skipFloorState;
    }

    public Map<Integer, Integer> getSkipFloorGrantedRewards() {
        var rewards = getTowerData().skipFloorGrantedRewards;
        return rewards != null ? rewards : Map.of();
    }

    public int getCurrentFloorId() {
        return this.getTowerData().currentFloorId;
    }

    /** floor number: 1 - 12, or 0 when no floor is selected * */
    public int getCurrentFloorNumber() {
        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        return floorData != null ? floorData.getFloorIndex() : 0;
    }

    public int getCurrentLevelId() {
        var resolved = resolveCurrentLevelData();
        return resolved != null
                ? resolved.getId()
                : this.getTowerData().currentLevelId + this.getTowerData().currentLevel;
    }

    /** form 1-3 */
    public int getCurrentLevel() {
        return this.getTowerData().currentLevel + 1;
    }

    public void onTick() {
        var challenge = player.getScene().getChallenge();
        if (!inProgress || challenge == null || !challenge.inProgress()) return;

        int stars = getCurLevelStars();
        while (stars < currentPossibleStars) {
            player
                    .getSession()
                    .send(
                            new PacketTowerLevelStarCondNotify(
                                    getTowerData().currentFloorId, getCurrentLevel(), currentPossibleStars));
            currentPossibleStars--;
        }
    }

    public void onBegin() {
        if (awaitingTeamReconfigure) {
            Grasscutter.getLogger()
                    .info(
                            "Tower onBegin ignored uid={} — awaiting team reconfigure",
                            player.getUid());
            return;
        }
        var challenge = player.getScene().getChallenge();
        inProgress = true;
        currentTimeLimit = challenge != null ? challenge.getTimeLimit() : 0;

        notifyCurLevelRecordChange();
        TowerAbyssFix.clearStartKeyOptions(player);

        try {
            if (player.getSession() != null) {
                player.sendPacket(new PacketCanUseSkillNotify(true));
            }
        } catch (Throwable ignored) {
        }

        this.fillTeamEnergy();
    }

    private void fillTeamEnergy() {
        player
                .getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            var depot = entity.getAvatar().getSkillDepot();
                            if (depot == null) return;

                            var energySkill = depot.getEnergySkillData();
                            if (energySkill != null && energySkill.getSpecialEnergyMin() > 0) {
                                entity.addSpecialEnergy(
                                        entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
                                return;
                            }

                            var element = depot.getElementType();
                            if (element == null) return;

                            float max = entity.getFightProperty(element.getMaxEnergyProp());
                            if (max <= 0) return;

                            entity.addEnergy(
                                    max, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, true);
                        });
    }

    public void onEnd() {
        inProgress = false;
    }

    public void refreshLevelEntityConfigs() {
        activeLevelEntityConfigs.clear();
        activeLevelEntityConfigs.addAll(TowerLevelEntityHelper.resolveActiveConfigNames(player));
        Grasscutter.getLogger()
                .info(
                        "Tower level-entity configs uid={} floor={} configs={}",
                        player.getUid(),
                        getCurrentFloorId(),
                        activeLevelEntityConfigs);
    }

    public void clearLevelEntityConfigs() {
        activeLevelEntityConfigs.clear();
        try {
            player.getBuffManager().clearBuffs();
        } catch (Throwable ignored) {
        }
    }

    public void notifyTowerLevelEntityAbilities() {
        TowerLevelEntityHelper.notifyClient(player);
    }

    public void beginAwaitingTeamReconfigure() {
        awaitingTeamReconfigure = true;
        inProgress = false;
    }

    public void clearAwaitingTeamReconfigure() {
        awaitingTeamReconfigure = false;
    }

    public void restartCurrentChamber() {
        int floorId = getTowerData().currentFloorId;
        if (floorId <= 0) return;

        midHalfCutscenePending = false;
        pendingMidHalfTeamId = -1;
        pendingMidHalfPos = null;
        pendingMidHalfRot = null;
        midHalfEarliestSwapMs = 0;
        pendingMidHalfTeamGuids = null;
        abyssCarryRemainingSeconds = 0;
        inProgress = false;
        currentTimeLimit = 0;
        currentPossibleStars = 3;
        forceChamberOneOnEnter = false;

        getTowerData().abyssTempTeamIndex = 0;
        resetTowerScriptStage();

        try {
            player.getTeamManager().useTemporaryTeam(0);
            TowerAbyssFix.prepareFirstChamber(player);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("Tower restartCurrentChamber team reset uid={}: {}", player.getUid(), t.toString());
        }

        player.save();
        Grasscutter.getLogger()
                .info(
                        "Tower restart current chamber uid={} floor={} chamber={}",
                        player.getUid(),
                        floorId,
                        getCurrentLevel());
    }

    private void resetTowerScriptStage() {
        var scene = player.getScene();
        if (scene == null || scene.getScriptManager() == null) return;
        try {
            var sm = scene.getScriptManager();
            for (var group : sm.getCachedGroupInstances().values()) {
                if (group == null || group.getCachedVariables() == null) continue;
                var vars = group.getCachedVariables();
                if (vars.containsKey("stage") || vars.containsKey("TPL_TIME")) {
                    vars.put("stage", 0);
                    vars.put("TPL_TIME", 0);
                }
            }
            sm.resetTowerStageVariables();
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("Tower reset stage/TPL_TIME uid={}: {}", player.getUid(), t.toString());
        }
    }

    /**
     * @deprecated Prefer {@link #restartCurrentChamber()}. Kept for any old callers.
     */
    @Deprecated
    public void restartFloorFromChamberOne() {
        restartCurrentChamber();
    }

    private static final int LEVELS_PER_FLOOR = 3;
    private static final int STARS_PER_LEVEL = 3;
    private static final int STARS_PER_FLOOR = LEVELS_PER_FLOOR * STARS_PER_LEVEL;

    public Map<Integer, TowerLevelRecord> getRecordMap() {
        Map<Integer, TowerLevelRecord> recordMap = getTowerData().recordMap;
        if (recordMap == null) {
            recordMap = new HashMap<>();
            getTowerData().recordMap = recordMap;
        }
        syncScheduleProgress(recordMap);
        grantEntranceFloors(recordMap);
        return recordMap;
    }

    private void syncScheduleProgress(Map<Integer, TowerLevelRecord> recordMap) {
        var towerSystem = player.getServer().getTowerSystem();
        var schedule = towerSystem.getCurrentTowerScheduleData();
        if (schedule == null) return;

        int currentId = schedule.getScheduleId();
        int lastId = getTowerData().lastScheduleId;
        if (lastId == currentId) return;

        var scheduleFloorList = towerSystem.getScheduleFloors();
        var scheduleFloors = new HashSet<>(scheduleFloorList);
        int previousBest = bestFullStarScheduleFloorIndex(recordMap, scheduleFloors);

        var entrance =
                new HashSet<>(
                        schedule.getEntranceFloorId() != null
                                ? schedule.getEntranceFloorId()
                                : List.of());
        var allowed = new HashSet<Integer>(entrance);
        allowed.addAll(scheduleFloors);

        recordMap
                .keySet()
                .removeIf(floorId -> scheduleFloors.contains(floorId) || !allowed.contains(floorId));

        getTowerData().lastScheduleId = currentId;
        getTowerData().currentFloorId = 0;
        getTowerData().currentLevel = 0;
        getTowerData().currentLevelId = 0;
        clearAbyssResume();

        applySkipFloorUnlock(recordMap, scheduleFloorList, previousBest);
        player.save();

        Grasscutter.getLogger()
                .info(
                        "Tower schedule progress reset uid={} schedule {} -> {} prevBest={} skipTo={} (cleared {})",
                        player.getUid(),
                        lastId,
                        currentId,
                        previousBest,
                        getTowerData().skipToFloorIndex,
                        scheduleFloors);
    }

    private static int bestFullStarScheduleFloorIndex(
            Map<Integer, TowerLevelRecord> recordMap, Set<Integer> scheduleFloors) {
        int best = 0;
        for (int floorId : scheduleFloors) {
            var record = recordMap.get(floorId);
            if (record == null || record.getStarCount() < STARS_PER_FLOOR) continue;
            var floorData = GameData.getTowerFloorDataMap().get(floorId);
            if (floorData == null) continue;
            best = Math.max(best, floorData.getFloorIndex());
        }
        return best;
    }

    private void applySkipFloorUnlock(
            Map<Integer, TowerLevelRecord> recordMap,
            List<Integer> scheduleFloors,
            int previousBestFloorIndex) {
        var data = getTowerData();
        data.skipFloorGrantedRewards = new HashMap<>();
        data.skipFloorState = 1;
        data.skipToFloorIndex = 9;

        if (scheduleFloors == null || scheduleFloors.isEmpty()) return;

        if (previousBestFloorIndex < 11) {
            recordMap.computeIfAbsent(scheduleFloors.get(0), TowerLevelRecord::new);
            return;
        }

        int skipThroughIndex = previousBestFloorIndex >= 12 ? 10 : 9;
        data.skipToFloorIndex = skipThroughIndex + 1;

        var granted = new LinkedHashMap<Integer, Integer>();
        for (int floorId : scheduleFloors) {
            var floorData = GameData.getTowerFloorDataMap().get(floorId);
            if (floorData == null) continue;
            int index = floorData.getFloorIndex();
            if (index < 9) continue;

            if (index <= skipThroughIndex) {
                fillFloorFullStars(recordMap, floorId);
                mergeFirstPassRewards(floorId, granted);
            } else if (index == data.skipToFloorIndex) {
                recordMap.computeIfAbsent(floorId, TowerLevelRecord::new);
            }
        }

        if (!granted.isEmpty()) {
            var items = new ArrayList<emu.grasscutter.game.inventory.GameItem>();
            granted.forEach(
                    (id, count) ->
                            items.add(new emu.grasscutter.game.inventory.GameItem(id, count)));
            player.getInventory()
                    .addItems(items, emu.grasscutter.game.props.ActionReason.TowerSkipFloorReward);
            data.skipFloorGrantedRewards = granted;
            data.skipFloorState = 3;
        } else {
            data.skipFloorState = 2;
        }
    }

    private void fillFloorFullStars(Map<Integer, TowerLevelRecord> recordMap, int floorId) {
        int firstLevelId = getFirstLevelId(floorId);
        if (firstLevelId == 0) return;
        var record = recordMap.computeIfAbsent(floorId, TowerLevelRecord::new);
        if (record.getPassedLevelMap() == null) {
            record.setPassedLevelMap(new HashMap<>());
        }
        for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
            record.setLevelStars(firstLevelId + i, STARS_PER_LEVEL);
        }
        record.setFloorStarRewardProgress(0);
    }

    private void mergeFirstPassRewards(int floorId, Map<Integer, Integer> into) {
        int firstLevelId = getFirstLevelId(floorId);
        if (firstLevelId == 0) return;
        for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
            var levelData = GameData.getTowerLevelDataMap().get(firstLevelId + i);
            if (levelData == null || levelData.getFirstPassRewardId() <= 0) continue;
            var reward = GameData.getRewardDataMap().get(levelData.getFirstPassRewardId());
            if (reward == null || reward.getRewardItemList() == null) continue;
            for (var param : reward.getRewardItemList()) {
                if (param == null || param.getId() <= 0 || param.getCount() <= 0) continue;
                into.merge(param.getId(), param.getCount(), Integer::sum);
            }
        }
    }

    private void grantEntranceFloors(Map<Integer, TowerLevelRecord> recordMap) {
        if (!GAME.tower.skipEntranceFloors) return;

        var schedule = player.getServer().getTowerSystem().getCurrentTowerScheduleData();
        if (schedule == null) return;

        var entranceFloors = schedule.getEntranceFloorId();
        if (entranceFloors == null || entranceFloors.isEmpty()) return;

        for (int floorId : entranceFloors) {
            int firstLevelId = getFirstLevelId(floorId);
            if (firstLevelId == 0) continue;

            var record = recordMap.computeIfAbsent(floorId, TowerLevelRecord::new);
            if (record.getPassedLevelMap() == null) {
                record.setPassedLevelMap(new HashMap<>());
            }

            record
                    .getPassedLevelMap()
                    .keySet()
                    .removeIf(id -> id < firstLevelId || id >= firstLevelId + LEVELS_PER_FLOOR);

            for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
                record.setLevelStars(firstLevelId + i, STARS_PER_LEVEL);
            }
            if (record.getFloorStarRewardProgress() < STARS_PER_FLOOR) {
                record.setFloorStarRewardProgress(STARS_PER_FLOOR);
            }
        }

        boolean hasScheduleRecord =
                recordMap.keySet().stream().anyMatch(id -> {
                    var floor = GameData.getTowerFloorDataMap().get(id);
                    return floor != null && floor.getFloorIndex() >= 9;
                });
        if (!hasScheduleRecord) {
            int firstScheduleFloor =
                    player
                            .getServer()
                            .getTowerSystem()
                            .getNextFloorId(entranceFloors.get(entranceFloors.size() - 1));
            if (firstScheduleFloor > 0) {
                recordMap.computeIfAbsent(firstScheduleFloor, TowerLevelRecord::new);
            }
        }
    }

    private static int getFirstLevelId(int floorId) {
        var floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (floorData == null) return 0;
        return GameData.getTowerLevelDataMap().values().stream()
                .filter(x -> x.getLevelGroupId() == floorData.getLevelGroupId() && x.getLevelIndex() == 1)
                .findFirst()
                .map(TowerLevelData::getId)
                .orElse(0);
    }

    public void teamSelect(int floor, List<List<Long>> towerTeams) {
        var floorData = GameData.getTowerFloorDataMap().get(floor);
        if (floorData == null) {
            Grasscutter.getLogger().warn("Tower team select for unknown floor {}", floor);
            return;
        }
        int floorId = floorData.getFloorId();
        int resumeChamber = findResumeChamberIndex(floorId);
        if (resumeChamber <= 0) {
            applyFloorStart(floorId);
            forceChamberOneOnEnter = true;
        } else {
            getTowerData().currentFloorId = floorId;
            getTowerData().currentLevel = resumeChamber;
            getTowerData().currentLevelId = getFirstLevelId(floorId);
            forceChamberOneOnEnter = false;
            resetTowerScriptStage();
        }

        if (getTowerData().entryScene == 0) {
            getTowerData().entryScene = player.getSceneId();
        }

        Grasscutter.getLogger()
                .info(
                        "Tower team select uid={} floor={}: {} team(s), sizes {}, resumeChamber={}",
                        player.getUid(),
                        floorId,
                        towerTeams.size(),
                        towerTeams.stream().map(List::size).toList(),
                        resumeChamber + 1);

        player.getTeamManager().setupTemporaryTeam(towerTeams);
        rememberAbyssTeams(towerTeams, 0);
        clearAwaitingTeamReconfigure();
    }

    private int findResumeChamberIndex(int floorId) {
        int firstLevelId = getFirstLevelId(floorId);
        if (firstLevelId == 0) {
            return 0;
        }
        var record = getRecordMap().get(floorId);
        if (record == null || record.getPassedLevelMap() == null || record.getPassedLevelMap().isEmpty()) {
            return 0;
        }
        for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
            if (record.getLevelStars(firstLevelId + i) <= 0) {
                return i;
            }
        }
        return 0;
    }

    public void rememberAbyssTeams(List<List<Long>> towerTeams, int teamIndex) {
        var data = getTowerData();
        data.abyssTeamGuids = new ArrayList<>();
        if (towerTeams != null) {
            for (var team : towerTeams) {
                data.abyssTeamGuids.add(team == null ? List.of() : new ArrayList<>(team));
            }
        }
        data.abyssTempTeamIndex = teamIndex;
        data.resumeAbyssOnLogin = true;
    }

    public List<List<Long>> getAbyssTeamGuids() {
        var teams = getTowerData().abyssTeamGuids;
        return teams != null ? teams : List.of();
    }

    public int getSelectedTowerBuffId() {
        return getTowerData().selectedTowerBuffId;
    }

    public void clearAbyssResume() {
        var data = getTowerData();
        data.abyssTeamGuids = new ArrayList<>();
        data.abyssTempTeamIndex = -1;
        data.resumeAbyssOnLogin = false;
        abyssCarryRemainingSeconds = 0;
    }

    public void rememberAbyssCarryRemaining(int remainingSeconds) {
        abyssCarryRemainingSeconds = Math.max(0, remainingSeconds);
        Grasscutter.getLogger()
                .info(
                        "Tower carry remaining uid={} remaining={}s",
                        player.getUid(),
                        abyssCarryRemainingSeconds);
    }

    public void clearAbyssCarryRemaining() {
        abyssCarryRemainingSeconds = 0;
    }

    public int resolveAbyssChallengeTimeLimit(int luaTimeLimitOrGroupId) {
        if (luaTimeLimitOrGroupId >= 600) {
            abyssCarryRemainingSeconds = 0;
            return luaTimeLimitOrGroupId;
        }
        int carry = abyssCarryRemainingSeconds;
        if (carry <= 0) {
            return luaTimeLimitOrGroupId;
        }
        boolean looksCorrupt =
                luaTimeLimitOrGroupId < 60
                        || (carry - luaTimeLimitOrGroupId) >= 60
                        || luaTimeLimitOrGroupId < carry / 2;
        if (looksCorrupt) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower ActiveChallenge time corrected uid={} lua={} -> carry={}",
                            player.getUid(),
                            luaTimeLimitOrGroupId,
                            carry);
            abyssCarryRemainingSeconds = 0;
            return carry;
        }
        abyssCarryRemainingSeconds = 0;
        return luaTimeLimitOrGroupId;
    }

    public void restoreAbyssTeamsOnLogin() {
        var data = getTowerData();
        if (!data.resumeAbyssOnLogin) return;
        if (!TowerAbyssFix.isTowerSceneId(player.getSceneId())) {
            clearAbyssResume();
            return;
        }
        if (data.abyssTeamGuids == null || data.abyssTeamGuids.isEmpty()) {
            clearAbyssResume();
            return;
        }
        try {
            player.getTeamManager().setupTemporaryTeam(data.abyssTeamGuids);
            int index = data.abyssTempTeamIndex >= 0 ? data.abyssTempTeamIndex : 0;
            player.getTeamManager().useTemporaryTeam(index);
            Grasscutter.getLogger()
                    .info(
                            "Tower restored abyss teams on login uid={} teams={} index={}",
                            player.getUid(),
                            data.abyssTeamGuids.size(),
                            index);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("Tower failed to restore abyss teams uid={}: {}", player.getUid(), t.toString());
            clearAbyssResume();
        }
    }

    public TowerLevelData getCurrentTowerLevelDataMap() {
        var resolved = resolveCurrentLevelData();
        return resolved != null ? resolved : GameData.getTowerLevelDataMap().get(getCurrentLevelId());
    }

    public TowerLevelData resolveCurrentLevelData() {
        int floorId = getTowerData().currentFloorId;
        int chamber = getTowerData().currentLevel + 1;
        if (floorId <= 0 || chamber <= 0) return null;

        TowerFloorData floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (floorData == null) return null;
        return GameData.getTowerLevelDataMap().values().stream()
                .filter(x -> x.getLevelGroupId() == floorData.getLevelGroupId())
                .filter(x -> x.getLevelIndex() == chamber)
                .findFirst()
                .orElse(null);
    }

    public void applyFloorStart(int floorId) {
        var floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (floorData == null) {
            Grasscutter.getLogger().warn("Tower applyFloorStart for unknown floor {}", floorId);
            return;
        }
        getTowerData().currentFloorId = floorData.getFloorId();
        getTowerData().currentLevel = 0;
        getTowerData().currentLevelId = getFirstLevelId(floorId);
    }

    public void ensureFloorStateForEnter() {
        if (getTowerData().currentLevel < 3) return;
        int nextFloorId = getNextFloorId();
        if (nextFloorId > 0) {
            applyFloorStart(nextFloorId);
            Grasscutter.getLogger().info(
                    "Tower auto-advanced to next floor uid={} floor={}", player.getUid(), nextFloorId);
        }
    }

    public int getCurrentMonsterLevel() {
        var levelData = getCurrentTowerLevelDataMap();
        if (levelData != null) {
            return levelData.getMonsterLevel() + 1;
        }
        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        Grasscutter.getLogger()
                .warn("No tower level data for level {}, falling back to the floor level", getCurrentLevelId());
        return floorData != null ? floorData.getOverrideMonsterLevel() : 1;
    }

    public void enterLevel(int enterPointId) {
        clearAwaitingTeamReconfigure();
        if (forceChamberOneOnEnter) {
            applyFloorStart(getTowerData().currentFloorId);
            forceChamberOneOnEnter = false;
        } else {
            ensureFloorStateForEnter();
        }
        var levelData = getCurrentTowerLevelDataMap();
        if (levelData == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower enter level {} on floor {} has no level data",
                            getCurrentLevelId(),
                            getCurrentFloorId());
            return;
        }

        var dungeonId = levelData.getDungeonId();

        notifyCurLevelRecordChange();
        refreshLevelEntityConfigs();
        getTowerData().abyssTempTeamIndex = 0;
        if (!player.getTeamManager().hasTemporaryTeam()) {
            ensureAbyssTemporaryTeams();
        }
        player.getTeamManager().useTemporaryTeam(0);
        TowerAbyssFix.prepareFirstChamber(player);
        player
                .getServer()
                .getDungeonSystem()
                .handoffDungeon(player, dungeonId, towerDungeonSettleListener);

        player.getScene().setPrevScene(getTowerData().entryScene);
        player.getScene().setPrevScenePoint(enterPointId);

        notifyTowerLevelEntityAbilities();

        var buffOfferings = rollCurrentChamberBuffs();
        player
                .getSession()
                .send(
                        new PacketTowerEnterLevelRsp(
                                getTowerData().currentFloorId, getCurrentLevel(), buffOfferings));
        player.getSession().send(new PacketCanUseSkillNotify(false));
        currentPossibleStars = 3;
        player
                .getSession()
                .send(
                        new PacketTowerLevelStarCondNotify(
                                getTowerData().currentFloorId, getCurrentLevel(), currentPossibleStars + 1));
    }

    public void notifyCurLevelRecordChange() {
        boolean upper = getTowerData().abyssTempTeamIndex <= 0;
        player
                .getSession()
                .send(
                        new PacketTowerCurLevelRecordChangeNotify(
                                getTowerData().currentFloorId, getCurrentLevel(), upper, player));
        if (player.getResinManager() != null) {
            player.getResinManager().refreshClientResinUi();
        }
    }

    public int getCurLevelStars() {
        var scene = player.getScene();
        var challenge = scene != null ? scene.getChallenge() : null;
        if (challenge == null) {
            Grasscutter.getLogger()
                    .warn(
                            "getCurLevelStars: no challenge uid={} floor={} level={}, using cached={}",
                            player.getUid(),
                            getCurrentFloorId(),
                            getCurrentLevel(),
                            lastSettledStars);
            return Math.max(0, Math.min(3, lastSettledStars));
        }

        var levelData = getCurrentTowerLevelDataMap();
        if (levelData == null) {
            Grasscutter.getLogger().error("getCurLevelStars: no level data for {}", getCurrentLevelId());
            return 0;
        }

        int elapsed =
                challenge.inProgress()
                        ? Math.max(0, scene.getSceneTimeSeconds() - challenge.getStartedAt())
                        : Math.max(0, challenge.getFinishedTime());
        int timeRemaining = challenge.getTimeLimit() - elapsed;

        int star;
        for (star = 2; star >= 0; star--) {
            var cond = levelData.getCondType(star);
            if (cond == TowerLevelData.TowerCondType.TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN) {
                var params = levelData.getTimeCond(star);
                if (params != null && timeRemaining >= params.getMinimumTimeInSeconds()) {
                    break;
                }
            } else if (cond == TowerLevelData.TowerCondType.TOWER_COND_LEFT_HP_GREATER_THAN) {
                var params = levelData.getHpCond(star);
                if (params != null && challenge.getGuardEntityHpPercent() >= params.getMinimumHpPercentage()) {
                    break;
                }
            } else {
                Grasscutter.getLogger()
                        .error(
                                "getCurLevelStars: Tower level {} has no or unknown condition defined for {} stars",
                                getCurrentLevelId(),
                                star + 1);
                continue;
            }
        }
        int result = star + 1;
        lastSettledStars = result;
        Grasscutter.getLogger()
                .info(
                        "getCurLevelStars uid={} level={} remaining={} => {} star(s)",
                        player.getUid(),
                        getCurrentLevelId(),
                        timeRemaining,
                        result);
        return result;
    }

    public void notifyCurLevelRecordChangeWhenDone(int stars) {
        if (midHalfCutscenePending) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower notifyCurLevelRecordChangeWhenDone skipped uid={} - mid-half pending",
                            player.getUid());
            return;
        }

        Map<Integer, TowerLevelRecord> recordMap = this.getRecordMap();
        int currentFloorId = getTowerData().currentFloorId;
        if (!recordMap.containsKey(currentFloorId)) {
            recordMap.put(
                    currentFloorId,
                    new TowerLevelRecord(currentFloorId).setLevelStars(getCurrentLevelId(), stars));
        } else {
            var prevRecord = recordMap.get(currentFloorId);
            int levelId = getCurrentLevelId();
            int prevStars = prevRecord.getLevelStars(levelId);
            if (stars > prevStars) {
                recordMap.put(currentFloorId, prevRecord.setLevelStars(levelId, stars));
            }
        }

        this.getTowerData().currentLevel++;
        getTowerData().abyssTempTeamIndex = 0;

        if (!this.hasNextLevel()) {
            var nextFloorId = this.getNextFloorId();
            if (nextFloorId > 0) {
                recordMap.computeIfAbsent(nextFloorId, TowerLevelRecord::new);
            }
            try {
                player.getSession().send(PacketTowerCurLevelRecordChangeNotify.empty());
            } catch (Throwable ignored) {
            }
            Grasscutter.getLogger()
                    .info(
                            "Tower floor cleared uid={} floor={} stars={} nextUnlocked={} (CurLevel cleared for claims)",
                            player.getUid(),
                            currentFloorId,
                            recordMap.get(currentFloorId) != null
                                    ? recordMap.get(currentFloorId).getStarCount()
                                    : stars,
                            nextFloorId);
        } else {
            player
                    .getSession()
                    .send(new PacketTowerCurLevelRecordChangeNotify(currentFloorId, getCurrentLevel()));
        }
        player.save();
    }

    public boolean hasNextLevel() {
        return getTowerData().currentLevel < 3;
    }

    public int getNextFloorId() {
        return this.player
                .getServer()
                .getTowerSystem()
                .getNextFloorId(this.getTowerData().currentFloorId);
    }

    public boolean hasNextFloor() {
        return this.player
                        .getServer()
                        .getTowerSystem()
                        .getNextFloorId(this.getTowerData().currentFloorId)
                > 0;
    }

    public void clearEntry() {
        getTowerData().entryScene = 0;
    }

    public boolean canEnterScheduleFloor() {
        Map<Integer, TowerLevelRecord> recordMap = this.getRecordMap();
        if (!recordMap.containsKey(this.player.getServer().getTowerSystem().getLastEntranceFloor())) {
            return false;
        }
        return recordMap
                        .get(this.player.getServer().getTowerSystem().getLastEntranceFloor())
                        .getStarCount()
                >= 6;
    }

    public boolean claimFloorStarReward(int floorId) {
        var record = getRecordMap().get(floorId);
        if (record == null) {
            Grasscutter.getLogger().warn("Tower claim stars: no record floor={}", floorId);
            return false;
        }
        var floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (floorData == null) return false;

        int stars = record.getStarCount();
        int claimed = Math.max(0, Math.min(9, record.getFloorStarRewardProgress()));
        claimed = (claimed / 3) * 3;
        if (stars < claimed + 3 || claimed >= 9) {
            Grasscutter.getLogger()
                    .info(
                            "Tower claim stars rejected uid={} floor={} stars={} claimed={}",
                            player.getUid(),
                            floorId,
                            stars,
                            claimed);
            return false;
        }

        var items = new ArrayList<emu.grasscutter.game.inventory.GameItem>();
        int progress = claimed;
        while (progress + 3 <= 9 && stars >= progress + 3) {
            int tier = progress + 3;
            int rewardId =
                    switch (tier) {
                        case 3 -> floorData.getRewardIdThreeStars();
                        case 6 -> floorData.getRewardIdSixStars();
                        case 9 -> floorData.getRewardIdNineStars();
                        default -> 0;
                    };
            if (rewardId <= 0) {
                Grasscutter.getLogger()
                        .warn("Tower claim stars: no rewardId floor={} tier={}", floorId, tier);
                break;
            }
            var reward = GameData.getRewardDataMap().get(rewardId);
            if (reward == null || reward.getRewardItemList() == null) {
                Grasscutter.getLogger()
                        .warn("Tower claim stars: missing RewardExcel id={}", rewardId);
                break;
            }
            for (var param : reward.getRewardItemList()) {
                if (param == null || param.getId() <= 0 || param.getCount() <= 0) continue;
                items.add(new emu.grasscutter.game.inventory.GameItem(param.getId(), param.getCount()));
            }
            progress = tier;
            Grasscutter.getLogger()
                    .info(
                            "Tower claim stars uid={} floor={} tier={} rewardId={}",
                            player.getUid(),
                            floorId,
                            tier,
                            rewardId);
        }

        if (progress == claimed) return false;

        if (!items.isEmpty()) {
            var merged = new java.util.LinkedHashMap<Integer, Integer>();
            for (var item : items) {
                merged.merge(item.getItemId(), item.getCount(), Integer::sum);
            }
            var grant = new ArrayList<emu.grasscutter.game.inventory.GameItem>();
            merged.forEach((id, count) -> grant.add(new emu.grasscutter.game.inventory.GameItem(id, count)));
            player.getInventory()
                    .addItems(grant, emu.grasscutter.game.props.ActionReason.TowerFloorStarReward);
        }
        record.setFloorStarRewardProgress(progress);
        player.save();
        return true;
    }

    private static int secondsToSchedulerDelay(int seconds) {
        return Math.max(1, seconds);
    }

    public void mirrorTeamSetUp(int teamId) {
        getTowerData().abyssTempTeamIndex = teamId;
        getTowerData().resumeAbyssOnLogin = true;

        Position bornPos = resolveChamberBornPos();
        Position bornRot = resolveChamberBornRot();

        pendingMidHalfTeamId = teamId;
        pendingMidHalfPos = new Position(bornPos);
        pendingMidHalfRot = new Position(bornRot);
        midHalfCutscenePending = true;
        midHalfEarliestSwapMs =
                System.currentTimeMillis() + MID_HALF_TRANSITION_SECONDS * 1000L;
        pendingMidHalfTeamGuids = copyTeamGuids(getAbyssTeamGuids());
        if (pendingMidHalfTeamGuids.isEmpty()) {
            ensureAbyssTemporaryTeams();
            pendingMidHalfTeamGuids = copyTeamGuids(getAbyssTeamGuids());
        }

        inProgress = false;
        try {
            var scene = player.getScene();
            var challenge = scene != null ? scene.getChallenge() : null;
            if (challenge != null) {
                if (challenge.inProgress()) {
                    challenge.finishSuccessQuiet();
                } else {
                    player.sendPacket(
                            new emu.grasscutter.server.packet.send.PacketDungeonChallengeFinishNotify(
                                    challenge));
                }
                scene.setChallenge(null);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("Tower mid-half end upper challenge uid={}: {}", player.getUid(), t.toString());
        }

        int delayTicks = secondsToSchedulerDelay(MID_HALF_FALLBACK_SECONDS);
        Grasscutter.getLogger()
                .info(
                        "Tower mid-half uid={} teamId={} fallback={}s (schedulerDelay={}) pos={}",
                        player.getUid(),
                        teamId,
                        MID_HALF_FALLBACK_SECONDS,
                        delayTicks,
                        bornPos);

        player
                .getSession()
                .send(
                        new PacketTowerCurLevelRecordChangeNotify(
                                getTowerData().currentFloorId, getCurrentLevel(), false, player));

        player.sendPacket(new PacketTowerMiddleLevelChangeTeamNotify());
        player.sendPacket(new PacketCutsceneBeginNotify(MID_HALF_CUTSCENE_ID));
        Grasscutter.getLogger()
                .info("Tower mid-half uid={} cutscene={}", player.getUid(), MID_HALF_CUTSCENE_ID);

        final int applyTeamId = teamId;
        player
                .getServer()
                .getScheduler()
                .scheduleDelayedTask(
                        () -> {
                            if (!midHalfCutscenePending || pendingMidHalfTeamId != applyTeamId) {
                                return;
                            }
                            Grasscutter.getLogger()
                                    .info(
                                            "Tower mid-half fallback swap uid={} after {}s",
                                            player.getUid(),
                                            MID_HALF_FALLBACK_SECONDS);
                            completeMidHalfSwap();
                        },
                        delayTicks);
    }

    public void onMidHalfCutsceneFinished(int cutsceneId) {
        if (cutsceneId != MID_HALF_CUTSCENE_ID) return;
        if (!midHalfCutscenePending) return;
        Grasscutter.getLogger()
                .info("Tower mid-half CutSceneFinish uid={} → swap", player.getUid());
        completeMidHalfSwap();
    }

    private void completeMidHalfSwap() {
        if (!midHalfCutscenePending) return;
        long now = System.currentTimeMillis();
        if (now < midHalfEarliestSwapMs) {
            int remainMs = (int) (midHalfEarliestSwapMs - now);
            int delay = Math.max(1, (remainMs + 999) / 1000);
            player.getServer().getScheduler().scheduleDelayedTask(this::completeMidHalfSwap, delay);
            return;
        }
        int teamId = pendingMidHalfTeamId;
        Position bornPos = pendingMidHalfPos;
        Position bornRot = pendingMidHalfRot;
        midHalfCutscenePending = false;
        pendingMidHalfTeamId = -1;
        pendingMidHalfPos = null;
        pendingMidHalfRot = null;
        midHalfEarliestSwapMs = 0;
        List<List<Long>> midHalfGuids = pendingMidHalfTeamGuids;
        pendingMidHalfTeamGuids = null;
        if (teamId < 0 || bornPos == null || bornRot == null) return;
        applyMidHalfTeamSwap(teamId, bornPos, bornRot, midHalfGuids);
    }

    private static List<List<Long>> copyTeamGuids(List<List<Long>> teams) {
        if (teams == null || teams.isEmpty()) return new ArrayList<>();
        var out = new ArrayList<List<Long>>(teams.size());
        for (var team : teams) {
            out.add(team == null ? List.of() : new ArrayList<>(team));
        }
        return out;
    }

    private boolean ensureAbyssTemporaryTeams(List<List<Long>> fallbackGuids, int teamIndexHint) {
        if (TowerAbyssTeamRestore.ensure(player)) {
            return true;
        }
        if (fallbackGuids != null && !fallbackGuids.isEmpty()) {
            rememberAbyssTeams(fallbackGuids, Math.max(0, teamIndexHint));
            return TowerAbyssTeamRestore.ensure(player);
        }
        return false;
    }

    private boolean ensureAbyssTemporaryTeams() {
        return ensureAbyssTemporaryTeams(null, getTowerData().abyssTempTeamIndex);
    }

    private void applyMidHalfTeamSwap(
            int teamId, Position bornPos, Position bornRot, List<List<Long>> fallbackGuids) {
        if (player.getSession() == null || !player.getSession().isActive()) {
            Grasscutter.getLogger()
                    .warn("Tower mid-half aborted uid={}: session inactive", player.getUid());
            return;
        }

        getTowerData().abyssTempTeamIndex = teamId;
        getTowerData().resumeAbyssOnLogin = true;

        if (!ensureAbyssTemporaryTeams(fallbackGuids, teamId)) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower mid-half aborted uid={}: cannot rebuild temporaryTeam for teamId={}",
                            player.getUid(),
                            teamId);
            return;
        }

        if (player.getTeamManager().getTemporaryTeamCount() <= teamId) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower mid-half aborted uid={}: teamId={} but only {} temporary team(s)",
                            player.getUid(),
                            teamId,
                            player.getTeamManager().getTemporaryTeamCount());
            return;
        }

        player.getPosition().set(bornPos);
        player.getRotation().set(bornRot);
        player.getTeamManager().useTemporaryTeam(teamId);

        TowerAbyssFix.prepareFirstChamber(player);

        var avatar = player.getTeamManager().getCurrentAvatarEntity();
        var scene = player.getScene();
        if (avatar != null && scene != null) {
            avatar.getPosition().set(bornPos);
            avatar.getRotation().set(bornRot);
            scene.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(
                            avatar, VisionType.VisionType_VISION_REPLACE));
            scene.broadcastPacket(
                    new PacketSceneEntityAppearNotify(
                            avatar, VisionType.VisionType_VISION_REPLACE, avatar.getId()));
            player.sendPacket(new PacketSceneEntityAppearNotify(player));
        }

        player.sendPacket(new PacketCanUseSkillNotify(true));
        if (scene != null) {
            scene.broadcastPacket(new PacketScenePlayerLocationNotify(scene));
        }

        boolean isUpper = teamId <= 0;
        player.sendPacket(PacketTowerCurLevelRecordChangeNotify.empty());
        player
                .getSession()
                .send(
                        new PacketTowerCurLevelRecordChangeNotify(
                                getTowerData().currentFloorId, getCurrentLevel(), isUpper, player));
        player
                .getSession()
                .send(
                        new PacketTowerLevelStarCondNotify(
                                getTowerData().currentFloorId, getCurrentLevel(), 4));

        this.fillTeamEnergy();
        notifyTowerLevelEntityAbilities();
        for (int sec : new int[] {1, 2, 3}) {
            final int at = sec;
            player
                    .getServer()
                    .getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                if (player.getSession() == null || !player.getSession().isActive()) return;
                                if (getTowerData().abyssTempTeamIndex != teamId) return;
                                player
                                        .getSession()
                                        .send(
                                                new PacketTowerCurLevelRecordChangeNotify(
                                                        getTowerData().currentFloorId,
                                                        getCurrentLevel(),
                                                        isUpper,
                                                        player));
                            },
                            secondsToSchedulerDelay(at));
        }
        Grasscutter.getLogger()
                .info(
                        "Tower mid-half applied uid={} teamId={} isUpper={} activeTeam={} lead={}",
                        player.getUid(),
                        teamId,
                        isUpper,
                        player.getTeamManager().getActiveTeam().size(),
                        player.getTeamManager().getCurrentCharacterIndex());
    }

    public List<Integer> rollCurrentChamberBuffs() {
        var levelData = getCurrentTowerLevelDataMap();
        var schedule = player.getServer().getTowerSystem().getCurrentTowerScheduleData();
        int scheduleId = schedule != null ? schedule.getScheduleId() : 0;
        var offerings = TowerBuffHelper.rollBuffOfferings(levelData, scheduleId);
        var data = getTowerData();
        data.currentBuffOfferings = new ArrayList<>(offerings);
        data.selectedTowerBuffId = 0;
        return offerings;
    }

    public boolean selectTowerBuff(int towerBuffId) {
        var data = getTowerData();
        if (!TowerBuffHelper.isValidOffering(towerBuffId, data.currentBuffOfferings)) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower buff select rejected uid={} buff={} offerings={}",
                            player.getUid(),
                            towerBuffId,
                            data.currentBuffOfferings);
            return false;
        }
        data.selectedTowerBuffId = towerBuffId;
        int abilityBuffId = TowerBuffHelper.getAbilityBuffId(towerBuffId);
        if (abilityBuffId > 0) {
            boolean applied = player.getBuffManager().addBuff(abilityBuffId);
            Grasscutter.getLogger()
                    .info(
                            "Tower chamber buff applied uid={} towerBuff={} abilityBuff={} ok={}",
                            player.getUid(),
                            towerBuffId,
                            abilityBuffId,
                            applied);
        }
        notifyCurLevelRecordChange();
        return true;
    }

    private Position resolveChamberBornPos() {
        var scene = player.getScene();
        try {
            if (scene != null) {
                var meta = ScriptLoader.getSceneMeta(scene.getId());
                if (meta != null && meta.config != null && meta.config.born_pos != null) {
                    return new Position(meta.config.born_pos);
                }
            }
        } catch (Throwable ignored) {
        }
        return new Position(0f, -0.102f, 15.011f);
    }

    private Position resolveChamberBornRot() {
        var scene = player.getScene();
        try {
            if (scene != null) {
                var meta = ScriptLoader.getSceneMeta(scene.getId());
                if (meta != null && meta.config != null && meta.config.born_rot != null) {
                    return new Position(meta.config.born_rot);
                }
            }
        } catch (Throwable ignored) {
        }
        return new Position(0f, 180f, 0f);
    }
}
