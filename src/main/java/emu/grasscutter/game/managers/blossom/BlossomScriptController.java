package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.SceneGroup;
import javax.script.Bindings;
import org.luaj.vm2.LuaValue;

/**
 * One encounter dispatch path: the authored group can override the default Lua hooks.
 * Both hooks run with the normal ScriptLib scene/entity context.
 */
final class BlossomScriptController {
    static final String START = "blossom_on_start";
    static final String COMBAT_COMPLETE = "blossom_on_combat_complete";

    private BlossomScriptController() {}

    static LuaValue selectHook(Bindings authored, Bindings defaults, String hook) {
        Object override = authored == null ? null : authored.get(hook);
        if (override instanceof LuaValue lua && lua.isfunction()) return lua;
        return (LuaValue) defaults.get(hook);
    }

    /** An original scene event owns the combat only when its complete trigger set is live. */
    static boolean hasNativeEncounter(SceneGroup group, boolean hasSelectTrigger) {
        return hasSelectTrigger && group != null && group.monsters != null
                && !group.monsters.isEmpty() && group.triggers != null
                && group.triggers.values().stream()
                    .anyMatch(trigger -> trigger.getEvent() == EventType.EVENT_BLOSSOM_PROGRESS_FINISH);
    }

    static boolean call(EntityGadget gadget, SceneGroup group, String hook) {
        try {
            var authored = group == null ? null : group.getBindings();
            var scripted = authored == null ? null : authored.get(hook);
            var scripts = gadget.getScene().getScriptManager();
            if (START.equals(hook) && !(scripted instanceof LuaValue lua && lua.isfunction())
                    && hasNativeEncounter(group, scripts.hasBlossomSelectTriggers(gadget.getGroupId()))) {
                var blossoms = gadget.getScene().getBlossomManager();
                if (!blossoms.beginNativeEncounter(gadget, group.monsters.size())) return false;
                return scripts.dispatchBlossomSelect(
                        gadget.getGroupId(), gadget.getConfigId(), 187);
            }

            var bindings = ScriptLoader.getEngine().createBindings();
            ScriptLoader.evalBlossomDefaults(bindings);
            var function = selectHook(authored, bindings, hook);
            if (function == null || !function.isfunction()) return false;
            return ScriptLoader.getScriptLib().withEntityContext(gadget, group, () -> {
                var result = function.call(
                        ScriptLoader.getScriptLibLua(), LuaValue.valueOf(gadget.getGroupId()));
                return result.isnumber() && result.toint() == 0;
            });
        } catch (Throwable error) {
            Grasscutter.getLogger().error(
                    "Ley-line Lua hook failed group={} config={} hook={}",
                    gadget.getGroupId(), gadget.getConfigId(), hook, error);
            return false;
        }
    }
}
