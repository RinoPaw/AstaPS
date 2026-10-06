package emu.grasscutter.database;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class DatabaseExecutorSupport {
    private DatabaseExecutorSupport() {}

    private static final RejectedExecutionHandler CALLER_RUNS_UNLESS_SHUTDOWN =
            (task, executor) -> {
                if (executor.isShutdown()) {
                    throw new RejectedExecutionException("Database executor is shut down");
                }
                task.run();
            };

    static RejectedExecutionHandler callerRunsUnlessShutdown() {
        return CALLER_RUNS_UNLESS_SHUTDOWN;
    }

    static boolean isOverloaded(ThreadPoolExecutor executor) {
        var queue = executor.getQueue();
        long queued = queue.size();
        long capacity = queued + queue.remainingCapacity();
        return capacity > 0 && queued * 10 > capacity * 7;
    }

    record Pool(String name, ThreadPoolExecutor executor) {}

    record UnfinishedPool(String name, int active, int queued, long outstanding) {}

    record ShutdownResult(boolean interrupted, List<UnfinishedPool> unfinishedPools) {}

    static ShutdownResult shutdownAndAwait(List<Pool> pools, long timeout, TimeUnit unit) {
        if (timeout < 0) throw new IllegalArgumentException("timeout must not be negative");
        long timeoutNanos = unit.toNanos(timeout);
        var executors = List.copyOf(pools);
        long startedAt = System.nanoTime();
        boolean interrupted = false;

        for (var pool : executors) pool.executor().shutdown();

        try {
            for (var pool : executors) {
                if (pool.executor().isTerminated()) continue;
                long remaining = timeoutNanos - (System.nanoTime() - startedAt);
                if (remaining <= 0) break;
                pool.executor().awaitTermination(remaining, TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            Thread.currentThread().interrupt();
        }

        var unfinished = new ArrayList<UnfinishedPool>();
        for (var pool : executors) {
            var executor = pool.executor();
            if (executor.isTerminated()) continue;
            unfinished.add(
                    new UnfinishedPool(
                            pool.name(),
                            executor.getActiveCount(),
                            executor.getQueue().size(),
                            Math.max(0, executor.getTaskCount() - executor.getCompletedTaskCount())));
        }
        return new ShutdownResult(interrupted, List.copyOf(unfinished));
    }
}
