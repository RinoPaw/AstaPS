package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.SceneAreaCatalog;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Scene area IDs displayed to the client, independent of persisted exploration progress. */
public final class SceneAreaVisibility {
    private SceneAreaVisibility() {}

    public static Collection<Integer> forPlayer(Player player, int sceneId) {
        var valid = SceneAreaCatalog.forScene(sceneId);
        if (!GAME_OPTIONS.mapFogEnabled) return valid;

        var areas = new LinkedHashSet<>(player.getUnlockedSceneAreas(sceneId));
        if (!valid.isEmpty()) areas.retainAll(valid);
        if (sceneId != 3) return areas;

        // Native scene3_point.json: Statue of the Seven (3,7) owns area 1.
        // Old saves and early quest commands may already contain area 1 without the
        // statue having been activated. Neither grants visibility in fog-on mode.
        boolean statueActive = player.getUnlockedScenePoints(3).contains(7)
                && !player.isScenePointForceLocked(3, 7);
        if (areas.contains(1) == statueActive) return areas;

        // Presentation only: never rewrite a player's persisted discovery flags.
        var visible = new LinkedHashSet<>(areas);
        if (statueActive) visible.add(1);
        else visible.remove(1);
        return visible;
    }
}
