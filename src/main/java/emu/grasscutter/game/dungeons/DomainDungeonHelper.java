/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneConfig;
import emu.grasscutter.scripts.data.SceneGroup;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DomainDungeonHelper {
    private static final int DOMAIN_WEATHER_ID = 1;
    private static final float DEFAULT_ENTRY_RADIUS = 45.0f;
    private static final long ENTER_DEBOUNCE_MS = 1500L;
    private static final Int2IntMap CLIENT_SCENE_CACHE =
            Int2IntMaps.synchronize(new Int2IntOpenHashMap());
    private static final ConcurrentHashMap<Integer, Long> LAST_ENTER_MS = new ConcurrentHashMap<>();

    private DomainDungeonHelper() {}

    public static int resolveClientSceneId(int n) {
        int n2 = CLIENT_SCENE_CACHE.get(n);
        if (n2 != Integer.MIN_VALUE) {
            return n2;
        }
        int n3 = n;
        if (n >= 40816 && n <= 40819) {
            n3 = 40754 + (n - 40816);
        } else if (n >= 40704 && n <= 40707) {
            n3 = 40754 + (n - 40704);
        } else if (n >= 40770 && n <= 40773) {
            n3 = n;
        } else if (n >= 40780 && n <= 40791) {
            n3 = n;
        } else if (n >= 40792 && n <= 40799) {
            n3 = n;
        } else if (n >= 40810 && n <= 40813) {
            n3 = 40506;
        } else if (n >= 40820 && n <= 40823) {
            // Moonchild / NDKL Cycle3: the client has no 40820-40823 art, so reuse the Lost Lunar Court scene
            n3 = 40754 + (n - 40820);
        } else if (n >= 40824 && n <= 40827) {
            // MDDungeon Cycle05: no client art, so reuse the Lost Lunar Court scene
            n3 = 40754 + (n - 40824);
        } else if (n >= 40828 && n <= 40831) {
            // Ice Erosion: the client has no 40828-40831 art, so reuse the Lost Lunar Court scene
            n3 = 40754 + (n - 40828);
        } else if (n >= 40832 && n <= 40835) {
            // Snezhnaya weapon: replicate the Lost Lunar Court (40754-40757) client scene art
            n3 = 40754 + (n - 40832);
        } else if (n >= 40836 && n <= 40839) {
            // Snezhnaya talent: replicate the Fontaine Pale Forgotten Glory / Lightless Depths scenes (40760-40763)
            n3 = 40760 + (n - 40836);
        } else if (n >= 40700 && n <= 40703) {
            n3 = n;
        } else if (n >= 40840 && n <= 40847) {
            // Extra custom domains without client art - reuse the Lost Lunar Court scene
            n3 = 40754 + ((n - 40840) % 4);
        } else if (n >= 40760 && n <= 40767) {
            n3 = n;
        }
        CLIENT_SCENE_CACHE.put(n, n3);
        return n3;
    }

    /** Scene id advertised to the client for art/team bind (may differ from server scene id). */
    public static int notifySceneId(int serverSceneId) {
        return resolveClientSceneId(serverSceneId);
    }

    public static int notifySceneId(Player player) {
        if (player == null) {
            return 0;
        }
        return notifySceneId(player.getSceneId());
    }

    public static TeleportProperties clientNotifyProperties(TeleportProperties teleportProperties) {
        if (teleportProperties == null) {
            return null;
        }
        int clientSceneId = resolveClientSceneId(teleportProperties.getSceneId());
        if (clientSceneId == teleportProperties.getSceneId()) {
            return teleportProperties;
        }
        return TeleportProperties.builder()
                .sceneId(clientSceneId)
                .dungeonId(teleportProperties.getDungeonId())
                .teleportType(teleportProperties.getTeleportType())
                .enterReason(teleportProperties.getEnterReason())
                .teleportTo(teleportProperties.getTeleportTo())
                .teleportRot(teleportProperties.getTeleportRot())
                .enterType(teleportProperties.getEnterType())
                .build();
    }

    public static void prepareOpenWorldExit(Player player, int sceneId) {
        if (player == null || sceneId <= 0) {
            return;
        }
        Scene scene = player.getScene();
        if (scene != null && isDomainScene(scene) && player.getSceneId() != sceneId) {
            Grasscutter.getLogger()
                    .debug(
                            "Domain exit scene sync uid={} from {} to {}",
                            player.getUid(),
                            player.getSceneId(),
                            sceneId);
            player.setSceneId(sceneId);
        }
    }

    public static void onDungeonRestart(Player player, Scene scene) {
        if (player == null || scene == null) {
            return;
        }
        LAST_ENTER_MS.remove(player.getUid());
        scene.setChallenge(null);
        scene.setKilledMonsterCount(0);
        applyDomainWeather(player);
        syncPlayerClientSceneId(player);
        DomainSceneResetHelper.clearGridCache(scene);
        DomainSceneResetHelper.refreshToInitSuites(scene);
        applyDomainSpawn(player);
        DomainChallengeKeyHelper.revealChallengeKeys(scene);
    }

    public static void syncPlayerClientSceneId(Player player) {
        if (player == null) {
            return;
        }
        Scene scene = player.getScene();
        if (scene == null || !isDomainScene(scene)) {
            return;
        }
        int serverSceneId = scene.getId();
        int clientSceneId = resolveClientSceneId(serverSceneId);
        if (clientSceneId != serverSceneId) {
            Grasscutter.getLogger()
                    .info(
                            "Domain client art remap uid={} server={} clientArt={} (player.sceneId kept)",
                            player.getUid(),
                            serverSceneId,
                            clientSceneId);
        }
    }

    public static boolean usesRemappedClientArt(Scene scene) {
        if (scene == null) {
            return false;
        }
        int sceneId = scene.getId();
        return resolveClientSceneId(sceneId) != sceneId;
    }

    public static boolean isDomainScene(Scene scene) {
        if (scene == null) {
            return false;
        }
        DungeonManager dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.getDungeonData() != null) {
            DungeonSubType subType = dungeonManager.getDungeonData().getSubType();
            if (subType == DungeonSubType.DUNGEON_SUB_TALENT
                    || subType == DungeonSubType.DUNGEON_SUB_WEAPON
                    || subType == DungeonSubType.DUNGEON_SUB_RELIQUARY) {
                return true;
            }
        }
        int sceneId = scene.getId();
        if (sceneId >= 40100 && sceneId <= 40699) {
            return true;
        }
        if (sceneId >= 40200 && sceneId <= 40299) {
            return true;
        }
        if (sceneId >= 40300 && sceneId <= 40499) {
            return true;
        }
        return sceneId >= 40700 && sceneId <= 40999;
    }

    public static int resolveTalentPack(int dungeonId) {
        if (dungeonId >= 4430 && dungeonId <= 4433
                || dungeonId >= 4440 && dungeonId <= 4443
                || dungeonId >= 4450 && dungeonId <= 4453) {
            return 1;
        }
        if (dungeonId >= 4434 && dungeonId <= 4437
                || dungeonId >= 4444 && dungeonId <= 4447
                || dungeonId >= 4454 && dungeonId <= 4457) {
            return 2;
        }
        if (dungeonId >= 4651 && dungeonId <= 4662) {
            return 3;
        }
        // SN talent IV: suite4 on the shared Fontaine scene 40763
        if (dungeonId == 4694 || dungeonId == 4698 || dungeonId == 4702) {
            return 4;
        }
        return 0;
    }

    public static void applyTalentPackVariable(Scene scene) {
        if (scene == null) {
            return;
        }
        int sceneId = scene.getId();
        // Talent shared scenes: NK 40750-40753, Fontaine 40760-40763 (also used by SN talent)
        if (!((sceneId >= 40750 && sceneId <= 40753)
                || (sceneId >= 40760 && sceneId <= 40763)
                || (sceneId >= 40836 && sceneId <= 40839))) {
            return;
        }
        DungeonManager dungeonManager = scene.getDungeonManager();
        if (dungeonManager == null || dungeonManager.getDungeonData() == null) {
            return;
        }
        int dungeonId = dungeonManager.getDungeonData().getId();
        int talentPack = resolveTalentPack(dungeonId);
        if (talentPack <= 0) {
            return;
        }
        int groupId = Integer.parseInt("240" + (sceneId - 40000) + "001");
        SceneScriptManager scriptManager = scene.getScriptManager();
        if (scriptManager == null) {
            return;
        }
        Map<String, Integer> variables = scriptManager.getVariables(groupId);
        if (variables == null) {
            return;
        }
        variables.put("talent_pack", talentPack);
        Grasscutter.getLogger()
                .info(
                        "talent_pack set uid-scene dungeon={} scene={} pack={}",
                        dungeonId,
                        sceneId,
                        talentPack);
    }

    public static void onPlayerEnterDomain(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastEnter = LAST_ENTER_MS.get(player.getUid());
        if (lastEnter != null && now - lastEnter < ENTER_DEBOUNCE_MS) {
            return;
        }
        LAST_ENTER_MS.put(player.getUid(), now);
        Scene scene = player.getScene();
        if (scene == null || !isDomainScene(scene)) {
            return;
        }
        DomainSceneResetHelper.clearGridCache(scene);
        applyDomainWeather(player);
        syncPlayerClientSceneId(player);
        SceneScriptManager scriptManager = scene.getScriptManager();
        if (scriptManager != null) {
            SceneScriptManager.eventExecutor.execute(() -> finishDomainEnterDeferred(player, scene));
        }
    }

    private static void finishDomainEnterDeferred(Player player, Scene scene) {
        if (player == null || scene == null || !isDomainScene(scene)) {
            return;
        }
        try {
            ensureGroupsLoaded(scene);
            DomainSceneResetHelper.refreshToInitSuites(scene);
            applyTalentPackVariable(scene);
            applyDomainSpawn(player);
            DomainChallengeKeyHelper.revealChallengeKeys(scene);
            Grasscutter.getLogger()
                    .info(
                            "Domain enter done uid={} serverScene={} clientScene={}",
                            player.getUid(),
                            scene.getId(),
                            resolveClientSceneId(scene.getId()));
        } catch (Throwable throwable) {
            Grasscutter.getLogger()
                    .warn(
                            "Domain deferred enter failed scene={}: {}",
                            scene.getId(),
                            throwable.toString());
        }
    }

    public static void ensureGroupsLoaded(Scene scene) {
        if (scene == null || !isDomainScene(scene)) {
            return;
        }
        SceneScriptManager scriptManager = scene.getScriptManager();
        if (scriptManager == null) {
            return;
        }
        if (scriptManager.isInit() && !scene.getLoadedGroups().isEmpty()) {
            return;
        }
        if (!scriptManager.isInit()) {
            scheduleEnsureGroupsLoaded(scene);
            return;
        }
        if (!scene.getLoadedGroups().isEmpty()) {
            return;
        }
        ArrayList<SceneGroup> groups = new ArrayList<>();
        for (SceneBlock block : scriptManager.getBlocks().values()) {
            scene.loadBlock(block);
            if (block.groups == null) {
                continue;
            }
            for (SceneGroup group : block.groups.values()) {
                if (group == null || group.dynamic_load) {
                    continue;
                }
                groups.add(group);
            }
        }
        if (!groups.isEmpty()) {
            scene.onLoadGroup(groups);
            scene.onRegisterGroups();
            Grasscutter.getLogger()
                    .debug("Preloaded {} domain groups for scene {}", groups.size(), scene.getId());
        }
    }

    private static void scheduleEnsureGroupsLoaded(Scene scene) {
        if (scene == null) {
            return;
        }
        SceneScriptManager.eventExecutor.execute(
                () -> {
                    try {
                        Thread.sleep(30L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    ensureGroupsLoaded(scene);
                });
    }

    public static void applyDomainWeather(Player player) {
        if (player == null) {
            return;
        }
        player.setWeather(DOMAIN_WEATHER_ID, ClimateType.CLIMATE_SUNNY);
    }

    public static void applyDomainSpawn(Player player) {
        if (player == null) {
            return;
        }
        Scene scene = player.getScene();
        if (!isDomainScene(scene)) {
            return;
        }
        if (DomainContinueSpawnHelper.applyNearChallengeKey(player)) {
            return;
        }
        Position position = null;
        Position rotation = null;
        DungeonManager dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.getDungeonData() != null) {
            position = dungeonManager.getDungeonData().getStartPosition();
            rotation = dungeonManager.getDungeonData().getStartRotation();
        }
        SceneScriptManager scriptManager = scene.getScriptManager();
        if (position == null && scriptManager != null && scriptManager.getConfig() != null) {
            SceneConfig config = scriptManager.getConfig();
            position = config.born_pos;
            rotation = config.born_rot;
        }
        if (position == null) {
            return;
        }
        player.getPosition().set(position);
        if (rotation != null) {
            player.getRotation().set(rotation);
        }
        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        if (avatarEntity != null) {
            avatarEntity.move(position, rotation != null ? rotation : player.getRotation());
        }
    }

    public static boolean canEnterDungeonFromOpenWorld(Player player, int pointId, int dungeonId) {
        if (player == null || pointId <= 0 || dungeonId <= 0) {
            return false;
        }
        DungeonData dungeonData = GameData.getDungeonDataMap().get(dungeonId);
        if (dungeonData == null) {
            Grasscutter.getLogger()
                    .warn("Blocked dungeon enter uid={} unknown dungeon {}", player.getUid(), dungeonId);
            return false;
        }
        Scene scene = player.getScene();
        if (scene == null) {
            return false;
        }
        if (scene.getSceneType() == SceneType.SCENE_DUNGEON && scene.getId() == dungeonData.getSceneId()) {
            return true;
        }
        if (scene.getSceneType() != SceneType.SCENE_WORLD) {
            Grasscutter.getLogger()
                    .warn(
                            "Blocked dungeon enter uid={} dungeon={} from scene {} ({})",
                            player.getUid(),
                            dungeonId,
                            scene.getId(),
                            scene.getSceneType());
            return false;
        }
        ScenePointEntry scenePointEntry = GameData.getScenePointEntryById(scene.getId(), pointId);
        if (scenePointEntry == null || scenePointEntry.getPointData() == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Blocked dungeon enter uid={} unknown point {} in scene {}",
                            player.getUid(),
                            pointId,
                            scene.getId());
            return false;
        }
        PointData pointData = scenePointEntry.getPointData();
        if (!containsDungeonId(pointData, dungeonId)) {
            Grasscutter.getLogger()
                    .warn(
                            "Blocked dungeon enter uid={} point {} does not offer dungeon {}",
                            player.getUid(),
                            pointId,
                            dungeonId);
            return false;
        }
        if (!isNearEntryPoint(player.getPosition(), pointData)) {
            Position entry = resolveEntryAnchor(pointData);
            Position playerPosition = player.getPosition();
            Grasscutter.getLogger()
                    .warn(
                            "Blocked dungeon enter uid={} point={} dungeon={}: player at ({}, {}, {}), entry at ({}, {}, {})",
                            player.getUid(),
                            pointId,
                            dungeonId,
                            playerPosition != null ? playerPosition.getX() : 0.0f,
                            playerPosition != null ? playerPosition.getY() : 0.0f,
                            playerPosition != null ? playerPosition.getZ() : 0.0f,
                            entry != null ? entry.getX() : 0.0f,
                            entry != null ? entry.getY() : 0.0f,
                            entry != null ? entry.getZ() : 0.0f);
            return false;
        }
        return true;
    }

    public static void handleQuickOpen(Player player, int entryConfigId) {
        if (player == null || entryConfigId <= 0) {
            return;
        }
        DungeonEntryData dungeonEntryData = GameData.getDungeonEntryDataMap().get(entryConfigId);
        if (dungeonEntryData == null) {
            Grasscutter.getLogger().debug("Unknown dungeon entry config id {}", entryConfigId);
            return;
        }
        ScenePointEntry scenePointEntry =
                GameData.getScenePointEntryById(
                        dungeonEntryData.getSceneId(), dungeonEntryData.getDungeonEntryId());
        if (scenePointEntry == null || scenePointEntry.getPointData() == null) {
            Grasscutter.getLogger()
                    .debug(
                            "Missing scene point {}:{} for entry config {}",
                            dungeonEntryData.getSceneId(),
                            dungeonEntryData.getDungeonEntryId(),
                            entryConfigId);
            return;
        }
        int[] dungeonIds = scenePointEntry.getPointData().getDungeonIds();
        if (dungeonIds == null || dungeonIds.length == 0) {
            return;
        }
        int pointId = dungeonEntryData.getDungeonEntryId();
        int dungeonId = dungeonIds[0];
        DungeonData dungeonData = GameData.getDungeonDataMap().get(dungeonId);
        if (dungeonData == null || !canEnterDungeonFromOpenWorld(player, pointId, dungeonId)) {
            return;
        }
        Grasscutter.getLogger()
                .info(
                        "Dungeon quick-open uid={} entryConfig={} point={} dungeon={}",
                        player.getUid(),
                        entryConfigId,
                        pointId,
                        dungeonId);
        Scene scene = player.getScene();
        DungeonManager dungeonManager = scene != null ? scene.getDungeonManager() : null;
        boolean entered;
        if (dungeonManager != null
                && dungeonManager.getDungeonData() != null
                && dungeonManager.getDungeonData().getId() == dungeonId) {
            player.getServer().getDungeonSystem().restartDungeon(player);
            onPlayerEnterDomain(player);
            entered = true;
        } else {
            entered = player.getServer().getDungeonSystem().enterDungeon(player, pointId, dungeonId, true);
            if (entered) {
                onPlayerEnterDomain(player);
            }
        }
        if (!entered) {
            Grasscutter.getLogger()
                    .debug(
                            "Quick open failed for entry {} (point {} dungeon {})",
                            entryConfigId,
                            pointId,
                            dungeonId);
        }
    }

    private static boolean containsDungeonId(PointData pointData, int dungeonId) {
        int[] dungeonIds = pointData.getDungeonIds();
        if (dungeonIds != null) {
            for (int offeredDungeonId : dungeonIds) {
                if (offeredDungeonId == dungeonId) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Position resolveEntryAnchor(PointData pointData) {
        if (pointData.getPos() != null) {
            return pointData.getPos();
        }
        return pointData.getTranPos();
    }

    private static boolean isNearEntryPoint(Position position, PointData pointData) {
        if (position == null || pointData == null) {
            return false;
        }
        Position anchor = resolveEntryAnchor(pointData);
        if (anchor == null) {
            return false;
        }
        float radius = DEFAULT_ENTRY_RADIUS;
        Position size = pointData.getSize();
        if (size != null) {
            radius = Math.max(radius, Math.max(size.getX(), Math.max(size.getY(), size.getZ())) * 1.5f);
        }
        double dx = position.getX() - anchor.getX();
        double dy = position.getY() - anchor.getY();
        double dz = position.getZ() - anchor.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    static {
        CLIENT_SCENE_CACHE.defaultReturnValue(Integer.MIN_VALUE);
    }
}
