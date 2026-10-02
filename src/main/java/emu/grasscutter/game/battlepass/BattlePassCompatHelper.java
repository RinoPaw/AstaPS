package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlePassCompatHelper {
    private static final ConcurrentHashMap<Integer, Integer> SELECTED_PLAN =
            new ConcurrentHashMap<>();

    private BattlePassCompatHelper() {}

    public static int getSelectedPlan(Player player) {
        return player == null ? 1 : SELECTED_PLAN.getOrDefault(player.getUid(), 1);
    }

    public static void setSelectedPlan(Player player, int plan) {
        if (player != null) {
            SELECTED_PLAN.put(player.getUid(), plan > 0 ? plan : 1);
        }
    }

    public static void clearPlayerState(int uid) {
        SELECTED_PLAN.remove(uid);
    }

    public static boolean setPaidFlag(BattlePassManager battlePassManager, boolean paid) {
        if (battlePassManager == null) {
            return false;
        }
        try {
            Field field = BattlePassManager.class.getDeclaredField("paid");
            field.setAccessible(true);
            field.setBoolean(battlePassManager, paid);
            return true;
        } catch (Exception exception) {
            Grasscutter.getLogger().error("BattlePass setPaid failed", exception);
            return false;
        }
    }
}
