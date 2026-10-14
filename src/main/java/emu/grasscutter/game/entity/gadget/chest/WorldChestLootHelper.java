package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.reward.ChestRewardConfig;
import emu.grasscutter.game.reward.RewardConfig;
import emu.grasscutter.game.reward.StandardChestRewardResolver;
import java.util.Map;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;

public final class WorldChestLootHelper {
    private WorldChestLootHelper() {
    }

    /**
     * Generic fixed fields come from CSS. Ordinary chests use the native
     * random pool once, with explicit CSS values replacing matching items.
     * Other fallback and quest/Boss chests retain their existing paths.
     */
    public static void grant(Player player, EntityGadget entityGadget) {
        if (player == null || entityGadget == null || entityGadget.getScene() == null) {
            return;
        }
        Tier tier = resolveTier(entityGadget);
        var preview = StandardChestRewardResolver.preview(entityGadget);
        ChestRewardConfig.Match base = preview.isPresent() ? null
                : RewardConfig.current().chest().resolve(
                        ChestRewardConfig.Kind.valueOf(tier.name()), 0, 0, 0);
        Map<Integer, Integer> amounts = preview.isPresent()
                ? preview.orElseThrow().amounts() : base.amounts();
        int fallbackSigilId = resolveSigilId(entityGadget);
        SceneGadget meta = entityGadget.getMetaGadget();
        if (StandardChestClassifier.classify(entityGadget).isStandard()
                && meta != null && meta.drop_tag != null
                && player.getServer().getDropSystem().handleStandardChestDrop(
                        meta.drop_tag, Math.max(1, meta.level), entityGadget,
                        amounts, fallbackSigilId)) {
            Grasscutter.getLogger().debug(
                    "Native chest rewards uid={} gadgetId={} tag={} level={}",
                    player.getUid(), entityGadget.getGadgetId(), meta.drop_tag, meta.level);
            return;
        }
        // Missing native tag/root: retain the operator's CSS-only fixed payout.
        Map<Integer, Integer> rewards =
                ChestRewardConfig.resolveRegionalSigils(amounts, fallbackSigilId);
        Scene scene = entityGadget.getScene();
        int adventureExp = rewards.getOrDefault(102, 0);
        if (adventureExp > 0) {
            player.earnExp(adventureExp);
        }
        rewards.forEach((itemId, count) -> {
            if (itemId != 102) drop(scene, entityGadget, itemId, count);
        });
        Grasscutter.getLogger().info(
                "WorldChestLoot drop uid={} gadgetId={} tier={} policy={} selector={} "
                        + "adventureExp={} primogems={}",
                player.getUid(), entityGadget.getGadgetId(), tier.name(),
                preview.isPresent() ? "css-region" : "css-default",
                preview.isPresent() ? preview.orElseThrow().lastSelector() : base.selector(),
                adventureExp, rewards.getOrDefault(201, 0));
    }

    private static void drop(Scene scene, GameEntity gameEntity, int n, int n2) {
        if (scene == null || gameEntity == null || n <= 0 || n2 <= 0) {
            return;
        }
        try {
            scene.addItemEntity(n, n2, gameEntity);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("WorldChestLoot drop failed item={} x{}: {}", n, n2, throwable.toString());
        }
    }

    public static Tier resolveTier(EntityGadget entityGadget) {
        int n = entityGadget.getGadgetId();
        switch (n) {
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
        String string = "";
        if (entityGadget.getGadgetData() != null && entityGadget.getGadgetData().getJsonName() != null) {
            string = entityGadget.getGadgetData().getJsonName();
        }
        if (WorldChestLootHelper.containsLv(string, 5) || string.contains("Drop_Chest_Lv3")) {
            return Tier.LUXURIOUS;
        }
        if (WorldChestLootHelper.containsLv(string, 4) || WorldChestLootHelper.containsLv(string, 3) || string.contains("Drop_Chest_Lv2")) {
            return Tier.PRECIOUS;
        }
        if (WorldChestLootHelper.containsLv(string, 2) || string.contains("Drop_Chest_Lv1")) {
            return Tier.EXQUISITE;
        }
        if (WorldChestLootHelper.containsLv(string, 1) || string.contains("NormalChest") || string.contains("Rock_Lv1")) {
            return Tier.COMMON;
        }
        SceneGadget sceneGadget = entityGadget.getMetaGadget();
        if (sceneGadget != null && sceneGadget.drop_tag != null) {
            String string2 = sceneGadget.drop_tag;
            if (string2.contains("\u8d85\u7ea7") || string2.contains("\u8c6a\u534e")) {
                return Tier.LUXURIOUS;
            }
            if (string2.contains("\u9ad8\u7ea7")) {
                return Tier.PRECIOUS;
            }
            if (string2.contains("\u4e2d\u7ea7")) {
                return Tier.EXQUISITE;
            }
            if (string2.contains("\u4f4e\u7ea7") || string2.contains("\u521d\u7ea7")) {
                return Tier.COMMON;
            }
        }
        return Tier.COMMON;
    }

    private static boolean containsLv(String string, int n) {
        return string.contains("_Lv" + n) || string.contains("Lv" + n + "_") || string.endsWith("Lv" + n);
    }

    private static int resolveSigilId(EntityGadget entityGadget) {
        // Explore-spawned chests: region from synthetic group / config id
        try {
            int gid = entityGadget.getGroupId();
            // NodKraiExploreSpawnHelper SYNTH_GROUP_BASE = 910700000
            if (gid >= 910700000 && gid < 910800000) {
                int id = gid - 910700000;
                if (id >= 40000 && id < 50000) return 303; // Sumeru, dendro sigil
                if (id >= 30000 && id < 40000) return 301; // Natlan, pyro sigil
                if (id >= 20000 && id < 30000) return 302; // Fontaine, hydro sigil
                if (id >= 10000 && id < 20000) return 306; // Snezhnaya, cryo sigil
                if (id > 0 && id < 10000) return 308; // Nod-Krai, lunar sigil
            }
            // SnezhnayaExploreSpawnHelper SYNTH_GROUP_BASE = 910800000
            if (gid >= 910800000 && gid < 910900000) {
                return 306; // Snezhnaya, cryo sigil
            }
        } catch (Throwable ignored) {
        }
        SceneGadget sceneGadget = entityGadget.getMetaGadget();
        if (sceneGadget != null && sceneGadget.drop_tag != null) {
            String string = sceneGadget.drop_tag;
            if (string.contains("\u7483\u6708")) {
                return 307;
            }
            if (string.contains("\u7a3b\u59bb")) {
                return 304;
            }
            if (string.contains("\u987b\u5f26") || string.contains("\u987b\u5f25")) {
                return 303;
            }
            if (string.contains("\u67ab\u4e39")) {
                return 302;
            }
            if (string.contains("\u7eb3\u5854")) {
                return 301;
            }
            if (string.contains("\u81f3\u51ac") || string.contains("\u96ea\u5c71")) {
                return 306;
            }
            if (string.contains("\u632a\u5fb7\u5361\u83b1")
                    || string.contains("\u8bfa\u5fb7\u514b\u83b1")
                    || string.contains("\u971c\u6708")) {
                return 308; // Nod-Krai, lunar sigil
            }
            if (string.contains("\u8499\u5fb7")) {
                return 305;
            }
        }
        // Open-world scene 3: infer from coordinates when tag missing / wrong
        try {
            if (entityGadget.getScene() != null
                    && entityGadget.getScene().getId() == 3
                    && entityGadget.getPosition() != null) {
                int byPos = WorldChestLootHelper.sigilByPosition(entityGadget.getPosition());
                if (byPos > 0) {
                    return byPos;
                }
            }
        } catch (Throwable ignored) {
        }
        int n = entityGadget.getScene() != null ? entityGadget.getScene().getId() : 3;
        return WorldChestLootHelper.sigilByScene(n);
    }

    /** 301 pyro, 302 hydro, 303 dendro, 304 electro, 305 anemo, 306 cryo, 307 geo, 308 lunar; 0 unknown. */
    private static int sigilByPosition(Position pos) {
        if (pos == null) return 0;
        float x = pos.getX();
        float z = pos.getZ();
        // Snezhnaya
        if (x >= 7000.0f && x <= 11000.0f && z >= 5200.0f && z <= 8800.0f) {
            return 306;
        }
        // Nod-Krai and the far north (high Z): lunar sigil
        if (z >= 8800.0f) {
            return 308;
        }
        // Fontaine
        if (z >= 2700.0f && z <= 5600.0f && x >= 1000.0f && x <= 5200.0f) {
            return 302;
        }
        // Natlan
        if (z >= 6000.0f && z <= 11000.0f && x >= -4000.0f && x <= 2000.0f) {
            return 301;
        }
        // Sumeru 3.6 desert
        if (z >= 5200.0f && z <= 7200.0f && x >= -500.0f && x <= 1500.0f) {
            return 303;
        }
        return 0;
    }

    private static int sigilByScene(int n) {
        if (n == 3) {
            return 305;
        }
        if (n == 5 || n == 6) {
            return 307;
        }
        if (n == 7) {
            return 304;
        }
        return 305;
    }

    public enum Tier {
        COMMON, EXQUISITE, PRECIOUS, LUXURIOUS
    }
}
