package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.utils.Utils;
import java.util.*;

public final class BlossomActivity {

    private final EntityGadget gadget;
    private WorldChallenge challenge;
    private boolean combatCompleted;
    private boolean combatCompletionReported;
    private int nativeMonsterGoal;
    private int nativeMonsterProgress;
    private boolean nativeProgressReported;
    private EntityGadget chest;
    private int step;
    private int goal;
    private int generatedCount;
    private int worldLevel;
    private boolean pass = false;
    private final Set<Integer> rewardClaimants = new HashSet<>();
    private final Map<Integer, String> rewardOperations = new HashMap<>();

    boolean rewardClaimed(int uid) { return rewardClaimants.contains(uid); }
    void confirmReward(int uid) { rewardClaimants.add(uid); }
    String rewardOperation(int uid) {
        return rewardOperations.computeIfAbsent(uid, ignored -> UUID.randomUUID().toString());
    }
    void resetUnpaidReward(int uid) { rewardOperations.remove(uid); }

    private final List<EntityMonster> activeMonsters = new ArrayList<>();
    // Keep every encounter root, including monsters already dead or pruned between waves.
    private final Set<Integer> encounterMonsterIds = new HashSet<>();
    // Death removes summoned entities from Scene before the claimable flower appears.
    // Retain their identities so the client also receives a final VISION_REMOVE.
    private final Map<Integer, GameEntity> encounterSummons = new LinkedHashMap<>();
    private final Queue<Integer> candidateMonsters = new ArrayDeque<>();

    public BlossomActivity(EntityGadget gadget) {
        this.gadget = gadget;
    }

    /** Lua starts the ordinary combat mode through ScriptLib. All encounters share this instance. */
    boolean startCombat(List<Integer> monsters, int worldLevel) {
        if (challenge != null || pass || monsters.isEmpty()) return false;
        var group = new SceneGroup();
        group.id = gadget.getId();
        this.worldLevel = worldLevel;
        this.goal = monsters.size();
        this.generatedCount = 0;
        this.candidateMonsters.addAll(monsters);
        var triggers = new ArrayList<ChallengeTrigger>();
        challenge = new WorldChallenge(gadget.getScene(), group, 1, 1,
                List.of(goal, -1), -1, goal, triggers);
        triggers.add(new KillMonsterCountTrigger());
        gadget.getScene().setChallenge(challenge);
        challenge.start();
        return true;
    }

    public WorldChallenge getChallenge() {
        return this.challenge;
    }

    public void addMonsters(List<EntityMonster> monsters) {
        // Earlier waves can still have living enemies when the next wave appears.
        this.activeMonsters.removeIf(monster -> !monster.isAlive());
        this.activeMonsters.addAll(monsters);
        for (EntityMonster monster : monsters) {
            monster.setGroupId(this.gadget.getId());
            encounterMonsterIds.add(monster.getId());
        }
        observeSummons();
    }

    /** Called immediately when Scene registers an entity, before its onCreate abilities run. */
    void onSceneEntityCreated(GameEntity entity) {
        int parent = BlossomSummonCleanup.parentId(entity);
        if (BlossomSummonCleanup.isEncounterChild(
                parent, encounterMonsterIds, encounterSummons.keySet())) {
            encounterSummons.putIfAbsent(entity.getId(), entity);
        }
    }

    public int getAliveMonstersCount() {
        int count = 0;
        for (EntityMonster monster : activeMonsters) {
            if (monster.isAlive()
                    && monster.getScene().getEntityById(monster.getId()) == monster) {
                count++;
            }
        }
        return count;
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

    /** Undo a partially started script before the worktop can be selected again. */
    void abort() {
        var scene = gadget.getScene();
        if (challenge != null) {
            challenge.fail();
            if (scene.getChallenge() == challenge) scene.setChallenge(null);
        }
        clearEncounterSummons();
        var remaining = activeMonsters.stream()
                .filter(monster -> scene.getEntityById(monster.getId()) == monster)
                .map(monster -> (GameEntity) monster).toList();
        if (!remaining.isEmpty()) scene.removeEntities(remaining,
                emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
        activeMonsters.clear();
        candidateMonsters.clear();
    }

    /** Mark completion once, after the Lua script has reached its own success condition. */
    boolean completeFromLua() {
        if (pass) return false;
        pass = true;
        return true;
    }

    /** The combat primitive reports its result once; Lua decides what happens next. */
    boolean takeCombatCompletion() {
        if (!combatCompleted || combatCompletionReported) return false;
        combatCompletionReported = true;
        return true;
    }

    /** A completed kill challenge is authoritative even if a monster reference remains alive. */
    static boolean encounterComplete(int goal, int generated, int alive, int kills) {
        return kills >= goal || (generated >= goal && alive == 0);
    }

    public void onTick() {
        if (pass || challenge == null || combatCompleted) return;
        observeSummons();
        if (challenge.isSuccess()) {
            combatCompleted = true;
            return;
        }
        if (!challenge.inProgress()) return;

        Scene scene = gadget.getScene();
        Position pos = gadget.getPosition();
        int alive = getAliveMonstersCount();
        int kills = challenge.getScore().get();
        if (encounterComplete(goal, generatedCount, alive, kills)) {
            challenge.done();
            combatCompleted = true;
            return;
        }
        if (alive <= 2) {
            if (generatedCount < goal) {
                step++;

                var worldLevelData = GameData.getWorldLevelDataMap().get(worldLevel);
                int worldLevelOverride = 0;
                if (worldLevelData != null) {
                    worldLevelOverride = worldLevelData.getMonsterLevel();
                }

                List<EntityMonster> newMonsters = new ArrayList<>();
                int willSpawn = Utils.randomRange(3, 5);
                if (generatedCount + willSpawn > goal) {
                    willSpawn = goal - generatedCount;
                }
                generatedCount += willSpawn;
                for (int i = 0; i < willSpawn; i++) {
                    var entry = candidateMonsters.poll();
                    if (entry == null) continue;

                    var monsterData = GameData.getMonsterDataMap().get((int) entry);
                    var level = scene.getEntityLevel(1, worldLevelOverride);
                    var entity =
                            new EntityMonster(scene, monsterData, pos.nearby2d(4f), Position.ZERO, level);
                    newMonsters.add(entity);
                }
                // Register roots before Scene.addEntity invokes onCreate/ability callbacks.
                addMonsters(newMonsters);
                for (EntityMonster monster : newMonsters) scene.addEntity(monster);
            }
        }
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
