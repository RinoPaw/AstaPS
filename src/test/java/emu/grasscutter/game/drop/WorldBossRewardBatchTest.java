package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.inventory.GameItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
class WorldBossRewardBatchTest {
    @Test
    void repeatedArtifactsBecomeSeparateInstances() {
        var rolled = List.of(new GameItem(57520, 3), new GameItem(202, 3000));
        var result = DropSystem.separateBossEquipment(rolled);
        assertEquals(4, result.size());
        for (int n = 0; n < 3; n++) {
            assertEquals(57520, result.get(n).getItemId());
            assertEquals(1, result.get(n).getCount());
            for (int m = 0; m < n; m++) assertNotSame(result.get(m), result.get(n));
        }
        assertSame(rolled.get(1), result.get(3));
    }
}
