package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.RewardGrantJournal;
import emu.grasscutter.game.reward.RewardClaimFlow;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.*;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.reward.RewardConfiguration;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.PacketBlossomBriefInfoNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

public class BlossomManager {
    private static final Set<BlossomType> MISSING_TYPES_REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final Scene scene;
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final List<BlossomActivity> activeChests = new ArrayList<>();
    private final List<EntityGadget> createdEntity = new ArrayList<>();

    private final List<SpawnDataEntry> blossomConsumed = new ArrayList<>();
    private List<SpawnDataEntry> scriptedSpawnCandidates;
    private final Set<SpawnDataEntry> scriptedSpawned = new HashSet<>();
    private static final double SCRIPTED_SPAWN_RANGE = 400;
    private static final double OVERLAP_RANGE = 2.5;
    private BlossomGeoIndex geometry;
    private long lastCycleDay = Long.MIN_VALUE;

    private record ActiveSite(int refreshId, SpawnDataEntry spawn) {}

    /** Native BlossomRefreshExcelConfigData entries drive one active site per city/type. */
    private List<ActiveSite> activeSites() {
        if (scriptedSpawnCandidates == null) {
            scriptedSpawnCandidates = scriptedCandidates(GameDepot.getSpawnLists(), scene.getId());
        }
        if (scriptedSpawnCandidates.isEmpty()) return List.of();
        var owner = scene.getWorld().getHost();
        if (owner == null) return List.of();
        long day = cycleDay();
        if (lastCycleDay != day) {
            lastCycleDay = day;
            blossomConsumed.clear();
            scriptedSpawned.clear();
        }
        if (geometry == null) geometry = BlossomGeoIndex.load(scene.getId());
        var byCityAndType = new HashMap<String, List<SpawnDataEntry>>();
        for (var spawn : scriptedSpawnCandidates) {
            int city = geometry.cityId(spawn.getPos().getX(), spawn.getPos().getZ());
            if (city == 0) continue; // Excludes offshore/unattributed imported candidates.
            BlossomType type = BlossomType.valueOf(spawn.getGadgetId());
            byCityAndType.computeIfAbsent(city + ":" + type.refreshType(), unused -> new ArrayList<>())
                    .add(spawn);
        }
        var configurations = new ArrayList<>(
                GameData.getBlossomRefreshExcelConfigDataMap().values());
        configurations.sort(java.util.Comparator
                .comparingInt((emu.grasscutter.data.excels.BlossomRefreshExcelConfigData row) ->
                        "BLOSSOM_REFRESH_EXP".equals(row.getRefreshType()) ? 0 : 1)
                .thenComparingInt(emu.grasscutter.data.excels.BlossomRefreshExcelConfigData::getId));
        var sites = new ArrayList<ActiveSite>();
        for (var config : configurations) {
            if (!"BLOSSOM_REFRESH_EXP".equals(config.getRefreshType())
                    && !"BLOSSOM_REFRESH_SCOIN".equals(config.getRefreshType())) continue;
            if (!eligible(config, owner)) continue;
            var pool = byCityAndType.get(config.getCityId() + ":" + config.getRefreshType());
            if (pool == null || pool.isEmpty()) continue;
            int offset = owner.getBlossomRefreshOffset(config.getId(), day);
            int index = Math.floorMod(
                    java.util.Objects.hash(owner.getUid(), day, config.getId()) + offset,
                    pool.size());
            int want = Math.min(config.getRefreshCount(), pool.size());
            int added = 0;
            for (int i = 0; i < pool.size() && added < want; i++) {
                var candidate = pool.get((index + i) % pool.size());
                if (blossomConsumed.contains(candidate)) continue;
                if (sites.stream().anyMatch(site ->
                        site.spawn().getPos().computeDistance(candidate.getPos()) <= OVERLAP_RANGE)) {
                    continue;
                }
                sites.add(new ActiveSite(config.getId(), candidate));
                added++;
            }
        }
        return sites;
    }

    /** 04:00 local reset, matching the native blossom refresh table. */
    private static long cycleDay() {
        return java.time.LocalDateTime.now().minusHours(4).toLocalDate().toEpochDay();
    }

    static boolean eligible(emu.grasscutter.data.excels.BlossomRefreshExcelConfigData config,
                            Player player) {
        var conditions = config.getRefreshCondVec();
        if (conditions == null) return true;
        for (var condition : conditions) {
            if (condition.getType() == null) continue;
            var params = condition.getParam();
            switch (condition.getType()) {
                case "BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER" -> {
                    if (player.getLevel() < params.get(0)) return false;
                }
                case "BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY" -> {
                    var city = GameData.getCityDataMap().get(config.getCityId());
                    if (city == null || city.getAreaIdVec() == null
                            || city.getAreaIdVec().stream().noneMatch(
                                area -> player.getUnlockedSceneAreas(
                                        player.getSceneId()).contains(area))) return false;
                }
                default -> throw new IllegalStateException(
                        "Unknown blossom condition " + condition.getType());
            }
        }
        return true;
    }

    /** The scene's legacy grid and script-group loaders must use the same active selection. */
    public boolean isActiveSpawn(SpawnDataEntry spawn) {
        return activeSites().stream().anyMatch(site -> site.spawn() == spawn);
    }

    public void ensureScriptedSpawns() {
        if (scene.getDungeonManager() != null || scene.getPlayers().isEmpty()) return;
        var active = activeSites();
        removeInactiveAuthoredOutcrops(active);
        for (var site : active) {
            var spawn = site.spawn();
            if (scriptedSpawned.contains(spawn) || blossomConsumed.contains(spawn)) continue;
            boolean nearby = scene.getPlayers().stream()
                    .anyMatch(player -> player.getPosition().computeDistance(spawn.getPos())
                            <= SCRIPTED_SPAWN_RANGE);
            if (!nearby) continue;

            EntityGadget existing = scene.getEntities().values().stream()
                    .filter(entity -> entity instanceof EntityGadget)
                    .map(entity -> (EntityGadget) entity)
                    .filter(entity -> entity.getGadgetId() == spawn.getGadgetId())
                    .filter(entity -> entity.getPosition().computeDistance(spawn.getPos())
                            <= OVERLAP_RANGE)
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.setSpawnEntry(spawn);
                initBlossom(existing);
                scene.broadcastPacket(new emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify(
                        existing, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH, 0));
            } else {
                var gadget = new EntityGadget(
                        scene, spawn.getGadgetId(), spawn.getPos(), spawn.getRot());
                gadget.setGroupId(spawn.getGroup().getGroupId());
                gadget.setConfigId(spawn.getConfigId());
                gadget.setSpawnEntry(spawn);
                if (spawn.getGadgetState() > 0) gadget.setState(spawn.getGadgetState());
                initBlossom(gadget);
                scene.addEntity(gadget);
            }
            scriptedSpawned.add(spawn);
        }
    }

    private void removeInactiveAuthoredOutcrops(List<ActiveSite> active) {
        var loaded = scene.getEntities().values().stream()
                .filter(entity -> entity instanceof EntityGadget)
                .map(entity -> (EntityGadget) entity)
                .filter(entity -> entity.getGroupId() != 0)
                .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                .toList();
        var kept = new ArrayList<EntityGadget>();
        for (var gadget : loaded) {
            boolean selected = active.stream().anyMatch(site ->
                    site.spawn().getGadgetId() == gadget.getGadgetId()
                    && site.spawn().getPos().computeDistance(gadget.getPosition()) <= OVERLAP_RANGE);
            boolean duplicate = kept.stream().anyMatch(other ->
                    other.getPosition().computeDistance(gadget.getPosition()) <= OVERLAP_RANGE);
            if (!selected || duplicate) {
                scene.removeEntity(gadget,
                        VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                createdEntity.remove(gadget);
                continue;
            }
            kept.add(gadget);
        }
    }

    /** Preserve both blossom types as potential locations; suppress same-type overlapping records. */
    static List<SpawnDataEntry> scriptedCandidates(
            Map<SpawnDataEntry.GridBlockId, ? extends List<SpawnDataEntry>> spawnLists,
            int sceneId) {
        var source = spawnLists.entrySet().stream()
                .filter(entry -> entry.getKey().getSceneId() == sceneId)
                .flatMap(entry -> entry.getValue().stream())
                .filter(entry -> BlossomType.valueOf(entry.getGadgetId()) != null)
                .distinct()
                .sorted(java.util.Comparator
                        .comparingInt(SpawnDataEntry::getGadgetId)
                        .thenComparingInt(entry -> entry.getGroup().getGroupId())
                        .thenComparingInt(SpawnDataEntry::getConfigId))
                .toList();
        var distinct = new ArrayList<SpawnDataEntry>();
        for (var entry : source) {
            if (distinct.stream().noneMatch(other ->
                    other.getGadgetId() == entry.getGadgetId()
                    && other.getPos().computeDistance(entry.getPos()) <= OVERLAP_RANGE)) {
                distinct.add(entry);
            }
        }
        return distinct;
    }

    public BlossomManager(Scene scene) {
        this.scene = scene;
    }

    public void onTick() {
        synchronized (blossomActivities) {
            var it = blossomActivities.iterator();
            while (it.hasNext()) {
                var active = it.next();
                active.onTick();
                if (active.getPass()) {
                    EntityGadget chest = active.getChest();
                    scene.addEntity(chest);
                    scene.setChallenge(null);
                    activeChests.add(active);
                    it.remove();
                }
            }
        }
    }

    public void recycleGadgetEntity(List<GameEntity> entities) {
        for (var entity : entities) {
            if (entity instanceof EntityGadget gadget) {
                createdEntity.remove(gadget);
            }
        }
        notifyIcon();
    }

    public void initBlossom(EntityGadget gadget) {
        if (gadget.getSpawnEntry() != null && !isActiveSpawn(gadget.getSpawnEntry())) return;
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
        int worldLevel = getWorldLevel();
        gadgetWorktop.addWorktopOptions(new int[] {187});
        gadgetWorktop.setOnSelectWorktopOptionEvent(
                (GadgetWorktop context, int option) -> {
                    BlossomActivity activity;
                    EntityGadget entityGadget = context.getGadget();
                    synchronized (blossomActivities) {
                        for (BlossomActivity i : this.blossomActivities) {
                            if (i.getGadget() == entityGadget) {
                                return false;
                            }
                        }

                        int volume = 0;
                        IntList monsters = new IntArrayList();
                        while (true) {
                            var remain = GameDepot.getBlossomConfig().getMonsterFightingVolume() - volume;
                            if (remain <= 0) {
                                break;
                            }
                            var rand = Utils.randomRange(1, 100);
                            if (rand > 85 && remain >= 50) { // 15% ,generate strong monster
                                monsters.addAll(getRandomMonstersID(2, 1));
                                volume += 50;
                            } else if (rand > 50 && remain >= 20) { // 35% ,generate normal monster
                                monsters.addAll(getRandomMonstersID(1, 1));
                                volume += 20;
                            } else { // 50% ,generate weak monster
                                monsters.addAll(getRandomMonstersID(0, 1));
                                volume += 10;
                            }
                        }

                        Grasscutter.getLogger().info("Blossom Monsters:" + monsters);

                        activity = new BlossomActivity(entityGadget, monsters, -1, worldLevel);
                        blossomActivities.add(activity);
                    }
                    entityGadget.updateState(201);
                    scene.setChallenge(activity.getChallenge());
                    scene.removeEntity(entityGadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                    activity.start();
                    return true;
                });
        createdEntity.add(gadget);
        notifyIcon();
    }

    /** Each player receives only the outcrops unlocked at their current Adventure Rank. */
    public void notifyIcon() {
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
        for (var site : activeSites()) {
            var spawn = site.spawn();
            var type = BlossomType.valueOf(spawn.getGadgetId());
            Integer previewReward = getPreviewReward(type, worldLevel);
            if (previewReward == null) continue;
            blossoms.add(BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                    .setSceneId(scene.getId())
                    .setPos(spawn.getPos().toProto())
                    .setResin(20)
                    .setMonsterLevel(monsterLevel)
                    .setRewardId(previewReward)
                    .setCircleCampId(type.getCircleCampId())
                    .setRefreshId(type.getBlossomChestId())
                    .build());
        }
        return blossoms;
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
    }

    private static Integer getPreviewReward(BlossomType type, int worldLevel) {
        if (type == null || worldLevel < 0 || worldLevel > 9) {
            throw new IllegalArgumentException(
                    "Unsupported blossom type/world level: " + type + "/" + worldLevel);
        }
        // Both kinds have blossomChestId=1 in the native Excel rows. The actual
        // distinction is refreshType (SCOIN vs EXP), shared across cities.
        Integer previewId = null;
        for (var data : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
            if (!type.refreshType().equals(data.getRefreshType())) continue;
            var drops = data.getDropVec();
            // Native Excel has 9 preview tiers (WL0..WL8); WL9 shares the capped WL8 preview.
            int index = worldLevel == 9 ? 8 : worldLevel;
            if (drops == null || drops.length <= index || drops[index] == null
                    || drops[index].getPreviewReward() <= 0) {
                throw new IllegalStateException(
                        "Missing native blossom preview: " + type + " WL" + worldLevel
                                + " refresh=" + data.getId());
            }
            int candidate = drops[index].getPreviewReward();
            if (previewId != null && previewId != candidate) {
                throw new IllegalStateException(
                        "Conflicting native blossom previews: " + type + " WL" + worldLevel);
            }
            previewId = candidate;
        }
        if (previewId == null && MISSING_TYPES_REPORTED.add(type)) {
            Grasscutter.getLogger().error(
                    "Missing native blossom refresh type {} for {}", type.refreshType(), type);
        }
        return previewId;
    }

    public boolean hasRewardChest(EntityGadget chest) {
        synchronized (activeChests) {
            return activeChests.stream().anyMatch(active -> active.getChest() == chest);
        }
    }

    /** Called once the reward chest is fully claimed; ignores chests this manager doesn't own. */
    public void retireChest(EntityGadget chest) {
        synchronized (activeChests) {
            var it = activeChests.iterator();
            while (it.hasNext()) {
                var active = it.next();
                if (active.getChest() == chest) {
                    it.remove();
                    // Debug-spawned outcrops have no authored SpawnDataEntry. Never
                    // mark null as consumed, or later /spawn outcrops all get skipped.
                    if (active.getGadget().getSpawnEntry() != null) {
                        var source = active.getGadget().getSpawnEntry();
                        int refreshId = activeSites().stream()
                                .filter(site -> site.spawn() == source)
                                .map(ActiveSite::refreshId)
                                .findFirst().orElseThrow();
                        blossomConsumed.add(source);
                        scene.getWorld().getHost().advanceBlossomRefresh(refreshId, cycleDay());
                        scriptedSpawned.remove(source);
                    }
                    recycleGadgetEntity(List.of(active.getGadget()));
                    ensureScriptedSpawns();
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
    public List<GameItem> claimReward(Player player, EntityGadget chest, boolean condensed,
            Runnable confirmed) {
        synchronized (activeChests) {
            BlossomActivity active = activeChests.stream()
                    .filter(candidate -> candidate.getChest() == chest)
                    .findFirst().orElse(null);
            if (active == null || active.rewardClaimed(player.getUid())) return null;

            BlossomType type = BlossomType.valueOf(active.getGadget().getGadgetId());
            if (type == null) throw new IllegalStateException("Unknown claimed blossom type");
            int worldLevel = getWorldLevel();
            Map<Integer, Integer> rolled = RewardConfiguration.current().rollBlossom(
                    type.rewardKind(), worldLevel, condensed,
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
                    ? List.of(new RewardGrantJournal.Debit(condensed ? 220007 : 106, condensed ? 1 : 20))
                    : List.<RewardGrantJournal.Debit>of();
            boolean accepted = RewardClaimFlow.grant(
                    RewardClaimFlow.receipt(journal, key, snapshot, debits),
                    new RewardClaimFlow.Delivery() {
                        public boolean admit(java.util.function.BooleanSupplier authorize, Runnable claim) {
                            return player.getInventory().addItems(items, ActionReason.OpenWorldBossChest,
                                    InventoryAddPolicy.ALL_OR_NOTHING, authorize, claim).allAccepted();
                        }
                        public void publish() {}
                    }, () -> condensed ? resin.useCondensedResin(1) : resin.useResin(20),
                    () -> {
                        active.confirmReward(player.getUid());
                        confirmed.run();
                    }, () -> active.resetUnpaidReward(player.getUid()));
            if (!accepted) {
                Grasscutter.getLogger().warn("Ley-line claim refused uid={} kind={} receipt={}",
                        player.getUid(), type, key.storageId());
                return null;
            }
            return items;
        }
    }

    public static IntList getRandomMonstersID(int difficulty, int count) {
        IntList result = new IntArrayList();
        List<Integer> monsters =
                GameDepot.getBlossomConfig().getMonsterIdsPerDifficulty().get(difficulty);
        for (int i = 0; i < count; i++) {
            result.add((int) monsters.get(Utils.randomRange(0, monsters.size() - 1)));
        }
        return result;
    }
}
