package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.scripts.data.SceneTrigger;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BlossomScriptControllerTest {
    @Test
    void nativeEncounterRequiresMonstersAndBothOriginalEvents() {
        var group = new SceneGroup();
        var select = new SceneTrigger();
        select.setEvent(EventType.EVENT_SELECT_OPTION);
        var finish = new SceneTrigger();
        finish.setEvent(EventType.EVENT_BLOSSOM_PROGRESS_FINISH);
        group.monsters = Map.of(1, new SceneMonster());
        group.triggers = Map.of("select", select, "finish", finish);
        assertTrue(BlossomScriptController.hasNativeEncounter(group));
        group.triggers = Map.of("select", select);
        assertFalse(BlossomScriptController.hasNativeEncounter(group));
        group.triggers = Map.of("finish", finish);
        assertFalse(BlossomScriptController.hasNativeEncounter(group));
        group.triggers = Map.of("select", select, "finish", finish);
        group.monsters = Map.of();
        assertFalse(BlossomScriptController.hasNativeEncounter(group));
    }

    @Test
    void originalMonsterProgressFinishesOnlyOnce() {
        var activity = new BlossomActivity(null);
        activity.beginNative(2);
        assertFalse(activity.addNativeProgress());
        assertTrue(activity.addNativeProgress());
        assertFalse(activity.addNativeProgress());
    }
}
