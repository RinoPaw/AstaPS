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
    private final Scene scene;
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final List<BlossomActivity> activeChests = new ArrayList<>();
    private final List<EntityGadget> createdEntity = new ArrayList<>();

    private List<SpawnDataEntry> scriptedSpawnCandidates;
    private final Set<SpawnDataEntry> scriptedSpawned = new HashSet<>();
    private static final double SCRIPTED_SPAWN_RANGE = 400;
    private static final float OVERLAP_RANGE = 2.5f;
    private List<BlossomActiveSelection.Active> selectedOutcrops = List.of();
    private long selectedDay = Long.MIN_VALUE;
    private int selectedRank = -1;
    private int selectedAreasHash;
    private boolean selectionDirty = true;

    private List<BlossomActiveSelection.Active> activeOutcrops() {
        if (scriptedSpawnCandidates == null) {
            scriptedSpawnCandidates = scriptedCandidates(GameDepot.getSpawnLists(), scene.getId());
        }
        Player host = scene.getWorld().getHost();
        if (host == null) return List.of();
        long day = BlossomRotationState.currentRefreshDay();
        int rank = host.getLevel();
        int areaHash = host.getUnlockedSceneAreas(scene.getId()).hashCode();
        if (selectionDirty || selectedDay != day || selectedRank != rank
                || selectedAreasHash != areaHash) {
            boolean newDay = selectedDay != day;
            var next = BlossomActiveSelection.select(
                    rank, host.getUid(), day, scriptedSpawnCandidates,
                    GameData.getBlossomGroupsExcelConfigDataMap().values(),
                    GameData.getBlossomSectionOrderExcelConfigDataMap().values(),
                    GameData.getBlossomRefreshExcelConfigDataMap().values(),
                    host.getBlossomRotation(),
                    config -> refreshConditionMet(config, host));
            boolean changed = !next.equals(selectedOutcrops);
            selectedOutcrops = next;
            selectedDay = day;
            selectedRank = rank;
            selectedAreasHash = areaHash;
            selectionDirty = false;
            if (newDay) scriptedSpawned.clear();
            if (changed) {
                Grasscutter.getLogger().info(
                        "Active ley-line outcrops scene={} AR={} selections={}",
                        scene.getId(), rank,
                        next.stream().map(a -> a.cityId() + ":" + a.type() + ":" + a.campId())
                                .toList());
            }
        }
        return selectedOutcrops;
    }

    private boolean refreshConditionMet(
            emu.grasscutter.data.excels.BlossomRefreshExcelConfigData config, Player host) {
        var conditions = config.getRefreshCondVec();
        if (conditions == null) return false;
        for (var condition : conditions) {
            if (condition.getType() == null || condition.getType().isBlank()) continue;
            var params = condition.getParam();
            if (params == null || params.isEmpty()) return false;
            switch (condition.getType()) {
                case "BLOSSOM_REFRESH_COND_PLAYER_LEVEL_EQUAL_GREATER" -> {
                    if (host.getLevel() < params.get(0)) return false;
                }
                case "BLOSSOM_REFRESH_COND_UNLOCK_ANY_AREA_IN_CITY" -> {
                    var city = GameData.getCityDataMap().get(params.get(0).intValue());
                    if (city == null || city.getAreaIdVec() == null
                            || city.getAreaIdVec().stream().noneMatch(
                                    host.getUnlockedSceneAreas(scene.getId())::contains)) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    /** Native group ownership and the selected camp determine the only available interactables. */
    public void ensureScriptedSpawns() {
        if (scene.getDungeonManager() != null || scene.getPlayers().isEmpty()) return;
        var previous = selectedOutcrops;
        var active = activeOutcrops();
        if (!previous.equals(active)) notifyIcon();
        var loaded = scene.getEntities().values().stream()
                .filter(entity -> entity instanceof EntityGadget)
                .map(entity -> (EntityGadget) entity)
                .filter(entity -> entity.getGroupId() != 0)
                .filter(entity -> BlossomType.valueOf(entity.getGadgetId()) != null)
                .toList();
        for (var gadget : loaded) {
            boolean selected = active.stream().anyMatch(outcrop ->
                    outcrop.spawn().getGroup().getGroupId() == gadget.getGroupId()
                    && outcrop.spawn().getConfigId() == gadget.getConfigId()
                    && outcrop.type().getGadgetId() == gadget.getGadgetId());
            if (!selected) {
                scene.removeEntity(gadget,
                        VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                createdEntity.remove(gadget);
            }
        }
        for (var outcrop : active) {
            var spawn = outcrop.spawn();
            if (scriptedSpawned.contains(spawn)) {
                boolean present = scene.getEntities().values().stream().anyMatch(e ->
                        e instanceof EntityGadget gadget && gadget.getSpawnEntry() == spawn
                                && gadget.getGadgetId() == outcrop.type().getGadgetId());
                boolean fighting = blossomActivities.stream().anyMatch(
                        activity -> activity.getGadget().getSpawnEntry() == spawn);
                boolean claiming = activeChests.stream().anyMatch(
                        activity -> activity.getGadget().getSpawnEntry() == spawn);
                if (present || fighting || claiming) continue;
                scriptedSpawned.remove(spawn);
            }
            boolean nearby = scene.getPlayers().stream().anyMatch(player ->
                    player.getPosition().computeDistance(spawn.getPos())
                            <= SCRIPTED_SPAWN_RANGE);
            if (!nearby) continue;

            var existing = scene.getEntities().values().stream()
                    .filter(entity -> entity instanceof EntityGadget)
                    .map(entity -> (EntityGadget) entity)
                    .filter(entity -> entity.getGroupId() == spawn.getGroup().getGroupId()
                            && entity.getConfigId() == spawn.getConfigId()
                            && entity.getGadgetId() == outcrop.type().getGadgetId())
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.setSpawnEntry(spawn);
                initBlossom(existing);
            } else {
                var gadget = new EntityGadget(
                        scene, outcrop.type().getGadgetId(), spawn.getPos(), spawn.getRot());
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

    /** Imported positional candidates are joined to verified group IDs by the selection planner. */
    static List<SpawnDataEntry> scriptedCandidates(
            Map<SpawnDataEntry.GridBlockId, ? extends List<SpawnDataEntry>> spawnLists,
            int sceneId) {
        var source = spawnLists.entrySet().stream()
                .filter(entry -> entry.getKey().getSceneId() == sceneId)
                .flatMap(entry -> entry.getValue().stream())
                .filter(entry -> BlossomType.valueOf(entry.getGadgetId()) != null)
                .distinct()
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
        if (player == null) return;
        player.sendPacket(new PacketBlossomBriefInfoNotify(buildBriefInfos(player)));
    }

    private List<BlossomBriefInfoOuterClass.BlossomBriefInfo> buildBriefInfos(Player player) {
        final int wl = Math.min(9, Math.max(0, getWorldLevel()));
        final var worldLevelData = GameData.getWorldLevelDataMap().get(wl);
        final int monsterLevel = worldLevelData == null ? 1 : worldLevelData.getMonsterLevel();
        var infos = new ArrayList<BlossomBriefInfoOuterClass.BlossomBriefInfo>();
        for (var active : activeOutcrops()) {
            var refresh = GameData.getBlossomRefreshExcelConfigDataMap().get(active.refreshId());
            if (refresh == null) continue;
            var tiers = refresh.getDropVec();
            int index = Math.min(8, wl);
            if (tiers == null || tiers.length <= index || tiers[index] == null
                    || tiers[index].getPreviewReward() <= 0) continue;
            infos.add(BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                    .setSceneId(scene.getId())
                    .setPos(active.spawn().getPos().toProto())
                    .setResin(20)
                    .setMonsterLevel(monsterLevel)
                    .setRewardId(tiers[index].getPreviewReward())
                    .setCircleCampId(active.campId())
                    .setRefreshId(active.refreshId())
                    .build());
        }
        return infos;
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
                var active = it.next();
                if (active.getChest() == chest) {
                    it.remove();
                    // Rotate only after a confirmed reward claim, never on fight completion.
                    var source = active.getGadget().getSpawnEntry();
                    if (source != null) {
                        for (var selected : activeOutcrops()) {
                            if (selected.spawn() != source) continue;
                            BlossomActiveSelection.advance(selected,
                                    scene.getWorld().getHost().getBlossomRotation(),
                                    GameData.getBlossomGroupsExcelConfigDataMap().values());
                            scriptedSpawned.remove(source);
                            selectionDirty = true;
                            break;
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
