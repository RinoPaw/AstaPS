package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DungeonRestartParticipantsTest {
    @Test
    void singlePlayerCanBeDetachedDuringRestart() {
        var live = new ArrayList<>(List.of(70632));
        var detached = DungeonRestartParticipants.detachAll(live, live::remove);
        assertEquals(List.of(70632), detached);
        assertTrue(live.isEmpty());
    }

    @Test
    void allPartyMembersSurviveLiveRosterMutation() {
        var live = new ArrayList<>(List.of(1, 2, 3));
        var visited = new ArrayList<Integer>();
        var detached = DungeonRestartParticipants.detachAll(live, player -> {
            visited.add(player);
            live.remove(player);
        });
        assertEquals(List.of(1, 2, 3), detached);
        assertEquals(detached, visited);
        assertTrue(live.isEmpty());
    }
}
