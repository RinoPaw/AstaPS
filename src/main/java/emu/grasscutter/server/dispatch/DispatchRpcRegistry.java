package emu.grasscutter.server.dispatch;

import com.google.gson.JsonElement;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

final class DispatchRpcRegistry {
    static final long DEFAULT_TIMEOUT_MILLIS = 5000L;

    private static final ScheduledExecutorService TIMEOUTS =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        var thread = new Thread(runnable, "dispatch-rpc-timeout");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final ConcurrentMap<PendingKey, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextRequestId;
    private final long timeoutMillis;
    private final ThreadLocal<Armed> armed = new ThreadLocal<>();
    private final ThreadLocal<Incoming> incoming = new ThreadLocal<>();

    DispatchRpcRegistry() {
        this(DEFAULT_TIMEOUT_MILLIS, ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE - 1));
    }

    DispatchRpcRegistry(long timeoutMillis, long initialRequestId) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be positive");
        this.timeoutMillis = timeoutMillis;
        this.nextRequestId = new AtomicLong(initialRequestId);
    }

    void arm(
            int expectedResponseId,
            Consumer<JsonElement> success,
            Consumer<Throwable> failure) {
        if (this.armed.get() != null) {
            throw new IllegalStateException("A dispatch RPC callback is already armed on this thread");
        }
        this.armed.set(new Armed(new PendingGroup(expectedResponseId, success, failure)));
    }

    Armed takeArmed() {
        var value = this.armed.get();
        this.armed.remove();
        return value;
    }

    long register(Object connection, Armed armedRequest) {
        var group = armedRequest.group;
        long requestId = this.nextRequestId.updateAndGet(value -> value == Long.MAX_VALUE ? 1 : value + 1);
        var key = new PendingKey(connection, requestId);
        group.keys.add(key);
        this.pending.put(key, new Pending(group));

        if (group.timeoutScheduled.compareAndSet(false, true)) {
            TIMEOUTS.schedule(
                    () -> this.failGroup(group, new TimeoutException("Dispatch RPC timed out")),
                    this.timeoutMillis,
                    TimeUnit.MILLISECONDS);
        }
        return requestId;
    }

    void failArmed(Armed armedRequest, Throwable cause) {
        this.failGroup(armedRequest.group, cause);
    }

    boolean complete(Object connection, long requestId, int packetId, JsonElement packet) {
        var pendingRequest = this.pending.get(new PendingKey(connection, requestId));
        if (pendingRequest == null || pendingRequest.group.expectedResponseId != packetId) return false;

        var group = pendingRequest.group;
        if (!group.done.compareAndSet(false, true)) return false;
        this.removeGroup(group);
        group.success.accept(packet);
        return true;
    }

    void fail(Object connection, long requestId, Throwable cause) {
        var key = new PendingKey(connection, requestId);
        var pendingRequest = this.pending.remove(key);
        if (pendingRequest == null) return;

        var group = pendingRequest.group;
        group.keys.remove(key);
        if (group.keys.isEmpty()) this.failGroup(group, cause);
    }

    void failConnection(Object connection, Throwable cause) {
        for (var entry : this.pending.entrySet()) {
            var key = entry.getKey();
            if (key.connection != connection) continue;

            if (this.pending.remove(key, entry.getValue())) {
                var group = entry.getValue().group;
                group.keys.remove(key);
                if (group.keys.isEmpty()) this.failGroup(group, cause);
            }
        }
    }

    void enterIncoming(Object connection, long requestId) {
        this.incoming.set(new Incoming(connection, requestId));
    }

    void leaveIncoming() {
        this.incoming.remove();
    }

    Long currentRequestId(Object connection) {
        var context = this.incoming.get();
        return context != null && context.connection == connection ? context.requestId : null;
    }

    int pendingCount() {
        return this.pending.size();
    }

    private void failGroup(PendingGroup group, Throwable cause) {
        if (!group.done.compareAndSet(false, true)) return;
        this.removeGroup(group);
        group.failure.accept(cause);
    }

    private void removeGroup(PendingGroup group) {
        for (var key : Set.copyOf(group.keys)) {
            this.pending.remove(key);
            group.keys.remove(key);
        }
    }

    static final class Armed {
        private final PendingGroup group;

        private Armed(PendingGroup group) {
            this.group = group;
        }
    }

    private static final class PendingGroup {
        private final int expectedResponseId;
        private final Consumer<JsonElement> success;
        private final Consumer<Throwable> failure;
        private final Set<PendingKey> keys = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean done = new AtomicBoolean();
        private final AtomicBoolean timeoutScheduled = new AtomicBoolean();

        private PendingGroup(
                int expectedResponseId,
                Consumer<JsonElement> success,
                Consumer<Throwable> failure) {
            this.expectedResponseId = expectedResponseId;
            this.success = success;
            this.failure = failure;
        }
    }

    private static final class Pending {
        private final PendingGroup group;

        private Pending(PendingGroup group) {
            this.group = group;
        }
    }

    private static final class PendingKey {
        private final Object connection;
        private final long requestId;

        private PendingKey(Object connection, long requestId) {
            this.connection = connection;
            this.requestId = requestId;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof PendingKey other
                    && this.connection == other.connection
                    && this.requestId == other.requestId;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.connection) + Long.hashCode(this.requestId);
        }
    }

    private static final class Incoming {
        private final Object connection;
        private final long requestId;

        private Incoming(Object connection, long requestId) {
            this.connection = connection;
            this.requestId = requestId;
        }
    }
}
