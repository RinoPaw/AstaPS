package emu.grasscutter.game.entity.gadget.chest;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.GameConfig.ChestReward;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.reward.RewardScaler;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;

/** Applies explicit user chest replacements. Original drop tables remain the default authority. */
public final class WorldChestLootHelper {
    private WorldChestLootHelper() {}

    /**
     * Applies a configured full replacement for this chest tier.
     *
     * @return true when a replacement existed and was applied; false means callers must use the
     *     original chest drop path.
     */
    public static boolean grantReplacement(Player player, EntityGadget entityGadget) {
        if (player == null || entityGadget == null || entityGadget.getScene() == null) {
            return false;
        }

        Tier tier = resolveTier(entityGadget);
        if (tier == null) {
            return false;
        }
        ChestReward reward = GAME.rewards.chest(tier.name());
        if (reward == null) {
            return false;
        }

        int sigilId = resolveSigilId(entityGadget);
        Scene scene = entityGadget.getScene();
        EntityGadget source = entityGadget;
        player.addExpDirectly(
                RewardScaler.scaleCount(
                        RewardScaler.ADVENTURE_EXP_ITEM_ID, reward.adventureExp, 1.0));
        drop(scene, source, 201, reward.primogems);
        drop(scene, source, 202, reward.mora);
        drop(scene, source, sigilId, reward.sigil);
        drop(scene, source, 104011, reward.enhancementOre);
        drop(scene, source, 104012, reward.fineEnhancementOre);
        drop(scene, source, 104013, reward.mysticEnhancementOre);
        drop(scene, source, 104001, reward.wanderersAdvice);
        drop(scene, source, 104002, reward.adventurersExperience);
        drop(scene, source, 104003, reward.herosWit);
        Grasscutter.getLogger()
                .info(
                        "World chest override uid={} gadgetId={} tier={} primogems={}",
                        player.getUid(),
                        entityGadget.getGadgetId(),
                        tier.name(),
                        reward.primogems);
        return true;
    }

    private static void drop(Scene scene, GameEntity gameEntity, int itemId, int baseCount) {
        int count = RewardScaler.scaleCount(itemId, baseCount, 1.0);
        if (scene == null || gameEntity == null || itemId <= 0 || count <= 0) {
            return;
        }
        try {
            scene.addItemEntity(itemId, count, gameEntity);
        } catch (Throwable throwable) {
            Grasscutter.getLogger()
                    .warn(
                            "World chest override drop failed item={} x{}: {}",
                            itemId,
                            count,
                            throwable.toString());
        }
    }

    public static Tier resolveTier(EntityGadget entityGadget) {
        int gadgetId = entityGadget.getGadgetId();
        switch (gadgetId) {
            case 70210001: {
                return Tier.COMMON;
            }
            case 70210002: {
                return Tier.EXQUISITE;
            }
            case 70210003: {
                return Tier.PRECIOUS;
            }
            case 70210004:
            case 70210005:
            case 70210006: {
                return Tier.LUXURIOUS;
            }
        }

        String name = "";
        if (entityGadget.getGadgetData() != null
                && entityGadget.getGadgetData().getJsonName() != null) {
            name = entityGadget.getGadgetData().getJsonName();
        }
        if (containsLv(name, 5) || name.contains("Drop_Chest_Lv3")) {
            return Tier.LUXURIOUS;
        }
        if (containsLv(name, 4) || containsLv(name, 3) || name.contains("Drop_Chest_Lv2")) {
            return Tier.PRECIOUS;
        }
        if (containsLv(name, 2) || name.contains("Drop_Chest_Lv1")) {
            return Tier.EXQUISITE;
        }
        if (containsLv(name, 1) || name.contains("NormalChest") || name.contains("Rock_Lv1")) {
            return Tier.COMMON;
        }

        SceneGadget sceneGadget = entityGadget.getMetaGadget();
        if (sceneGadget != null && sceneGadget.drop_tag != null) {
            String tag = sceneGadget.drop_tag;
            if (tag.contains("超级") || tag.contains("豪华")) {
                return Tier.LUXURIOUS;
            }
            if (tag.contains("高级")) {
                return Tier.PRECIOUS;
            }
            if (tag.contains("中级")) {
                return Tier.EXQUISITE;
            }
            if (tag.contains("低级") || tag.contains("初级")) {
                return Tier.COMMON;
            }
        }
        return null;
    }

    private static boolean containsLv(String value, int level) {
        return value.contains("_Lv" + level)
                || value.contains("Lv" + level + "_")
                || value.endsWith("Lv" + level);
    }

    private static int resolveSigilId(EntityGadget entityGadget) {
        try {
            int gid = entityGadget.getGroupId();
            if (gid >= 910700000 && gid < 910800000) {
                int id = gid - 910700000;
                if (id >= 40000 && id < 50000) return 303;
                if (id >= 30000 && id < 40000) return 301;
                if (id >= 20000 && id < 30000) return 302;
                if (id >= 10000 && id < 20000) return 306;
                if (id > 0 && id < 10000) return 308;
            }
            if (gid >= 910800000 && gid < 910900000) {
                return 306;
            }
        } catch (Throwable ignored) {
        }

        SceneGadget sceneGadget = entityGadget.getMetaGadget();
        if (sceneGadget != null && sceneGadget.drop_tag != null) {
            String tag = sceneGadget.drop_tag;
            if (tag.contains("璃月")) return 307;
            if (tag.contains("稻妻")) return 304;
            if (tag.contains("须弥")) return 303;
            if (tag.contains("枫丹")) return 302;
            if (tag.contains("纳塔")) return 301;
            if (tag.contains("至冬") || tag.contains("雪山")) return 306;
            if (tag.contains("挪德卡莱") || tag.contains("诺德克莱") || tag.contains("霜月")) {
                return 308;
            }
            if (tag.contains("蒙德")) return 305;
        }

        try {
            if (entityGadget.getScene() != null
                    && entityGadget.getScene().getId() == 3
                    && entityGadget.getPosition() != null) {
                int byPos = sigilByPosition(entityGadget.getPosition());
                if (byPos > 0) return byPos;
            }
        } catch (Throwable ignored) {
        }
        int sceneId = entityGadget.getScene() != null ? entityGadget.getScene().getId() : 3;
        return sigilByScene(sceneId);
    }

    private static int sigilByPosition(Position pos) {
        if (pos == null) return 0;
        float x = pos.getX();
        float z = pos.getZ();
        if (x >= 7000.0f && x <= 11000.0f && z >= 5200.0f && z <= 8800.0f) return 306;
        if (z >= 8800.0f) return 308;
        if (z >= 2700.0f && z <= 5600.0f && x >= 1000.0f && x <= 5200.0f) return 302;
        if (z >= 6000.0f && z <= 11000.0f && x >= -4000.0f && x <= 2000.0f) return 301;
        if (z >= 5200.0f && z <= 7200.0f && x >= -500.0f && x <= 1500.0f) return 303;
        return 0;
    }

    private static int sigilByScene(int sceneId) {
        if (sceneId == 3) return 305;
        if (sceneId == 5 || sceneId == 6) return 307;
        if (sceneId == 7) return 304;
        return 305;
    }

    public enum Tier {
        COMMON,
        EXQUISITE,
        PRECIOUS,
        LUXURIOUS
    }
}
