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
    private static final float OVERLAP_RANGE = 2.5f;
    private String activeDay = "";
    private boolean pendingDailyMapSync;
    private final Map<SpawnDataEntry, Integer> sourceCity = new IdentityHashMap<>();

    /**
     * The original refresh rows set a count per city and refresh type. Candidate
     * coordinates are imported server data, so use the exported WorldArea polygons
     * to associate them with the shipped CityConfig area IDs. Unknown points are
     * excluded, including legacy gadget points outside defined world areas.
     */
    private Set<SpawnDataEntry> activeSources() {
        Player host = scene.getWorld().getHost();
        var day = refreshDay(java.time.LocalDateTime.now());
        if (!day.equals(host.getBlossomRefreshDay())) {
            host.getBlossomRotations().clear();
            host.setBlossomRefreshDay(day);
            host.save();
        }
        if (!day.equals(activeDay)) {
            if (!activeDay.isEmpty()) pendingDailyMapSync = true;
            activeDay = day;
            blossomConsumed.clear();
            scriptedSpawned.clear();
        }
        if (scriptedSpawnCandidates == null) {
            scriptedSpawnCandidates = scriptedCandidates(GameDepot.getSpawnLists(), scene.getId());
            Grasscutter.getLogger().info(
                    "Ley-line candidate sources scene={} total={}",
                    scene.getId(), scriptedSpawnCandidates.size());
        }
        Map<String, List<SpawnDataEntry>> grouped = new TreeMap<>();
        for (var spawn : scriptedSpawnCandidates) {
            int city = sourceCity.computeIfAbsent(
                    spawn, source -> BlossomAreaIndex.cityFor(scene.getId(), source.getPos()));
            var type = BlossomType.valueOf(spawn.getGadgetId());
            if (city == 0 || type == null) continue;
            grouped.computeIfAbsent(slotKey(city, type), unused -> new ArrayList<>())
                    .add(spawn);
        }
        var selected = new LinkedHashSet<SpawnDataEntry>();
        for (var config : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
            var type = Arrays.stream(BlossomType.values())
                    .filter(t -> t.refreshType().equals(config.getRefreshType()))
                    .findFirst().orElse(null);
            if (type == null || config.getRefreshCount() < 1
                    || !eligible(host, config, scene.getId())) continue;
            String key = slotKey(config.getCityId(), type);
            var sources = grouped.get(key);
            if (sources == null || sources.isEmpty()) continue;
            selected.addAll(selectSources(
                    sources, config.getRefreshCount(), host.getUid(), key, day,
                    host.getBlossomRotations().getOrDefault(key, 0)));
        }
        return selected;
    }

    static String refreshDay(java.time.LocalDateTime now) {
        var date = now.toLocalTime().isBefore(java.time.LocalTime.of(4, 0))
                ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        return date.toString();
    }

    static String slotKey(int city, BlossomType type) {
        return city + ":" + type.name();
    }

    static List<SpawnDataEntry> selectSources(
            List<SpawnDataEntry> sources, int count, int uid, String key,
            String day, int claimed) {
        if (sources.isEmpty() || count <= 0) return List.of();
        int first = Math.floorMod(Objects.hash(uid, key, day), sources.size());
        var result = new ArrayList<SpawnDataEntry>();
        for (int i = 0; i < Math.min(count, sources.size()); i++) {
            result.add(sources.get(Math.floorMod(first + claimed + i, sources.size())));
        }
        return result;
    }

    static boolean eligible(
            Player player, emu.grasscutter.data.excels.BlossomRefreshExcelConfigData config,
            int sceneId) {
        var conditions = config.getRefreshCondVec();
        if (conditions == null) return true;
        for (var cond : conditions) {
            if (cond == null || cond.getType() == null || cond.getType().isBlank()) continue;
            var params = cond.getParam();
            if (params == null || params.isEmpty()) return false;
            switch (cond.getType()) {
                case "BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER" -> {
                    if (player.getLevel() < params.get(0)) return false;
                }
                case "BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY" -> {
                    var city = GameData.getCityDataMap().get(params.get(0).intValue());
                    if (city == null || city.getAreaIdVec() == null) return false;
                    var unlocked = player.getUnlockedSceneAreas(sceneId);
                    if (city.getAreaIdVec().stream().noneMatch(unlocked::contains)) return false;
                }
                default -> {
                    // No invented semantics for an unknown native condition.
                    return false;
                }
            }
        }
        return true;
    }

    /** The map, worktops and authored scene spawns must share one selection. */
    public boolean shouldSpawn(SpawnDataEntry entry) {
        return entry == null || BlossomType.valueOf(entry.getGadgetId()) == null
                || activeSources().contains(entry);
    }

    public void ensureScriptedSpawns() {
        if (scene.getDungeonManager() != null || scene.getPlayers().isEmpty()) return;
        var selected = activeSources();
        if (pendingDailyMapSync) {
            pendingDailyMapSync = false;
            notifyIcon();
        }
        removeInactiveAuthoredOutcrops(selected);
        for (var spawn : selected) {
            if (scriptedSpawned.contains(spawn) || blossomConsumed.contains(spawn)) continue;
            boolean nearby = scene.getPlayers().stream()
                    .anyMatch(player -> player.getPosition().computeDistance(spawn.getPos())
                            <= SCRIPTED_SPAWN_RANGE);
            if (!nearby) continue;
            EntityGadget existing = scene.getEntities().values().stream()
                    .filter(entity -> entity instanceof EntityGadget)
                    .map(entity -> (EntityGadget) entity)
                    .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                    .filter(entity -> entity.getPosition().computeDistance(spawn.getPos())
                            <= OVERLAP_RANGE)
                    .findFirst().orElse(null);
            if (existing != null) {
                if (existing.getGroupId() == spawn.getGroup().getGroupId()
                        && existing.getConfigId() == spawn.getConfigId()
                        && existing.getGadgetId() == spawn.getGadgetId()) {
                    existing.setSpawnEntry(spawn);
                    initBlossom(existing);
                }
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

    private void removeInactiveAuthoredOutcrops(Set<SpawnDataEntry> selected) {
        var loaded = scene.getEntities().values().stream()
                .filter(entity -> entity instanceof EntityGadget)
                .map(entity -> (EntityGadget) entity)
                .filter(entity -> entity.getGroupId() != 0)
                .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                .toList();
        var kept = new ArrayList<EntityGadget>();
        for (var gadget : loaded) {
            boolean current = selected.stream().anyMatch(spawn ->
                    spawn.getGadgetId() == gadget.getGadgetId()
                    && spawn.getPos().computeDistance(gadget.getPosition()) <= OVERLAP_RANGE);
            boolean duplicate = kept.stream().anyMatch(other ->
                    other.getPosition().computeDistance(gadget.getPosition()) <= OVERLAP_RANGE);
            if (!current || duplicate) {
                scene.removeEntity(gadget,
                        VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                createdEntity.remove(gadget);
            } else {
                kept.add(gadget);
            }
        }
    }

    /** Use existing spawn coordinates and gadget IDs, never synthetic ley-line positions. */
    static List<SpawnDataEntry> scriptedCandidates(
            Map<SpawnDataEntry.GridBlockId, ? extends List<SpawnDataEntry>> spawnLists,
            int sceneId) {
        var source = spawnLists.entrySet().stream()
                .filter(entry -> entry.getKey().getSceneId() == sceneId)
                .flatMap(entry -> entry.getValue().stream())
                .filter(entry -> BlossomType.valueOf(entry.getGadgetId()) != null)
                .distinct()
                // Prefer the earlier-unlocked Revelation outcrop when two source
                // entries occupy the same physical location.
                .sorted(java.util.Comparator
                        .comparingInt((SpawnDataEntry entry) ->
                                entry.getGadgetId() == BlossomType.BLUE.getGadgetId() ? 0 : 1)
                        .thenComparingInt(entry -> entry.getGroup().getGroupId())
                        .thenComparingInt(SpawnDataEntry::getConfigId))
                .toList();
        var distinct = new ArrayList<SpawnDataEntry>();
        for (var entry : source) {
            if (distinct.stream().noneMatch(other ->
                    other.getPos().computeDistance(entry.getPos()) <= OVERLAP_RANGE)) {
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
        if (createdEntity.contains(gadget)) {
            return;
        }
        if (blossomConsumed.contains(gadget.getSpawnEntry())) {
            return;
        }
        var id = gadget.getGadgetId();
        if (gadget.getSpawnEntry() != null && !shouldSpawn(gadget.getSpawnEntry())) return;
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

    public void notifyIcon() {
        scene.getPlayers().forEach(this::notifyIcon);
    }

    public void notifyIcon(Player player) {
        player.sendPacket(new PacketBlossomBriefInfoNotify(buildBriefInfos(player)));
    }

    private List<BlossomBriefInfoOuterClass.BlossomBriefInfo> buildBriefInfos(Player player) {
        int wl = Math.clamp(getWorldLevel(), 0, 9);
        var worldLevelData = GameData.getWorldLevelDataMap().get(wl);
        int monsterLevel = worldLevelData != null ? worldLevelData.getMonsterLevel() : 1;
        var result = new ArrayList<BlossomBriefInfoOuterClass.BlossomBriefInfo>();
        for (var spawn : activeSources()) {
            if (blossomConsumed.contains(spawn)) continue;
            var type = BlossomType.valueOf(spawn.getGadgetId());
            int city = sourceCity.computeIfAbsent(
                    spawn, source -> BlossomAreaIndex.cityFor(scene.getId(), source.getPos()));
            if (type == null) continue;
            var refresh = GameData.getBlossomRefreshExcelConfigDataMap().values().stream()
                    .filter(d -> d.getCityId() == city && type.refreshType().equals(d.getRefreshType()))
                    .findFirst().orElse(null);
            if (refresh == null || !eligible(player, refresh, scene.getId())) continue;
            Integer preview = getPreviewReward(type, city, wl);
            if (preview == null) continue;
            result.add(BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                    .setSceneId(scene.getId())
                    .setPos(spawn.getPos().toProto())
                    .setResin(20)
                    .setMonsterLevel(monsterLevel)
                    .setRewardId(preview)
                    .setCircleCampId(type.getCircleCampId())
                    .setRefreshId(refresh.getId())
                    .build());
        }
        return result;
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
    }

    private static Integer getPreviewReward(BlossomType type, int cityId, int worldLevel) {
        if (type == null || worldLevel < 0 || worldLevel > 9) {
            throw new IllegalArgumentException(
                    "Unsupported blossom type/world level: " + type + "/" + worldLevel);
        }
        // Both kinds have blossomChestId=1 in the native Excel rows. The actual
        // distinction is refreshType (SCOIN vs EXP), shared across cities.
        Integer previewId = null;
        for (var data : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
            if (data.getCityId() != cityId || !type.refreshType().equals(data.getRefreshType())) continue;
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
                        var spawn = active.getGadget().getSpawnEntry();
                        blossomConsumed.add(spawn);
                        int city = sourceCity.computeIfAbsent(
                    spawn, source -> BlossomAreaIndex.cityFor(scene.getId(), source.getPos()));
                        var type = BlossomType.valueOf(spawn.getGadgetId());
                        if (city > 0 && type != null) {
                            Player host = scene.getWorld().getHost();
                            host.getBlossomRotations().merge(slotKey(city, type), 1, Integer::sum);
                            host.save();
                        }
                    }
                    recycleGadgetEntity(List.of(active.getGadget()));
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
