package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.scripts.data.*;
import java.util.*;

public final class BlossomActivity {

    private final EntityGadget gadget;
    private int nativeMonsterGoal;
    private int nativeMonsterProgress;
    private boolean nativeProgressReported;
    private EntityGadget chest;
    private boolean pass = false;
    private final Set<Integer> rewardClaimants = new HashSet<>();
    private final Map<Integer, String> rewardOperations = new HashMap<>();

    boolean rewardClaimed(int uid) { return rewardClaimants.contains(uid); }
    void confirmReward(int uid) { rewardClaimants.add(uid); }
    String rewardOperation(int uid) {
        return rewardOperations.computeIfAbsent(uid, ignored -> UUID.randomUUID().toString());
    }
    void resetUnpaidReward(int uid) { rewardOperations.remove(uid); }

    // Keep every encounter root, including monsters already dead or pruned between waves.
    private final Set<Integer> encounterMonsterIds = new HashSet<>();
    // Death removes summoned entities from Scene before the claimable flower appears.
    // Retain their identities so the client also receives a final VISION_REMOVE.
    private final Map<Integer, GameEntity> encounterSummons = new LinkedHashMap<>();

    public BlossomActivity(EntityGadget gadget) {
        this.gadget = gadget;
    }

    /** Called immediately when Scene registers an entity, before its onCreate abilities run. */
    void onSceneEntityCreated(GameEntity entity) {
        if (nativeMonsterGoal > 0 && entity instanceof EntityMonster
                && entity.getGroupId() == gadget.getGroupId()) {
            encounterMonsterIds.add(entity.getId());
        }
        int parent = BlossomSummonCleanup.parentId(entity);
        if (BlossomSummonCleanup.isEncounterChild(
                parent, encounterMonsterIds, encounterSummons.keySet())) {
            encounterSummons.putIfAbsent(entity.getId(), entity);
        }
    }

    public boolean getPass() {
        return pass;
    }

    /** Use the native Lua group monster definitions as its scripted progress target. */
    void beginNative(int monsterGoal) {
        nativeMonsterGoal = monsterGoal;
        nativeMonsterProgress = 0;
        nativeProgressReported = false;
    }

    /** Native Lua reports each group monster death; complete the progress event once. */
    boolean addNativeProgress() {
        if (nativeMonsterGoal <= 0 || nativeProgressReported || pass) return false;
        if (++nativeMonsterProgress < nativeMonsterGoal) return false;
        nativeProgressReported = true;
        return true;
    }

    void abort() {
        clearEncounterSummons();
        var scene = gadget.getScene();
        var toRemove = scene.getEntities().values().stream()
                .filter(entity -> entity instanceof EntityMonster)
                .filter(entity -> entity.getGroupId() == gadget.getGroupId())
                .filter(entity -> encounterMonsterIds.contains(entity.getId()))
                .toList();
        if (!toRemove.isEmpty()) {
            scene.removeEntities(toRemove,
                    emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
        }
        encounterMonsterIds.clear();
    }

    int nativeKills() { return nativeMonsterProgress; }
    int nativeGoal() { return nativeMonsterGoal; }

    /** Mark completion once, after the Lua script has reached its own success condition. */
    boolean completeFromLua() {
        if (pass) return false;
        pass = true;
        return true;
    }

    /**
     * Remember both living summons and summons that the server already removed via a death
     * notification. Their owners can also have disappeared in an earlier wave.
     */
    private void observeSummons() {
        var owners = new HashSet<>(encounterMonsterIds);
        owners.addAll(encounterSummons.keySet());
        for (var spawned : BlossomSummonCleanup.find(gadget.getScene(), owners)) {
            encounterSummons.putIfAbsent(spawned.getId(), spawned);
        }
    }

    /** Finish the complete summon lifecycle before publishing the reward flower. */
    void clearEncounterSummons() {
        observeSummons();
        var result = BlossomSummonCleanup.remove(gadget.getScene(), encounterSummons.values());
        if (result.live() != 0 || result.stale() != 0) {
            emu.grasscutter.Grasscutter.getLogger().info(
                    "Ley-line encounter summons cleared source={} live={} stale={}",
                    gadget.getId(), result.live(), result.stale());
        }
        encounterSummons.clear();
    }

    public EntityGadget getGadget() {
        return gadget;
    }

    public EntityGadget getChest() {
        if (chest == null) {
            EntityGadget rewardGadget =
                    new EntityGadget(gadget.getScene(),
                            BlossomType.valueOf(gadget.getGadgetId()).getRewardChestGadgetId(),
                            gadget.getPosition());
            SceneGadget metaGadget = new SceneGadget();
            metaGadget.boss_chest = new SceneBossChest();
            metaGadget.boss_chest.resin = 20;
            rewardGadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, Float.POSITIVE_INFINITY);
            rewardGadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.POSITIVE_INFINITY);
            rewardGadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, Float.POSITIVE_INFINITY);
            rewardGadget.setMetaGadget(metaGadget);
            rewardGadget.buildContent();
            chest = rewardGadget;
        }
        return chest;
    }
}
