package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import emu.grasscutter.utils.DispatchUtils;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class IDispatcherCorrelationRegressionTest {
    private static final int RESPONSE_ID = 7002;

    @Test
    void sameTypeRequestsStayDistinctWhenResponsesArriveOutOfOrder() {
        var registry = new DispatchRpcRegistry(5000, 100);
        var connection = new Object();
        var resultA = new AtomicReference<String>();
        var resultB = new AtomicReference<String>();

        long requestA = register(registry, connection, resultA, new AtomicReference<>());
        long requestB = register(registry, connection, resultB, new AtomicReference<>());

        assertTrue(registry.complete(connection, requestB, RESPONSE_ID, response("B", true)));
        assertNull(resultA.get());
        assertEquals("B", resultB.get());

        assertTrue(registry.complete(connection, requestA, RESPONSE_ID, response("A", true)));
        assertEquals("A", resultA.get());
    }

    @Test
    void successAndFailureForSameTypeRequestsDoNotCross() {
        var registry = new DispatchRpcRegistry(5000, 200);
        var connection = new Object();
        var resultA = new AtomicReference<Boolean>();
        var resultB = new AtomicReference<Boolean>();

        long requestA = registerBoolean(registry, connection, resultA);
        long requestB = registerBoolean(registry, connection, resultB);

        assertTrue(registry.complete(connection, requestA, RESPONSE_ID, response("A", true)));
        assertTrue(resultA.get());
        assertNull(resultB.get());

        assertTrue(registry.complete(connection, requestB, RESPONSE_ID, response("B", false)));
        assertFalse(resultB.get());
    }

    @Test
    void timeoutRemovesPendingRequest() throws Exception {
        var registry = new DispatchRpcRegistry(20, 300);
        var connection = new Object();
        var failed = new CountDownLatch(1);

        registry.arm(RESPONSE_ID, ignored -> fail("unexpected success"), ignored -> failed.countDown());
        registry.register(connection, registry.takeArmed());

        assertTrue(failed.await(1, TimeUnit.SECONDS));
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void lateResponseAfterTimeoutCannotCompleteLaterRequest() throws Exception {
        var registry = new DispatchRpcRegistry(20, 400);
        var connection = new Object();
        var timedOut = new CountDownLatch(1);

        registry.arm(RESPONSE_ID, ignored -> fail("unexpected success"), ignored -> timedOut.countDown());
        long oldRequest = registry.register(connection, registry.takeArmed());
        assertTrue(timedOut.await(1, TimeUnit.SECONDS));

        var later = new AtomicReference<String>();
        long laterRequest = register(registry, connection, later, new AtomicReference<>());

        assertFalse(registry.complete(connection, oldRequest, RESPONSE_ID, response("old", true)));
        assertNull(later.get());
        assertTrue(registry.complete(connection, laterRequest, RESPONSE_ID, response("new", true)));
        assertEquals("new", later.get());
    }

    @Test
    void disconnectFailsAndClearsEveryPendingRequestForThatConnection() {
        var registry = new DispatchRpcRegistry(5000, 500);
        var connection = new Object();
        var failures = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            registry.arm(RESPONSE_ID, ignored -> fail("unexpected success"), ignored -> failures.incrementAndGet());
            registry.register(connection, registry.takeArmed());
        }

        registry.failConnection(connection, new IllegalStateException("disconnected"));

        assertEquals(2, failures.get());
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void sameRequestIdOnDifferentServersCannotCrossComplete() {
        var serverA = new DispatchRpcRegistry(5000, 600);
        var serverB = new DispatchRpcRegistry(5000, 600);
        var connectionA = new Object();
        var connectionB = new Object();
        var resultA = new AtomicReference<String>();
        var resultB = new AtomicReference<String>();

        long idA = register(serverA, connectionA, resultA, new AtomicReference<>());
        long idB = register(serverB, connectionB, resultB, new AtomicReference<>());
        assertEquals(idA, idB, "test requires identical request IDs on separate servers");

        assertTrue(serverA.complete(connectionA, idA, RESPONSE_ID, response("A", true)));
        assertEquals("A", resultA.get());
        assertNull(resultB.get());
        assertEquals(1, serverB.pendingCount());
    }

    @Test
    void callbackCompletesExactlyOnceForDuplicateResponse() {
        var registry = new DispatchRpcRegistry(5000, 700);
        var connection = new Object();
        var completions = new AtomicInteger();

        registry.arm(RESPONSE_ID, ignored -> completions.incrementAndGet(), ignored -> fail("unexpected failure"));
        long requestId = registry.register(connection, registry.takeArmed());

        assertTrue(registry.complete(connection, requestId, RESPONSE_ID, response("first", true)));
        assertFalse(registry.complete(connection, requestId, RESPONSE_ID, response("duplicate", true)));
        assertEquals(1, completions.get());
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void authenticatedAccountMustMatchRequestedAccount() {
        assertTrue(DispatchUtils.matchesAuthenticatedAccount("1001", "1001"));
        assertFalse(DispatchUtils.matchesAuthenticatedAccount("1001", "1002"));
        assertFalse(DispatchUtils.matchesAuthenticatedAccount(null, "1001"));
    }

    private static long register(
            DispatchRpcRegistry registry,
            Object connection,
            AtomicReference<String> result,
            AtomicReference<Throwable> failure) {
        registry.arm(
                RESPONSE_ID,
                packet -> result.set(packet.getAsJsonObject().get("name").getAsString()),
                failure::set);
        return registry.register(connection, registry.takeArmed());
    }

    private static long registerBoolean(
            DispatchRpcRegistry registry, Object connection, AtomicReference<Boolean> result) {
        registry.arm(
                RESPONSE_ID,
                packet -> result.set(packet.getAsJsonObject().get("ok").getAsBoolean()),
                ignored -> fail("unexpected failure"));
        return registry.register(connection, registry.takeArmed());
    }

    private static JsonObject response(String name, boolean ok) {
        var response = new JsonObject();
        response.addProperty("name", name);
        response.addProperty("ok", ok);
        return response;
    }
}
