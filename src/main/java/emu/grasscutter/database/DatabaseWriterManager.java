package emu.grasscutter.database;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Owns admission and bounded shutdown for every asynchronous database writer. */
public final class DatabaseWriterManager {
    public record PoolShutdownStatus(
            String name, boolean terminated, int activeAtTimeout, int notStartedTasks) {}

    public record ShutdownResult(
            boolean completed, boolean interrupted, List<PoolShutdownStatus> pools) {
        public int activeAtTimeoutTaskCount() {
            return pools.stream().mapToInt(PoolShutdownStatus::activeAtTimeout).sum();
        }

        public int notStartedTaskCount() {
            return pools.stream().mapToInt(PoolShutdownStatus::notStartedTasks).sum();
        }
    }

    private final LinkedHashMap<String, ExecutorService> pools;
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Condition callerRunsDrained = lifecycle.newCondition();
    private final LinkedHashMap<ExecutorService, Integer> callerRunsActive = new LinkedHashMap<>();
    private boolean accepting = true;
    private ShutdownResult shutdownResult;

    DatabaseWriterManager(Map<String, ? extends ExecutorService> pools) {
        if (pools.isEmpty()) {
            throw new IllegalArgumentException("At least one database writer is required");
        }
        this.pools = new LinkedHashMap<>(pools);
        for (var executor : this.pools.values()) callerRunsActive.put(executor, 0);
    }

    /**
     * Submits through one admission gate so shutdown has a precise cut-over point.
     *
     * <p>The executors use AbortPolicy. A rejection while admission is open therefore means queue
     * saturation and preserves the old caller-runs backpressure. Caller-run work is counted, but it
     * does not hold the lifecycle lock while doing database I/O; shutdown can still enforce its
     * deadline if that I/O is stuck.
     */
    void submit(ExecutorService executor, Runnable task) {
        boolean runOnCaller = false;
        lifecycle.lock();
        try {
            if (!accepting) {
                throw new RejectedExecutionException("Database writers are shutting down");
            }
            try {
                executor.execute(task);
                return;
            } catch (RejectedExecutionException rejected) {
                if (executor.isShutdown()) {
                    throw new RejectedExecutionException("Database writer is shutting down", rejected);
                }
                callerRunsActive.compute(executor, (ignored, count) -> count == null ? 1 : count + 1);
                runOnCaller = true;
            }
        } finally {
            lifecycle.unlock();
        }

        if (!runOnCaller) return;
        try {
            task.run();
        } finally {
            lifecycle.lock();
            try {
                callerRunsActive.computeIfPresent(executor, (ignored, count) -> Math.max(0, count - 1));
                callerRunsDrained.signalAll();
            } finally {
                lifecycle.unlock();
            }
        }
    }

    /** Stops admission, drains every writer against one deadline, then reports any forced stop. */
    public synchronized ShutdownResult shutdown(Duration timeout) {
        if (shutdownResult != null) return shutdownResult;
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Shutdown timeout must not be negative");
        }

        lifecycle.lock();
        try {
            accepting = false;
            // Stop all four at once. Waiting starts only after every writer has entered drain mode.
            pools.values().forEach(ExecutorService::shutdown);
        } finally {
            lifecycle.unlock();
        }

        long deadline = saturatingAdd(System.nanoTime(), timeout.toNanos());
        boolean interrupted = false;
        boolean timedOut = false;

        for (var executor : pools.values()) {
            if (executor.isTerminated()) continue;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                timedOut = true;
                break;
            }
            try {
                if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    timedOut = true;
                    break;
                }
            } catch (InterruptedException e) {
                interrupted = true;
                timedOut = true;
                break;
            }
        }

        if (!timedOut && !interrupted) {
            lifecycle.lock();
            try {
                while (callerRunsActive.values().stream().anyMatch(count -> count > 0)) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        timedOut = true;
                        break;
                    }
                    try {
                        callerRunsDrained.awaitNanos(remaining);
                    } catch (InterruptedException e) {
                        interrupted = true;
                        timedOut = true;
                        break;
                    }
                }
            } finally {
                lifecycle.unlock();
            }
        }

        var statuses = new ArrayList<PoolShutdownStatus>(pools.size());
        for (var entry : pools.entrySet()) {
            var executor = entry.getValue();
            int activeAtTimeout = callerRunCount(executor);
            int notStarted = 0;
            if (!executor.isTerminated()) {
                if (executor instanceof ThreadPoolExecutor pool) {
                    activeAtTimeout += pool.getActiveCount();
                }
                List<Runnable> queued = executor.shutdownNow();
                notStarted = queued.size();
            }
            statuses.add(
                    new PoolShutdownStatus(
                            entry.getKey(), executor.isTerminated(), activeAtTimeout, notStarted));
        }

        if (interrupted) Thread.currentThread().interrupt();
        boolean completed =
                !timedOut
                        && !interrupted
                        && statuses.stream().allMatch(PoolShutdownStatus::terminated)
                        && statuses.stream().allMatch(status -> status.activeAtTimeout() == 0);
        shutdownResult = new ShutdownResult(completed, interrupted, List.copyOf(statuses));
        return shutdownResult;
    }

    private int callerRunCount(ExecutorService executor) {
        lifecycle.lock();
        try {
            return callerRunsActive.getOrDefault(executor, 0);
        } finally {
            lifecycle.unlock();
        }
    }

    private static long saturatingAdd(long left, long right) {
        long sum = left + right;
        if (((left ^ sum) & (right ^ sum)) < 0) return Long.MAX_VALUE;
        return sum;
    }
}
