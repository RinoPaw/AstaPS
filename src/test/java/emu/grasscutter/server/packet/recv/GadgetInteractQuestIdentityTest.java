package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class GadgetInteractQuestIdentityTest {
    @Test
    void onlyCorrectGadgetDataIdCanAdvanceAnInteractionObjective() {
        assertTrue(HandlerGadgetInteractReq.matchesQuestGadget(70300015, 70300015));
        assertFalse(HandlerGadgetInteractReq.matchesQuestGadget(70300015, 70300016));
        assertFalse(HandlerGadgetInteractReq.matchesQuestGadget(0, 70300015));
        assertFalse(HandlerGadgetInteractReq.matchesQuestGadget(70300015, 0));
    }
}
