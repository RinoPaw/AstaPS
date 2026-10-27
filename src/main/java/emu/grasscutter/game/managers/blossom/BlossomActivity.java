package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.scripts.data.*;
import java.util.*;

public final class BlossomActivity {

    private final EntityGadget gadget;
    private final BlossomNativeCamps.CampNode source;
    private final BlossomNativeCamps.Blossom blossom;
    private int nativeMonsterGoal;
    private int nativeMonsterProgress;
    private boolean nativeProgressReported;
    private EntityGadget chest;
    private SceneGadget nativeRewardGadget;
    private final Set<Integer> rewardClaimants = new HashSet<>();

    boolean rewardClaimed(int uid) { return rewardClaimants.contains(uid); }
    void confirmReward(int uid) { rewardClaimants.add(uid); }

    // Keep every encounter root, including monsters already dead or pruned between waves.
    private final Set<Integer> encounterMonsterIds = new HashSet<>();
    // Death removes summoned entities from Scene before the claimable flower appears.
    // Retain their identities so the client also receives a final VISION_REMOVE.
    private final Map<Integer, GameEntity> encounterSummons = new LinkedHashMap<>();

    public BlossomActivity(EntityGadget gadget, BlossomNativeCamps.CampNode source) {
        this.gadget = gadget;
        this.source = source;
        this.blossom = source.blossom;
    }

    BlossomNativeCamps.CampNode source() { return source; }
    BlossomNativeCamps.Blossom blossom() { return blossom; }
    private BlossomNativeCamps.Blossom.State state() { return blossom.state; }

    /** Called immediately when Scene registers an entity, before its onCreate abilities run. */
    void onSceneEntityCreated(GameEntity entity) {
        if (state() == BlossomNativeCamps.Blossom.State.FIGHTING && nativeMonsterGoal > 0 && entity instanceof EntityMonster
                && entity.getGroupId() == gadget.getGroupId()) {
            encounterMonsterIds.add(entity.getId());
        }
        int parent = BlossomSummonCleanup.parentId(entity);
        if (BlossomSummonCleanup.isEncounterChild(
                parent, encounterMonsterIds, encounterSummons.keySet())) {
            encounterSummons.putIfAbsent(entity.getId(), entity);
        }
    }

    boolean rewardReady() {
        return state() == BlossomNativeCamps.Blossom.State.REWARD && chest == null;
    }
    boolean isClaimableChest(EntityGadget entity) {
        return state() == BlossomNativeCamps.Blossom.State.REWARD && chest == entity;
    }
    /** Lua's schedule stage is a projection of the flower lifecycle. */
    int scheduleState() {
        return switch (state()) {
            case IDLE -> 1;
            case FIGHTING -> 2;
            case REWARD -> 3;
        };
    }

    /** Use the native Lua group monster definitions as its scripted progress target. */
    void beginNative(int monsterGoal) {
        blossom.state = BlossomNativeCamps.Blossom.State.FIGHTING;
        nativeMonsterGoal = monsterGoal;
        nativeMonsterProgress = 0;
        nativeProgressReported = false;
    }

    /** Native Lua reports each group monster death; complete the progress event once. */
    boolean addNativeProgress() {
        if (nativeMonsterGoal <= 0 || nativeProgressReported || state() != BlossomNativeCamps.Blossom.State.FIGHTING) return false;
        if (++nativeMonsterProgress < nativeMonsterGoal) return false;
        nativeProgressReported = true;
        return true;
    }

    void abort() {
        blossom.state = BlossomNativeCamps.Blossom.State.IDLE;
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

    /** Restore a previously earned reward from its original Lua marker. */
    boolean restoreFromLua(SceneGadget rewardDefinition) {
        if (state() != BlossomNativeCamps.Blossom.State.REWARD
                || rewardDefinition == null || !rewardDefinition.is_blossom_chest) return false;
        nativeRewardGadget = rewardDefinition;
        return true;
    }

    /** Mark completion once, after the Lua script has reached its own success condition. */
    boolean completeFromLua(SceneGadget rewardDefinition) {
        if (state() != BlossomNativeCamps.Blossom.State.FIGHTING || rewardDefinition == null
                || !rewardDefinition.is_blossom_chest) return false;
        nativeRewardGadget = rewardDefinition;
        blossom.state = BlossomNativeCamps.Blossom.State.REWARD;
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

    /** Build the claimable flower using the Lua reward marker and configured chest type. */
    public EntityGadget getChest() {
        if (chest == null) {
            if (nativeRewardGadget == null || state() != BlossomNativeCamps.Blossom.State.REWARD) {
                throw new IllegalStateException("Native Lua has not declared a blossom reward");
            }
            EntityGadget rewardGadget = new EntityGadget(
                    gadget.getScene(),
                    BlossomType.valueOf(gadget.getGadgetId()).getRewardChestGadgetId(),
                    nativeRewardGadget.pos);
            rewardGadget.setGroupId(gadget.getGroupId());
            rewardGadget.setConfigId(nativeRewardGadget.config_id);
            rewardGadget.getRotation().set(nativeRewardGadget.rot);
            SceneGadget metaGadget = new SceneGadget();
            metaGadget.config_id = nativeRewardGadget.config_id;
            metaGadget.pos = nativeRewardGadget.pos;
            metaGadget.rot = nativeRewardGadget.rot;
            metaGadget.group = nativeRewardGadget.group;
            metaGadget.is_blossom_chest = true;
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
