package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.SceneGroup;

/** One original scene-event path for ley-line encounters. */
final class BlossomScriptController {
    private BlossomScriptController() {}

    static boolean hasNativeEncounter(SceneGroup group) {
        return group != null
                && group.monsters != null && !group.monsters.isEmpty()
                && group.triggers != null
                && group.triggers.values().stream()
                        .anyMatch(trigger -> trigger.getEvent() == EventType.EVENT_SELECT_OPTION)
                && group.triggers.values().stream()
                        .anyMatch(trigger ->
                                trigger.getEvent() == EventType.EVENT_BLOSSOM_PROGRESS_FINISH);
    }

    static boolean start(EntityGadget gadget, SceneGroup group) {
        if (!hasNativeEncounter(group)) {
            Grasscutter.getLogger().warn(
                    "Ley-line group {} has no original encounter triggers; refusing to start",
                    gadget.getGroupId());
            return false;
        }
        var scripts = gadget.getScene().getScriptManager();
        if (!scripts.hasBlossomSelectTriggers(gadget.getGroupId())) return false;
        var blossoms = gadget.getScene().getBlossomManager();
        if (!blossoms.beginNativeEncounter(gadget, group.monsters.size())) return false;
        return scripts.dispatchBlossomSelect(gadget.getGroupId(), gadget.getConfigId(), 187);
    }
}
