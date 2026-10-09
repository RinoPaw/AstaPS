package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HandlerNpcTalkReqTest {
    @Test
    void replyIsSentOnceEvenWhenTalkActionFails() {
        var responses = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> HandlerNpcTalkReq.runTalkAndReply(
                        () -> { throw new IllegalStateException("quest action failed"); },
                        responses::incrementAndGet));

        assertEquals(1, responses.get());
    }

    @Test
    void replyIsSentOnceAfterSuccessfulTalkAction() {
        var actions = new AtomicInteger();
        var responses = new AtomicInteger();

        HandlerNpcTalkReq.runTalkAndReply(actions::incrementAndGet, responses::incrementAndGet);

        assertEquals(1, actions.get());
        assertEquals(1, responses.get());
    }
}
