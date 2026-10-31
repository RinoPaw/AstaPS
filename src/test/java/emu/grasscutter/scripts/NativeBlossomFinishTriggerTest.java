package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneTrigger;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeBlossomFinishTriggerTest {
    @Test
    void finishUsesOriginalLuaTriggerDefinition() {
        var group = SceneGroup.of(133001374);
        var finish = new SceneTrigger();
        finish.setName("BLOSSOM_PROGRESS_FINISH_374016");
        finish.setEvent(EventType.EVENT_BLOSSOM_PROGRESS_FINISH);
        finish.currentGroup = group;
        var death = new SceneTrigger();
        death.setName("ANY_MONSTER_DIE_374015");
        death.setEvent(EventType.EVENT_ANY_MONSTER_DIE);
        death.currentGroup = group;
        group.triggers = Map.of(finish.getName(), finish, death.getName(), death);

        assertEquals(List.of(finish), SceneScriptManager.nativeBlossomFinishTriggers(group));
    }
}