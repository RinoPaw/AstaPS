package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AmberGuideOpenStateTest {
    @Test
    void distinguishesExplicitValueFromDefaultAndAbsentState() {
        assertEquals(
                "1=absent, 2=absent, 7=stored:1, 8=default:1, 16=absent, "
                        + "17=stored:0, 24=absent, 60=absent",
                GameSession.summarizeAmberGuideOpenStates(
                        Map.of(7, 1, 17, 0), Set.of(7, 8)));
    }

    @Test
    void noPersistedStateMustNotBeReportedAsExplicitlyUnlocked() {
        assertEquals(
                "1=absent, 2=absent, 7=absent, 8=absent, 16=absent, "
                        + "17=absent, 24=absent, 60=absent",
                GameSession.summarizeAmberGuideOpenStates(Map.of(), Set.of()));
    }
}
