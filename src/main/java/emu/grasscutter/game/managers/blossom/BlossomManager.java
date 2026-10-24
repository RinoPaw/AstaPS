package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.reward.RewardClaimFlow;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.reward.RewardConfiguration;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.managers.ResinManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.PacketBlossomBriefInfoNotify;
import java.util.*;

public class BlossomManager {
    private final Scene scene;
    private final BlossomNativeCamps nativeCamps = new BlossomNativeCamps();
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final Set<EntityGadget> createdEntity = new HashSet<>();
    private final Set<SpawnDataEntry> blossomConsumed = new HashSet<>();
    private final Set<SpawnDataEntry> scriptedSpawned = new HashSet<>();
    private List<SpawnDataEntry> activeSpawnCache;
    private int cachedAdventureRank = -1;
    private int cachedConsumedCount = -1;
    private Set<Integer> cachedUnlockedAreas = Set.of();
    private int activeSetRevision;
    private boolean mapMarkersDirty;
    private static final double SCRIPTED_SPAWN_RANGE = 400;

    /** Spawn only the camps chosen by BlossomGroups and backed by a real Lua gadget. */
    public void ensureScriptedSpawns() {
        removeInactiveAuthoredOutcrops();
        for (var spawn : activeScriptedSpawns()) {
            if (scriptedSpawned.contains(spawn)) continue;
            if (scene.getPlayers().stream().noneMatch(player ->
                    player.getPosition().computeDistance(spawn.getPos()) <= SCRIPTED_SPAWN_RANGE))
                continue;

            var group = nativeCamps.groupFor(spawn);
            if (group == null) continue;
            // Only the original Lua GROUP_LOAD -> GROUP_REFRESH -> CreateGadget path
            // creates a worktop. The entity-created hook below attaches its camp.
            scene.getScriptManager().ensureBlossomGroup(group);
            scriptedSpawned.add(spawn);
        }
    }

    /** A previously selected, but no longer active, camp cannot leave an interactive gadget. */
    private void removeInactiveAuthoredOutcrops() {
        var seen = new HashSet<String>();
        var toRemove = new ArrayList<GameEntity>();
        for (var entity : List.copyOf(scene.getEntities().values())) {
            if (!(entity instanceof EntityGadget gadget)
                    || !isAuthoredWorktop(gadget.getGroupId(), gadget.getGadgetId())) continue;
            boolean active = isActiveAuthoredGadget(
                    gadget.getGroupId(), gadget.getConfigId(), gadget.getGadgetId());
            String identity = gadget.getGroupId() + ":" + gadget.getConfigId() + ":" + gadget.getGadgetId();
            if (!active || !seen.add(identity)) {
                scene.removeEntity(gadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                toRemove.add(gadget);
            }
        }
        recycleGadgetEntity(toRemove);
    }

    /** The normal world grid and Lua group loader must obey the same selection. */
    public boolean isActiveSpawn(SpawnDataEntry entry) {
        // Legacy Spawns.json bloom entries are never a second source of camps.
        return BlossomType.valueOf(entry.getGadgetId()) == null;
    }

    /** Report the selected camp's native refresh type to its authored Lua group. */
    public Integer getBlossomRefreshTypeByGroupId(int groupId) {
        for (var spawn : activeScriptedSpawns()) {
            if (spawn.getGroup().getGroupId() != groupId) continue;
            return luaRefreshType(BlossomType.valueOf(spawn.getGadgetId()));
        }
        return null;
    }

    static Integer luaRefreshType(BlossomType type) {
        if (type == null) return null;
        return type == BlossomType.GOLD ? 1 : 3;
    }

    /** Lua reports completion; the Java ledger owns the reward and camp rotation. */
    public boolean completeLuaEncounter(int groupId, emu.grasscutter.scripts.data.SceneGadget reward) {
        synchronized (blossomActivities) {
            for (var activity : blossomActivities) {
                if (activity.getGadget().getGroupId() == groupId)
                    return activity.completeFromLua(reward);
            }
        }
        return false;
    }

    /** The original Lua trigger route uses the same activity and reward owner. */
    public boolean beginNativeEncounter(EntityGadget source, int monsterGoal) {
        synchronized (blossomActivities) {
            for (var activity : blossomActivities) {
                if (activity.getGadget() != source) continue;
                activity.beginNative(monsterGoal);
                return true;
            }
        }
        return false;
    }

    /** One event per native group monster death, with one final Lua progress event. */
    public int addNativeScheduleProgress(int groupId) {
        synchronized (blossomActivities) {
            for (var activity : blossomActivities) {
                if (activity.getGadget().getGroupId() != groupId) continue;
                if (activity.addNativeProgress()) {
                    scene.getScriptManager().callEvent(new emu.grasscutter.scripts.data.ScriptArgs(
                            groupId, emu.grasscutter.scripts.constants.EventType.EVENT_BLOSSOM_PROGRESS_FINISH));
                }
                return 0;
            }
        }
        return 1;
    }

    public int blossomScheduleState(int groupId) {
        synchronized (blossomActivities) {
            return blossomActivities.stream()
                    .filter(activity -> activity.getGadget().getGroupId() == groupId)
                    .mapToInt(BlossomActivity::scheduleState).findFirst().orElse(1);
        }
    }

    public int setBlossomScheduleState(int groupId, int state) {
        if (state < 1 || state > 3) return 1;
        boolean selected = activeScriptedSpawns().stream()
                .anyMatch(spawn -> spawn.getGroup().getGroupId() == groupId);
        if (!selected) return 1;
        synchronized (blossomActivities) {
            var activity = blossomActivities.stream()
                    .filter(current -> current.getGadget().getGroupId() == groupId)
                    .findFirst().orElse(null);
            // The native Lua requests the transition. The actual phase was
            // already established by encounter start or completion.
            if (state == 1) return activity == null ? 0 : 1;
            return activity != null && activity.scheduleState() == state ? 0 : 1;
        }
    }

    private emu.grasscutter.scripts.data.SceneGroup scriptGroupFor(EntityGadget gadget) {
        // Reuse the same Lua environment as the live scene triggers when available.
        var group = scene.getScriptManager().getGroupById(gadget.getGroupId());
        return group != null ? group : nativeCamps.groupFor(gadget.getSpawnEntry());
    }

    /** Operator-created gadgets with no authored group are outside this policy. */
    /** Native camp instances are not part of the legacy Spawns.json grid. */
    public boolean isNativeCampSpawn(SpawnDataEntry entry) {
        return nativeCamps.owns(entry);
    }

    public boolean isAuthoredWorktop(int groupId, int gadgetId) {
        return groupId != 0 && nativeCamps.ownsGroup(groupId)
                && BlossomType.valueOf(gadgetId) != null;
    }

    /** Other SceneGroups are never governed by the blossom camp selection. */
    public boolean isActiveAuthoredGroup(int groupId) {
        return !nativeCamps.ownsGroup(groupId) || activeScriptedSpawns().stream()
                .anyMatch(spawn -> spawn.getGroup().getGroupId() == groupId);
    }

    public boolean isActiveAuthoredGadget(int groupId, int configId, int gadgetId) {
        if (!isAuthoredWorktop(groupId, gadgetId)) return true;
        boolean selected = activeScriptedSpawns().stream().anyMatch(entry ->
                entry.getGroup().getGroupId() == groupId
                        && entry.getConfigId() == configId
                        && entry.getGadgetId() == gadgetId);
        if (!selected) return false;
        // A delayed GROUP_REFRESH must not recreate the interactive flower
        // while its single encounter is running or its reward remains unclaimed.
        synchronized (blossomActivities) {
            return blossomActivities.stream().noneMatch(activity ->
                    activity.getGadget().getGroupId() == groupId
                            && activity.getGadget().getConfigId() == configId
                            && activity.getGadget().getGadgetId() == gadgetId);
        }
    }

    private List<SpawnDataEntry> activeScriptedSpawns() {
        var host = scene.getWorld().getHost();
        int rank = host.getLevel();
        var unlocked = host.getUnlockedSceneAreas(scene.getId());
        if (activeSpawnCache != null && cachedAdventureRank == rank
                && cachedConsumedCount == blossomConsumed.size()
                && cachedUnlockedAreas.equals(unlocked)) {
            return activeSpawnCache;
        }

        var old = activeSpawnCache;
        var selected = nativeCamps.select(scene, rank, unlocked).stream()
                .filter(entry -> !blossomConsumed.contains(entry)).toList();

        activeSpawnCache = selected;
        cachedAdventureRank = rank;
        cachedUnlockedAreas = Set.copyOf(unlocked);
        cachedConsumedCount = blossomConsumed.size();
        if (old == null || !old.equals(selected)) {
            activeSetRevision++;
            if (old != null) mapMarkersDirty = true;
            Grasscutter.getLogger().info(
                    "Ley-line active scene={} rank={} blue={} gold={} originalCamps={}",
                    scene.getId(), rank,
                    selected.stream().filter(e -> e.getGadgetId() == BlossomType.BLUE.getGadgetId()).count(),
                    selected.stream().filter(e -> e.getGadgetId() == BlossomType.GOLD.getGadgetId()).count(),
                    !selected.isEmpty());
        }
        return selected;
    }

    /** Used by the normal grid loader to notice eligibility changes without player movement. */
    public int getActiveSetRevision() {
        activeScriptedSpawns();
        return activeSetRevision;
    }

    static boolean refreshEligible(
            BlossomRefreshExcelConfigData refresh, int adventureRank,
            Collection<Integer> unlockedAreas, Collection<CityData> cities) {
        if (refresh.getRefreshCondVec() == null) return false;
        for (var condition : refresh.getRefreshCondVec()) {
            if (condition == null || condition.getType() == null) continue;
            var params = condition.getParam();
            if (params == null || params.isEmpty()) return false;
            switch (condition.getType()) {
                case "BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER" -> {
                    if (adventureRank < params.get(0)) return false;
                }
                case "BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY" -> {
                    if (params.get(0) != refresh.getCityId()) return false;
                    boolean anyUnlocked = cities.stream()
                            .filter(city -> city.getCityId() == refresh.getCityId())
                            .filter(city -> city.getAreaIdVec() != null)
                            .anyMatch(city -> city.getAreaIdVec().stream()
                                    .anyMatch(unlockedAreas::contains));
                    if (!anyUnlocked) return false;
                }
                default -> {
                    // Unknown conditions must be verified before enabling their entries.
                    return false;
                }
            }
        }
        return true;
    }

    public BlossomManager(Scene scene) {
        this.scene = scene;
    }

    /** Track ability summons at creation, including ones removed before the next world tick. */
    public void onSceneEntityCreated(GameEntity entity) {
        if (entity instanceof EntityGadget gadget
                && isAuthoredWorktop(gadget.getGroupId(), gadget.getGadgetId())) {
            for (var spawn : activeScriptedSpawns()) {
                if (spawn.getGroup().getGroupId() == gadget.getGroupId()
                        && spawn.getConfigId() == gadget.getConfigId()
                        && spawn.getGadgetId() == gadget.getGadgetId()) {
                    gadget.setSpawnEntry(spawn);
                    initBlossom(gadget);
                    scene.getSpawnedEntities().add(spawn);
                    break;
                }
            }
        }
        synchronized (blossomActivities) {
            for (var activity : blossomActivities) activity.onSceneEntityCreated(entity);
        }
    }

    public void onTick() {
        activeScriptedSpawns();
        // Scene.checkGroups() already loads nearby authored outcrops in script mode.
        // The normal spawn-grid path needs the same loader.
        if (!scene.getScriptManager().isInit()) ensureScriptedSpawns();
        if (mapMarkersDirty) notifyIcon();
        synchronized (blossomActivities) {
            for (var active : blossomActivities) {
                if (!active.rewardReady()) continue;
                active.clearEncounterSummons();
                EntityGadget chest = active.getChest();
                active.markRewardSpawned();
                scene.addEntity(chest);
                Grasscutter.getLogger().info(
                        "Ley-line reward flower spawned scene={} gadget={} source={} nativeKills={}/{}",
                        scene.getId(), chest.getGadgetId(),
                        active.getGadget().getGadgetId(),
                        active.nativeKills(), active.nativeGoal());
            }
        }
    }

    /** Entity unload is separate from activity completion and map-marker changes. */
    public void recycleGadgetEntity(List<GameEntity> entities) {
        for (var entity : entities) {
            if (!(entity instanceof EntityGadget gadget)
                    || BlossomType.valueOf(gadget.getGadgetId()) == null) continue;
            createdEntity.remove(gadget);
            var spawn = gadget.getSpawnEntry();
            if (spawn == null) continue;
            scriptedSpawned.remove(spawn);
            scene.getSpawnedEntities().remove(spawn);
        }
    }

    public void initBlossom(EntityGadget gadget) {
        if (createdEntity.contains(gadget)) {
            return;
        }
        if (blossomConsumed.contains(gadget.getSpawnEntry())) {
            return;
        }
        var id = gadget.getGadgetId();
        if (BlossomType.valueOf(id) == null) {
            return;
        }
        // Source gadget rows may carry a generic type under 7.1; a ley-line outcrop
        // always needs a worktop to start its combat challenge.
        GadgetWorktop gadgetWorktop = gadget.getContent() instanceof GadgetWorktop worktop
                ? worktop : new GadgetWorktop(gadget);
        gadget.replaceContent(gadgetWorktop);
        gadget.setState(204);
        gadgetWorktop.addWorktopOptions(new int[] {187});
        gadgetWorktop.setOnSelectWorktopOptionEvent(
                (GadgetWorktop context, int option) -> {
                    if (option != 187) return false;
                    EntityGadget source = context.getGadget();
                    BlossomActivity activity = new BlossomActivity(source);
                    synchronized (blossomActivities) {
                        if (blossomActivities.stream().anyMatch(current ->
                                current.getGadget() == source)) return false;
                        blossomActivities.add(activity);
                    }

                    // Dispatch only the original scene's select trigger.
                    // Keep the worktop available if the native script fails.
                    var group = scriptGroupFor(source);
                    int monsterGoal = group == null || group.monsters == null ? 0 : group.monsters.size();
                    if (!beginNativeEncounter(source, monsterGoal)
                            || !scene.getScriptManager().dispatchBlossomSelect(
                                    source.getGroupId(), source.getConfigId(), option)) {
                        activity.abort();

                        synchronized (blossomActivities) {
                            blossomActivities.remove(activity);
                        }
                        // The activity owns its transient Lua schedule state.
                        source.updateState(204);
                        context.addWorktopOptions(new int[] {187});
                        return false;
                    }
                    source.updateState(201);
                    // Scene.selectWorktopOptionWith removes the selected gadget exactly once.
                    return true;
                });
        createdEntity.add(gadget);
        notifyIcon();
    }

    /** Each player receives only the outcrops unlocked at their current Adventure Rank. */
    public void notifyIcon() {
        activeScriptedSpawns();
        mapMarkersDirty = false;
        scene.getPlayers().forEach(this::notifyIcon);
    }

    /** Sync map markers after EnterSceneDone, including when no outcrop is nearby. */
    public void notifyIcon(Player player) {
        player.sendPacket(new PacketBlossomBriefInfoNotify(buildBriefInfos(player)));
    }

    private List<BlossomBriefInfoOuterClass.BlossomBriefInfo> buildBriefInfos(Player player) {
        final int wl = getWorldLevel();
        final int worldLevel = (wl < 0) ? 0 : ((wl > 9) ? 9 : wl);
        final var worldLevelData = GameData.getWorldLevelDataMap().get(worldLevel);
        final int monsterLevel = (worldLevelData != null) ? worldLevelData.getMonsterLevel() : 1;
        var blossoms = new ArrayList<BlossomBriefInfoOuterClass.BlossomBriefInfo>();
        for (var spawn : activeScriptedSpawns()) {
            if (blossomConsumed.contains(spawn)) continue;
            var type = BlossomType.valueOf(spawn.getGadgetId());
            int refreshId = nativeCamps.refreshId(spawn);
            int campId = nativeCamps.campId(spawn);
            var refresh = GameData.getBlossomRefreshExcelConfigDataMap().get(refreshId);
            if (type == null || refresh == null
                    || !refreshEligible(refresh, player.getLevel(),
                            player.getUnlockedSceneAreas(scene.getId()), GameData.getCityDataMap().values()))
                continue;
            int previewReward = getPreviewReward(refresh, worldLevel);
            blossoms.add(BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                    .setSceneId(scene.getId())
                    .setPos(spawn.getPos().toProto())
                    .setResin(20)
                    .setMonsterLevel(monsterLevel)
                    .setRewardId(previewReward)
                    .setCircleCampId(campId)
                    .setRefreshId(refreshId)
                    .build());
        }
        return blossoms;
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
    }

    /** Read the map preview from this flower's own country-specific refresh row. */
    static int getPreviewReward(BlossomRefreshExcelConfigData refresh, int worldLevel) {
        if (refresh == null || worldLevel < 0 || worldLevel > 9) {
            throw new IllegalArgumentException("Invalid blossom refresh or world level");
        }
        // Native Excel has WL0..WL8 previews; WL9 uses the capped WL8 value.
        int index = Math.min(worldLevel, 8);
        var drops = refresh.getDropVec();
        if (drops == null || drops.length <= index || drops[index] == null
                || drops[index].getPreviewReward() <= 0) {
            throw new IllegalStateException(
                    "Missing blossom preview: city=" + refresh.getCityId()
                            + " refresh=" + refresh.getId() + " WL" + worldLevel);
        }
        return drops[index].getPreviewReward();
    }

    public boolean hasRewardChest(EntityGadget chest) {
        synchronized (blossomActivities) {
            return blossomActivities.stream().anyMatch(active -> active.isClaimableChest(chest));
        }
    }

    /** Called once the reward chest is fully claimed; ignores chests this manager doesn't own. */
    public void retireChest(EntityGadget chest) {
        synchronized (blossomActivities) {
            var it = blossomActivities.iterator();
            while (it.hasNext()) {
                var active = it.next();
                if (active.isClaimableChest(chest)) {
                    it.remove();
                    // Debug-spawned outcrops have no authored SpawnDataEntry. Never
                    // mark null as consumed, or later /spawn outcrops all get skipped.
                    var source = active.getGadget().getSpawnEntry();
                    if (source != null) {
                        if (nativeCamps.advance(scene, source, scene.getWorld().getHost().getLevel())) {
                            activeSpawnCache = null;
                            activeSetRevision++;
                        } else {
                            blossomConsumed.add(source);
                        }
                        // Unregister the completed group's Lua triggers before entering its next camp.
                        if (scene.getScriptManager().getGroupInstanceById(
                                active.getGadget().getGroupId()) != null)
                            scene.unloadGroup(null, active.getGadget().getGroupId());
                    }
                    recycleGadgetEntity(List.of(active.getGadget()));
                    ensureScriptedSpawns();
                    notifyIcon();
                    return;
                }
            }
        }
    }

    /**
     * Claim a completed ley-line flower. Roll the two rewards independently for
     * condensed resin, preflight the complete inventory batch, then charge once.
     * Admission/payment refusal permits retry. Ambiguous delivery failures retain a receipt.
     */
    record ClaimCost(int rolls, int itemId, int amount) {}

    /**
     * Global 7.1 GadgetInteractReq field 14 selects one (20 resin) or two (40 resin)
     * original-resin claims. Both use ResinCostType_NORMAL on the wire.
     */
    static ClaimCost claimCost(
            ResinCostTypeOuterClass.ResinCostType costType, int buyCount, int normalClaimCount) {
        return switch (costType) {
            case ResinCostType_NONE, ResinCostType_NORMAL ->
                    normalClaimCount == 1 || normalClaimCount == 2
                            ? new ClaimCost(normalClaimCount, 106, 20 * normalClaimCount)
                            : null;
            case ResinCostType_CONDENSE -> new ClaimCost(3, 220007, 1);
            case ResinCostType_FRAGILE -> new ClaimCost(3, 107009, 1);
            case ResinCostType_TIMING -> new ClaimCost(3, 107012, 1);
            case ResinCostType_HCOIN -> buyCount < ResinManager.MAX_RESIN_BUYING_COUNT
                    ? new ClaimCost(3, 201, ResinManager.HCOIN_NUM_TO_BUY_RESIN[buyCount]) : null;
            default -> null;
        };
    }

    /** Global 7.1 original-resin amount selector: GadgetInteractReq field 14. */
    static int normalClaimCount(GadgetInteractReqOuterClass.GadgetInteractReq req) {
        return (int) req.getField(req.getDescriptorForType().findFieldByNumber(14));
    }

    public List<GameItem> claimReward(Player player, EntityGadget chest,
            GadgetInteractReqOuterClass.GadgetInteractReq req, Runnable confirmed) {
        synchronized (blossomActivities) {
            BlossomActivity active = blossomActivities.stream()
                    .filter(candidate -> candidate.isClaimableChest(chest))
                    .findFirst().orElse(null);
            if (active == null || active.rewardClaimed(player.getUid())) return null;

            BlossomType type = BlossomType.valueOf(active.getGadget().getGadgetId());
            if (type == null) throw new IllegalStateException("Unknown claimed blossom type");
            var costType = req.getResinCostType();
            int normalClaimCount = normalClaimCount(req);
            var cost = claimCost(costType, player.getResinBuyCount(), normalClaimCount);
            if (cost == null) {
                Grasscutter.getLogger().warn(
                        "Ley-line claim rejected uid={} resinCostType={} field14={}",
                        player.getUid(), costType, normalClaimCount);
                return null;
            }
            int resinBefore = player.getProperty(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_RESIN);
            int worldLevel = getWorldLevel();
            Map<Integer, Integer> rolled = RewardConfiguration.current().rollBlossom(
                    type.rewardKind(), worldLevel, cost.rolls(),
                    java.util.concurrent.ThreadLocalRandom.current());
            var items = rolled.entrySet().stream()
                    .map(entry -> new GameItem(entry.getKey(), entry.getValue()))
                    .toList();
            var resin = player.getResinManager();
            var journal = new RewardGrantJournal();
            var key = new RewardGrantJournal.Key(RewardGrantJournal.Source.BLOSSOM, player.getUid(),
                    "flower." + scene.getId() + "." + active.getGadget().getGroupId() + "."
                            + active.getGadget().getConfigId() + "." + active.rewardOperation(player.getUid()));
            var snapshot = items.stream().map(item -> new RewardGrantJournal.Reward(
                    item.getItemId(), item.getCount(), item.getLevel())).toList();
            var debits = Configuration.GAME_OPTIONS.resinOptions.resinUsage
                    ? List.of(new RewardGrantJournal.Debit(cost.itemId(), cost.amount()))
                    : List.<RewardGrantJournal.Debit>of();
            boolean accepted = RewardClaimFlow.grant(
                    RewardClaimFlow.receipt(journal, key, snapshot, debits),
                    new RewardClaimFlow.Delivery() {
                        public boolean admit(java.util.function.BooleanSupplier authorize, Runnable claim) {
                            return player.getInventory().addItems(items, ActionReason.OpenWorldBossChest,
                                    InventoryAddPolicy.ALL_OR_NOTHING, authorize, claim).allAccepted();
                        }
                        public void publish() {}
                    }, () -> switch (costType) {
                        case ResinCostType_CONDENSE -> resin.useCondensedResin(1);
                        case ResinCostType_FRAGILE, ResinCostType_TIMING ->
                                !Configuration.GAME_OPTIONS.resinOptions.resinUsage
                                        || player.getInventory().payItem(cost.itemId(), cost.amount());
                        case ResinCostType_HCOIN -> resin.payHcoinRewardClaim();
                        default -> resin.useResin(cost.amount());
                    },
                    () -> {
                        active.confirmReward(player.getUid());
                        confirmed.run();
                    }, () -> active.resetUnpaidReward(player.getUid()));
            if (!accepted) {
                Grasscutter.getLogger().warn("Ley-line claim refused uid={} kind={} receipt={}",
                        player.getUid(), type, key.storageId());
                return null;
            }
            Grasscutter.getLogger().info(
                    "Ley-line claim settled uid={} kind={} resinCostType={} rolls={} debitItem={} debitAmount={} resinBefore={} resinAfter={} rewards={}",
                    player.getUid(), type, costType, cost.rolls(), cost.itemId(), cost.amount(),
                    resinBefore,
                    player.getProperty(emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_RESIN),
                    rolled);
            return items;
        }
    }


}
