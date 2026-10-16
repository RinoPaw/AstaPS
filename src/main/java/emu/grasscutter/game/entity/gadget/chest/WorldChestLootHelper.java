/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.server.packet.send.PacketDropHintNotify;

public final class WorldChestLootHelper {
    private WorldChestLootHelper() {
    }

    public static void grant(Player player, EntityGadget entityGadget) {
        var props = new java.util.HashMap<String, String>();
        String chestType = ChestTypeResolver.resolve(entityGadget);
        if (chestType == null) {
            throw new IllegalStateException(
                    "Unrecognized world chest type: gadgetId=" + entityGadget.getGadgetId()
                    + " jsonName=" + (entityGadget.getGadgetData() == null
                            ? null : entityGadget.getGadgetData().getJsonName())
                    + " group=" + entityGadget.getGroupId()
                    + " config=" + entityGadget.getConfigId());
        }
        props.put("type", chestType);
        // Special chest types have no implicit common/luxurious grade.
        if ("normal".equals(chestType)) {
            props.put("grade", resolveTier(entityGadget).name().toLowerCase(java.util.Locale.ROOT));
        }
        props.put("scene", Integer.toString(entityGadget.getScene().getId()));
        props.put("gadget", Integer.toString(entityGadget.getGadgetId()));
        props.put("group", Integer.toString(entityGadget.getGroupId()));
        props.put("config", Integer.toString(entityGadget.getConfigId()));
        SceneGadget meta = entityGadget.getMetaGadget();
        if (meta != null) {
            props.put("level", Integer.toString(meta.level));
        }
        var context = new emu.grasscutter.game.reward.RewardContext(
                entityGadget.getScene().getId(),
                meta != null ? meta.area_id : 0,
                meta != null ? meta.rewardRegion : null);
        emu.grasscutter.game.reward.RewardAreaResolver.resolve(context)
                .addAttributes(props);

        var items = emu.grasscutter.game.reward.RewardConfiguration.current().roll(
                "chest", props, java.util.concurrent.ThreadLocalRandom.current(), context);
        for (var entry : items.entrySet()) {
            int id = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) continue;
            if (id == 102) {
                player.earnExp(amount);
            } else {
                var item = GameData.getItemDataMap().get(id);
                var dropRule = GameData.getDropMaterialDataMap().get(id);
                if (shouldAutoCollect(
                        item == null ? null : item.getItemType(),
                        item == null ? -1 : item.getGadgetId(),
                        dropRule != null && dropRule.isAutoPick())) {
                    if (player.getInventory().addItem(id, amount, ActionReason.OpenChest)) {
                        player.sendPacket(new PacketDropHintNotify(
                                id, entityGadget.getPosition().toProto()));
                        continue;
                    }
                    Grasscutter.getLogger().warn(
                            "World chest auto-pick failed uid={} item={} x{}; dropping to ground",
                            player.getUid(), id, amount);
                }
                drop(entityGadget.getScene(), entityGadget, id, amount);
            }
        }
        Grasscutter.getLogger().info(
                "World chest rewards uid={} gadget={} type={} grade={} itemTypes={}",
                player.getUid(), entityGadget.getGadgetId(), props.get("type"),
                props.get("grade"), items.size());
    }

    // Same policy as DropSystem.dropItem: virtual currency without a gadget,
    // or a native DropMaterial entry explicitly marked for automatic pickup.
    static boolean shouldAutoCollect(ItemType type, int gadgetId, boolean autoPick) {
        return autoPick || (type == ItemType.ITEM_VIRTUAL && gadgetId == 0);
    }

    private static void drop(Scene scene, GameEntity gameEntity, int itemId, int count) {
        if (scene == null || gameEntity == null || itemId <= 0 || count <= 0) {
            throw new IllegalArgumentException(
                    "Invalid chest ground drop: item=" + itemId + " count=" + count);
        }
        // A failed world drop is a failed grant, not a successful chest claim.
        scene.addItemEntity(itemId, count, gameEntity);
    }

    public static Tier resolveTier(EntityGadget entityGadget) {
        SceneGadget meta = entityGadget.getMetaGadget();
        if (meta != null && meta.chestGrade != null) {
            try {
                return Tier.valueOf(meta.chestGrade.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid source chest grade: " + meta.chestGrade
                        + " gadgetId=" + entityGadget.getGadgetId(), e);
            }
        }
        String jsonName = entityGadget.getGadgetData() == null
                ? null : entityGadget.getGadgetData().getJsonName();
        return resolveTier(entityGadget.getGadgetId(), jsonName);
    }

    static Tier resolveTier(int n, String jsonName) {
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
        String string = jsonName == null ? "" : jsonName;
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
        throw new IllegalStateException("Unrecognized world chest grade: gadgetId=" + n
                + " jsonName=" + jsonName);
    }

    private static boolean containsLv(String string, int n) {
        return string.contains("_Lv" + n) || string.contains("Lv" + n + "_") || string.endsWith("Lv" + n);
    }

    public enum Tier {
        COMMON,
        EXQUISITE,
        PRECIOUS,
        LUXURIOUS
    }
}
