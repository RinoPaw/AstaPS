package emu.grasscutter.database;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Drains every asynchronous database writer and holds them at a common fence until closed.
 *
 * <p>Code inside the barrier sees a stable database snapshot: every write queued before acquisition
 * has completed, and later queued writes cannot pass the fence until the barrier is released.
 */
final class DatabaseWriteBarrier implements AutoCloseable {
    private static final long DATABASE_BARRIER_TIMEOUT_SECONDS = 30;

    private final List<PoolFence> fences;

    private DatabaseWriteBarrier(List<PoolFence> fences) {
        this.fences = fences;
    }

    static DatabaseWriteBarrier acquire() {
        var fences = new ArrayList<PoolFence>(4);
        try {
            fences.add(PoolFence.hold(asThreadPool(DatabaseHelper.getEventExecutor())));
            fences.add(PoolFence.hold(asThreadPool(DatabaseHelper.getEventExecutorAccount())));
            fences.add(PoolFence.hold(asThreadPool(DatabaseHelper.getEventExecutorItem())));
            fences.add(PoolFence.hold(asThreadPool(DatabaseHelper.getEventExecutorGroup())));

            for (PoolFence fence : fences) {
                fence.awaitReady();
            }
            return new DatabaseWriteBarrier(fences);
        } catch (InterruptedException e) {
            releaseAll(fences);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while draining database writes", e);
        } catch (RuntimeException | Error e) {
            releaseAll(fences);
            throw e;
        }
    }

    private static ThreadPoolExecutor asThreadPool(ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor pool) {
            return pool;
        }
        throw new IllegalStateException("Database executor is not a ThreadPoolExecutor");
    }

    private static void releaseAll(List<PoolFence> fences) {
        for (int i = fences.size() - 1; i >= 0; i--) {
            fences.get(i).release();
        }
    }

    @Override
    public void close() {
        releaseAll(this.fences);
    }

    private static final class PoolFence {
        private final ThreadPoolExecutor executor;
        private final int originalCorePoolSize;
        private final CountDownLatch ready;
        private final CountDownLatch release;

        private PoolFence(
                ThreadPoolExecutor executor,
                int originalCorePoolSize,
                CountDownLatch ready,
                CountDownLatch release) {
            this.executor = executor;
            this.originalCorePoolSize = originalCorePoolSize;
            this.ready = ready;
            this.release = release;
        }

        private static PoolFence hold(ThreadPoolExecutor executor) throws InterruptedException {
            int originalCorePoolSize = executor.getCorePoolSize();
            int workerCount = executor.getMaximumPoolSize();
            var ready = new CountDownLatch(workerCount);
            var release = new CountDownLatch(1);
            var fence = new PoolFence(executor, originalCorePoolSize, ready, release);

            try {
                // Make every possible worker participate in the fence. Once all fence tasks are
                // running, every task that was ahead of them in the FIFO queue has completed.
                executor.setCorePoolSize(workerCount);
                executor.prestartAllCoreThreads();

                Runnable fenceTask =
                        () -> {
                            ready.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        };

                for (int i = 0; i < workerCount; i++) {
                    executor.getQueue().put(fenceTask);
                }
                return fence;
            } catch (InterruptedException | RuntimeException | Error e) {
                fence.release();
                throw e;
            }
        }

        private void awaitReady() throws InterruptedException {
            if (!this.ready.await(DATABASE_BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out draining a database write pool");
            }
        }

        private void release() {
            this.release.countDown();
            this.executor.setCorePoolSize(this.originalCorePoolSize);
        }
    }
}
