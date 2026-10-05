package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.*;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IDispatcherCorrelationRegressionTest {
    private static final int REQUEST_ID = 7001;
    private static final int RESPONSE_ID = 7002;

    @Test
    void sameTypeRequestsStayDistinctWhenResponsesArriveOutOfOrder() {
        var dispatcher = new FakeDispatcher();

        var futureA = dispatcher.async(request("A"), REQUEST_ID, RESPONSE_ID);
        var futureB = dispatcher.async(request("B"), REQUEST_ID, RESPONSE_ID);

        dispatcher.respond(RESPONSE_ID, response("B", true));

        assertFalse(futureA.isDone(), "B response must not complete request A");
        assertEquals("B", futureB.join().get("name").getAsString());

        dispatcher.respond(RESPONSE_ID, response("A", true));
        assertEquals("A", futureA.join().get("name").getAsString());
    }

    @Test
    void successAndFailureForSameTypeRequestsDoNotCross() {
        var dispatcher = new FakeDispatcher();

        var futureA =
                dispatcher.async(
                        request("A"),
                        REQUEST_ID,
                        RESPONSE_ID,
                        packet -> IDispatcher.decode(packet).get("ok").getAsBoolean());
        var futureB =
                dispatcher.async(
                        request("B"),
                        REQUEST_ID,
                        RESPONSE_ID,
                        packet -> IDispatcher.decode(packet).get("ok").getAsBoolean());

        dispatcher.respond(RESPONSE_ID, response("A", true));

        assertTrue(futureA.join());
        assertFalse(futureB.isDone(), "A success must not complete request B");

        dispatcher.respond(RESPONSE_ID, response("B", false));
        assertFalse(futureB.join());
    }

    private static JsonObject request(String name) {
        var request = new JsonObject();
        request.addProperty("name", name);
        return request;
    }

    private static JsonObject response(String name, boolean ok) {
        var response = new JsonObject();
        response.addProperty("name", name);
        response.addProperty("ok", ok);
        return response;
    }

    private static final class FakeDispatcher implements IDispatcher {
        private final Logger logger = LoggerFactory.getLogger(FakeDispatcher.class);
        private final Map<Integer, BiConsumer<WebSocket, JsonElement>> handlers = new HashMap<>();
        private final Map<Integer, List<Consumer<JsonElement>>> callbacks = new HashMap<>();

        @Override
        public void sendMessage(int packetId, Object message) {}

        void respond(int packetId, JsonElement packet) {
            var registered = this.callbacks.get(packetId);
            if (registered == null) return;

            List.copyOf(registered).forEach(callback -> callback.accept(packet));
            registered.clear();
        }

        @Override
        public Logger getLogger() {
            return this.logger;
        }

        @Override
        public Map<Integer, BiConsumer<WebSocket, JsonElement>> getHandlers() {
            return this.handlers;
        }

        @Override
        public Map<Integer, List<Consumer<JsonElement>>> getCallbacks() {
            return this.callbacks;
        }
    }
}
