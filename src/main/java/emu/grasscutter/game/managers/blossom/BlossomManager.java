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
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.PacketBlossomBriefInfoNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

public class BlossomManager {
    private final Scene scene;
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final List<BlossomActivity> activeChests = new ArrayList<>();
    private final List<EntityGadget> createdEntity = new ArrayList<>();

    private List<SpawnDataEntry> scriptedSpawnCandidates;
    private final Set<SpawnDataEntry> scriptedSpawned = new HashSet<>();
    private static final double SCRIPTED_SPAWN_RANGE = 400;
    private static final float OVERLAP_RANGE = 2.5f;

    record ActiveOutcrop(SpawnDataEntry spawn, BlossomType type,
                         BlossomRefreshExcelConfigData refresh, int cityId) {}

    private List<ActiveOutcrop> activeOutcrops = List.of();
    private String selectedDay = "";
    private int selectedAdventureRank = -1;
    private Set<Integer> selectedUnlockedAreas = Set.of();
    private int selectedRevision = -1;
    private int selectionRevision;

    /** Original 7.1 refresh rows for these types all specify 04:00:00. */
    private static String currentRefreshDay() {
        return java.time.LocalDateTime.now().minusHours(4).toLocalDate().toString();
    }

    /** Stable initial pick and bounded circular rotation; no invented location coordinates. */
    static List<SpawnDataEntry> pickSources(
            List<SpawnDataEntry> pool, int count, int seed, int rotation) {
        if (pool.isEmpty() || count <= 0) return List.of();
        int start = Math.floorMod(seed, pool.size());
        var selected = new ArrayList<SpawnDataEntry>();
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            selected.add(pool.get(Math.floorMod(start + rotation + i, pool.size())));
        }
        return selected;
    }

    private List<SpawnDataEntry> sources() {
        if (scriptedSpawnCandidates == null) {
            scriptedSpawnCandidates = scriptedCandidates(GameDepot.getSpawnLists(), scene.getId());
            long gold = scriptedSpawnCandidates.stream()
                    .filter(entry -> BlossomType.valueOf(entry.getGadgetId()) == BlossomType.GOLD)
                    .count();
            Grasscutter.getLogger().info(
                    "Ley-line authored source pool scene={} wealth={} revelation={}",
                    scene.getId(), gold, scriptedSpawnCandidates.size() - gold);
        }
        return scriptedSpawnCandidates;
    }

    /** The world-area outline and native city-to-area table establish candidate ownership. */
    static int cityForArea(int areaId, Collection<CityData> cities) {
        if (areaId == 0) return 0;
        int resolved = 0;
        for (var city : cities) {
            if (city.getAreaIdVec() == null || !city.getAreaIdVec().contains(areaId)) continue;
            if (resolved != 0 && resolved != city.getCityId()) return 0;
            resolved = city.getCityId();
        }
        return resolved;
    }

    /** Apply the conditions declared on the exact native refresh row. */
    static boolean refreshEligible(
            BlossomRefreshExcelConfigData refresh, int rank, Set<Integer> unlockedAreas,
            List<Integer> cityAreas) {
        if (refresh.getRefreshCount() <= 0
                || !"BLOSSOM_SHOWTYPE_CHALLENGE".equals(refresh.getClientShowType())) return false;
        var conditions = refresh.getRefreshCondVec();
        if (conditions == null) return true;
        for (var condition : conditions) {
            if (condition.getType() == null) continue;
            var params = condition.getParam();
            switch (condition.getType()) {
                case "BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER" -> {
                    if (params == null || params.size() != 1 || rank < params.get(0)) return false;
                }
                case "BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY" -> {
                    if (params == null || params.size() != 1
                            || params.get(0) != refresh.getCityId()
                            || cityAreas == null || Collections.disjoint(unlockedAreas, cityAreas)) {
                        return false;
                    }
                }
                default -> {
                    // Unknown original condition types are not silently considered satisfied.
                    return false;
                }
            }
        }
        return true;
    }

    private synchronized List<ActiveOutcrop> selectedOutcrops() {
        Player owner = scene.getWorld().getHost();
        String day = currentRefreshDay();
        int rank = owner.getLevel();
        Set<Integer> areas = Set.copyOf(owner.getUnlockedSceneAreas(scene.getId()));
        if (day.equals(selectedDay) && rank == selectedAdventureRank
                && areas.equals(selectedUnlockedAreas) && selectedRevision == selectionRevision) {
            return activeOutcrops;
        }
        var outlines = WorldAreaIndex.forScene(scene.getId());
        var cities = GameData.getCityDataMap().values();
        var byKey = new HashMap<String, List<SpawnDataEntry>>();
        for (var spawn : sources()) {
            int area = outlines.level1AreaAt(spawn.getPos().getX(), spawn.getPos().getZ());
            int city = cityForArea(area, cities);
            if (city == 0) continue; // Excludes out-of-area/sea candidate positions.
            var type = BlossomType.valueOf(spawn.getGadgetId());
            byKey.computeIfAbsent(city + ":" + type.refreshType(), ignored -> new ArrayList<>())
                    .add(spawn);
        }
        var next = new ArrayList<ActiveOutcrop>();
        var rows = GameData.getBlossomRefreshExcelConfigDataMap().values().stream()
                .filter(row -> "BLOSSOM_SHOWTYPE_CHALLENGE".equals(row.getClientShowType()))
                .sorted(Comparator.comparingInt(BlossomRefreshExcelConfigData::getId))
                .toList();
        for (var row : rows) {
            var type = Arrays.stream(BlossomType.values())
                    .filter(t -> t.refreshType().equals(row.getRefreshType()))
                    .findFirst().orElse(null);
            if (type == null) continue;
            var city = GameData.getCityDataMap().get(row.getCityId());
            if (city == null || city.getSceneId() != scene.getId()
                    || !refreshEligible(row, rank, areas, city.getAreaIdVec())) continue;
            var pool = byKey.get(row.getCityId() + ":" + type.refreshType());
            if (pool == null || pool.isEmpty()) continue;
            int offset = owner.blossomRotationOffset(day, row.getCityId(), type.refreshType());
            for (var candidate : pickSources(pool, row.getRefreshCount(),
                    Objects.hash(day, row.getCityId(), type.getGadgetId()), offset)) {
                next.add(new ActiveOutcrop(candidate, type, row, row.getCityId()));
            }
        }
        activeOutcrops = List.copyOf(next);
        selectedDay = day;
        selectedAdventureRank = rank;
        selectedUnlockedAreas = areas;
        selectedRevision = selectionRevision;
        Grasscutter.getLogger().info(
                "Ley-line active scene={} day={} AR={} nativeRows={} active={}",
                scene.getId(), day, rank, rows.size(), next.size());
        return activeOutcrops;
    }

    /** The same selected source drives both native map packets and physical gadget spawning. */
    public boolean isActiveSource(SpawnDataEntry spawn) {
        return selectedOutcrops().stream().anyMatch(active -> active.spawn() == spawn);
    }

    public Set<SpawnDataEntry> activeSourceSet() {
        var result = new HashSet<SpawnDataEntry>();
        selectedOutcrops().forEach(active -> result.add(active.spawn()));
        return result;
    }

    public void ensureScriptedSpawns() {
        if (scene.getDungeonManager() != null || scene.getPlayers().isEmpty()) return;
        var before = activeOutcrops;
        var selected = selectedOutcrops();
        if (before != selected && !before.isEmpty()) notifyIcon();
        removeInactiveAuthoredOutcrops(selected);
        for (var active : selected) {
            var spawn = active.spawn();
            if (scriptedSpawned.contains(spawn)) continue;
            boolean nearby = scene.getPlayers().stream()
                    .anyMatch(player -> player.getPosition().computeDistance(spawn.getPos())
                            <= SCRIPTED_SPAWN_RANGE);
            if (!nearby) continue;

            EntityGadget existing = scene.getEntities().values().stream()
                    .filter(entity -> entity instanceof EntityGadget)
                    .map(entity -> (EntityGadget) entity)
                    .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) == active.type())
                    .filter(entity -> entity.getPosition().computeDistance(spawn.getPos())
                            <= OVERLAP_RANGE)
                    .findFirst().orElse(null);
            if (existing != null) {
                if (existing.getGroupId() == spawn.getGroup().getGroupId()
                        && existing.getConfigId() == spawn.getConfigId()
                        && existing.getGadgetId() == spawn.getGadgetId()) {
                    existing.setSpawnEntry(spawn);
                }
                initBlossom(existing);
                scene.broadcastPacket(new emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify(
                        existing, VisionTypeOuterClass.VisionType.VisionType_VISION_REFRESH, 0));
            } else {
                var gadget = new EntityGadget(scene, spawn.getGadgetId(), spawn.getPos(), spawn.getRot());
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

    /** Lua-loaded groups may contain all candidates. Only the active one may remain visible. */
    private void removeInactiveAuthoredOutcrops(List<ActiveOutcrop> selected) {
        var loaded = scene.getEntities().values().stream()
                .filter(entity -> entity instanceof EntityGadget)
                .map(entity -> (EntityGadget) entity)
                .filter(entity -> entity.getGroupId() != 0)
                .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                .sorted(Comparator.comparingInt(EntityGadget::getGroupId)
                        .thenComparingInt(EntityGadget::getConfigId))
                .toList();
        var kept = new ArrayList<EntityGadget>();
        for (var gadget : loaded) {
            boolean active = selected.stream().anyMatch(candidate ->
                    candidate.type() == BlossomType.valueOf(gadget.getGadgetId())
                            && gadget.getPosition().computeDistance(candidate.spawn().getPos())
                                    <= OVERLAP_RANGE);
            boolean duplicate = kept.stream().anyMatch(other ->
                    other.getPosition().computeDistance(gadget.getPosition()) <= OVERLAP_RANGE);
            if (!active || duplicate) {
                scene.removeEntity(gadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                createdEntity.remove(gadget);
                // A skipped or removed source can become active after the next rotation.
                if (gadget.getSpawnEntry() != null) {
                    scene.getSpawnedEntities().remove(gadget.getSpawnEntry());
                    scriptedSpawned.remove(gadget.getSpawnEntry());
                }
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
        var id = gadget.getGadgetId();
        var type = BlossomType.valueOf(id);
        if (type == null) return;
        if (gadget.getGroupId() != 0
                && selectedOutcrops().stream().noneMatch(selected ->
                        selected.type() == type
                                && gadget.getPosition().computeDistance(selected.spawn().getPos())
                                        <= OVERLAP_RANGE)) return;
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

    /** Publish the same active-world selection to every client. */
    public void notifyIcon() {
        scene.getPlayers().forEach(this::notifyIcon);
    }

    public void notifyIcon(Player player) {
        player.sendPacket(new PacketBlossomBriefInfoNotify(buildBriefInfos()));
    }

    private List<BlossomBriefInfoOuterClass.BlossomBriefInfo> buildBriefInfos() {
        final int wl = getWorldLevel();
        final int worldLevel = Math.clamp(wl, 0, 9);
        final var worldLevelData = GameData.getWorldLevelDataMap().get(worldLevel);
        final int monsterLevel = (worldLevelData != null) ? worldLevelData.getMonsterLevel() : 1;
        var result = new ArrayList<BlossomBriefInfoOuterClass.BlossomBriefInfo>();
        for (var active : selectedOutcrops()) {
            var spawn = active.spawn();
            var drops = active.refresh().getDropVec();
            int previewIndex = Math.min(worldLevel, 8);
            if (drops == null || drops.length <= previewIndex || drops[previewIndex] == null
                    || drops[previewIndex].getPreviewReward() <= 0) continue;
            result.add(BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                    .setSceneId(scene.getId())
                    .setPos(spawn.getPos().toProto())
                    .setResin(20)
                    .setMonsterLevel(monsterLevel)
                    .setRewardId(drops[previewIndex].getPreviewReward())
                    .setCircleCampId(active.type().getCircleCampId())
                    .setRefreshId(active.type().getBlossomChestId())
                    .build());
        }
        return result;
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
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
                var activity = it.next();
                if (activity.getChest() != chest) continue;
                it.remove();
                var sourceGadget = activity.getGadget();
                createdEntity.remove(sourceGadget);
                // Manual /spawn flowers do not belong to the daily regional rotation.
                var selected = selectedOutcrops().stream()
                        .filter(row -> row.type() == BlossomType.valueOf(sourceGadget.getGadgetId()))
                        .filter(row -> row.spawn().getPos().computeDistance(sourceGadget.getPosition())
                                <= OVERLAP_RANGE)
                        .findFirst().orElse(null);
                if (selected != null) {
                    var host = scene.getWorld().getHost();
                    host.advanceBlossomRotation(
                            currentRefreshDay(), selected.cityId(), selected.type().refreshType());
                    // A settled claim advances only the current city/type slot.
                    selectionRevision++;
                    host.save();
                }
                notifyIcon();
                return;
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
