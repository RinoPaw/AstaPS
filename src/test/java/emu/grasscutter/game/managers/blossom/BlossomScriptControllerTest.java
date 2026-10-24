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
