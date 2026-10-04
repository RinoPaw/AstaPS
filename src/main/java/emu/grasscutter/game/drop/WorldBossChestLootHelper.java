package emu.grasscutter.game.drop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.scripts.data.SceneGadget;

/** Resolves a missing native boss drop tag; reward contents still come only from ChestDrop/DropTable. */
public final class WorldBossChestLootHelper {
    private WorldBossChestLootHelper() {}

    public static boolean grant(Player player, SceneGadget meta, int groupId) {
        if (player == null || meta == null) {
            return false;
        }

        if (meta.drop_tag != null && !meta.drop_tag.isBlank()) {
            return false;
        }

        int monsterConfigId =
                meta.boss_chest != null ? meta.boss_chest.monster_config_id : 0;
        String dropTag = BossChestDropTagResolver.resolve(groupId, monsterConfigId);
        if (dropTag == null || dropTag.isBlank()) {
            return false;
        }

        try {
            return player.getServer().getDropSystem().handleBossChestDrop(dropTag, player);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn(
                            "WorldBossChestLootHelper native drop failed tag={} uid={}",
                            dropTag,
                            player.getUid(),
                            t);
            return false;
        }
    }
}
