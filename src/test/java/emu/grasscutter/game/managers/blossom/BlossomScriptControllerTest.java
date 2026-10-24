package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.ScriptLoader;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

final class BlossomScriptControllerTest {
    @Test
    void defaultHooksAreLuaFunctions() throws Exception {
        ScriptLoader.init();
        var defaults = ScriptLoader.getEngine().createBindings();
        ScriptLoader.evalBlossomDefaults(defaults);

        var start = BlossomScriptController.selectHook(null, defaults, BlossomScriptController.START);
        var complete = BlossomScriptController.selectHook(
                null, defaults, BlossomScriptController.COMBAT_COMPLETE);
        assertTrue(start.isfunction());
        assertTrue(complete.isfunction());
        assertNotSame(start, complete);
    }

    @Test
    void originalSelectRouteRequiresACompleteLiveEncounter() {
        var group = new emu.grasscutter.scripts.data.SceneGroup();
        var finish = new emu.grasscutter.scripts.data.SceneTrigger();
        finish.setEvent(emu.grasscutter.scripts.constants.EventType.EVENT_BLOSSOM_PROGRESS_FINISH);
        group.triggers = java.util.Map.of("finish", finish);
        group.monsters = java.util.Map.of(1, new emu.grasscutter.scripts.data.SceneMonster());

        assertTrue(BlossomScriptController.hasNativeEncounter(group, true));
        assertFalse(BlossomScriptController.hasNativeEncounter(group, false));
        group.monsters = java.util.Map.of();
        assertFalse(BlossomScriptController.hasNativeEncounter(group, true));
    }

    @Test
    void nativeMonsterProgressFinishesExactlyOnce() {
        var activity = new BlossomActivity(null);
        activity.beginNative(2);
        assertFalse(activity.addNativeProgress());
        assertTrue(activity.addNativeProgress());
        assertFalse(activity.addNativeProgress());
    }

    @Test
    void authoredLuaOverridesOnlyTheNamedHook() throws Exception {
        ScriptLoader.init();
        var defaults = ScriptLoader.getEngine().createBindings();
        ScriptLoader.evalBlossomDefaults(defaults);
        var authored = ScriptLoader.getEngine().createBindings();
        var override = new org.luaj.vm2.lib.TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue context, LuaValue groupId) {
                return LuaValue.ZERO;
            }
        };
        authored.put(BlossomScriptController.START, override);

        assertSame(override, BlossomScriptController.selectHook(
                authored, defaults, BlossomScriptController.START));
        assertSame(defaults.get(BlossomScriptController.COMBAT_COMPLETE),
                BlossomScriptController.selectHook(
                        authored, defaults, BlossomScriptController.COMBAT_COMPLETE));
    }
}
