package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class DatabaseWriteConsistencyTest {
    private static final Duration DEADLINE = Duration.ofSeconds(2);

    @Test
    void equalKeysAcrossPoolsStayOrderedWithoutOccupyingTheSuccessorWorker() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var secondStarted = new CountDownLatch(1);
            var unrelated = new CountDownLatch(1);
            var writes = Collections.synchronizedList(new ArrayList<String>());
            try {
                fixture.manager.submitOrdered(
                        fixture.item,
                        new String("item-id"),
                        () -> {
                            started.countDown();
                            await(release);
                            writes.add("save");
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                fixture.manager.submitOrdered(
                        fixture.other,
                        new String("item-id"),
                        () -> {
                            writes.add("delete");
                            secondStarted.countDown();
                        });
                fixture.manager.submitOrdered(fixture.other, "other-item", unrelated::countDown);
                assertTrue(unrelated.await(2, TimeUnit.SECONDS));
                assertEquals(1, secondStarted.getCount(), "delete must not pass the pending save");
                release.countDown();
                assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
                assertEquals(List.of("save", "delete"), writes);
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {
                    assertEquals(List.of("save", "delete"), writes);
                }
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void exceptionReleasesTheKeyAndDispatchesItsSuccessor() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var successor = new CountDownLatch(1);
            try {
                fixture.manager.submitOrdered(
                        fixture.item,
                        "item",
                        () -> {
                            started.countDown();
                            await(release);
                            throw new IllegalStateException("intentional writer failure");
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                fixture.manager.submitOrdered(fixture.other, "item", successor::countDown);
                release.countDown();
                assertTrue(successor.await(2, TimeUnit.SECONDS));
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {}
                var reused = new CountDownLatch(1);
                fixture.manager.submitOrdered(fixture.item, "item", reused::countDown);
                assertTrue(reused.await(2, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void orderedBacklogHasABoundAndRejectsWithoutWaitingOnTheActiveWriter() throws Exception {
        try (var fixture = new Fixture(2)) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var successor = new CountDownLatch(1);
            try {
                fixture.manager.submitOrdered(
                        fixture.item,
                        "item",
                        () -> {
                            started.countDown();
                            await(release);
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                fixture.manager.submitOrdered(fixture.other, "item", successor::countDown);
                assertThrows(
                        RejectedExecutionException.class,
                        () ->
                                fixture.manager.submitOrdered(
                                        fixture.item, "item", () -> fail("rejected write ran")));
                release.countDown();
                assertTrue(successor.await(2, TimeUnit.SECONDS));
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {}
                fixture.manager.submitOrdered(fixture.item, "item", () -> {});
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void externallyStoppedPoolCancelsItsOrderedHeadsAndDispatchesTheLiveSuccessor() throws Exception {
        try (var fixture = new Fixture(2048)) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var successor = new CountDownLatch(1);
            var cancelledMutations = new AtomicInteger();
            try {
                fixture.manager.submitOrdered(
                        fixture.item,
                        "item",
                        () -> {
                            started.countDown();
                            await(release);
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                for (int i = 0; i < 1500; i++) {
                    fixture.manager.submitOrdered(fixture.item, "item", cancelledMutations::incrementAndGet);
                }
                fixture.manager.submitOrdered(fixture.other, "item", successor::countDown);
                fixture.item.shutdown();
                release.countDown();
                assertTrue(successor.await(2, TimeUnit.SECONDS));
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {}
                assertEquals(0, cancelledMutations.get());
                var reused = new CountDownLatch(1);
                fixture.manager.submitOrdered(fixture.other, "item", reused::countDown);
                assertTrue(reused.await(2, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void saturatedOrderedChainDrainsWhenEveryOperationThrowsTheSameException() throws Exception {
        try (var fixture = new Fixture(2048)) {
            var workerStarted = new CountDownLatch(1);
            var releaseWorker = new CountDownLatch(1);
            var callerStarted = new CountDownLatch(1);
            var releaseCaller = new CountDownLatch(1);
            var mutations = new AtomicInteger();
            var sharedFailure = new IllegalStateException("shared writer failure");
            try {
                fixture.manager.submit(
                        fixture.item,
                        () -> {
                            workerStarted.countDown();
                            await(releaseWorker);
                        });
                assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
                fixture.manager.submit(fixture.item, () -> {});
                var caller =
                        fixture.threads.submit(
                                () ->
                                        fixture.manager.submitOrdered(
                                                fixture.item,
                                                "item",
                                                () -> {
                                                    callerStarted.countDown();
                                                    await(releaseCaller);
                                                    mutations.incrementAndGet();
                                                    throw sharedFailure;
                                                }));
                assertTrue(callerStarted.await(2, TimeUnit.SECONDS));
                for (int i = 0; i < 1500; i++) {
                    fixture.manager.submitOrdered(
                            fixture.item,
                            "item",
                            () -> {
                                mutations.incrementAndGet();
                                throw sharedFailure;
                            });
                }
                releaseCaller.countDown();
                var failure = assertThrows(ExecutionException.class, () -> caller.get(2, TimeUnit.SECONDS));
                assertSame(sharedFailure, failure.getCause());
                assertEquals(1501, mutations.get());
                releaseWorker.countDown();
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {}
            } finally {
                releaseCaller.countDown();
                releaseWorker.countDown();
            }
        }
    }

    @Test
    void saturatedCallerRunAndItsOrderedSuccessorBothDrainBeforeTheBarrier() throws Exception {
        try (var fixture = new Fixture()) {
            var workerStarted = new CountDownLatch(1);
            var releaseWorker = new CountDownLatch(1);
            var callerStarted = new CountDownLatch(1);
            var releaseCaller = new CountDownLatch(1);
            var successor = new CountDownLatch(1);
            var barrierAcquired = new CountDownLatch(1);
            try {
                fixture.manager.submit(
                        fixture.item,
                        () -> {
                            workerStarted.countDown();
                            await(releaseWorker);
                        });
                assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
                fixture.manager.submit(fixture.item, () -> {});
                var callerThread = new AtomicReference<Thread>();
                var actualWriter = new AtomicReference<Thread>();
                var caller =
                        fixture.threads.submit(
                                () -> {
                                    callerThread.set(Thread.currentThread());
                                    fixture.manager.submitOrdered(
                                            fixture.item,
                                            "item",
                                            () -> {
                                                actualWriter.set(Thread.currentThread());
                                                callerStarted.countDown();
                                                await(releaseCaller);
                                            });
                                });
                assertTrue(callerStarted.await(2, TimeUnit.SECONDS));
                assertSame(callerThread.get(), actualWriter.get());
                fixture.manager.submitOrdered(fixture.other, "item", successor::countDown);
                var barrier =
                        fixture.threads.submit(
                                () -> {
                                    try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {
                                        barrierAcquired.countDown();
                                        assertEquals(0, successor.getCount());
                                    }
                                });
                awaitClosedAdmission(fixture.manager);
                assertEquals(1, barrierAcquired.getCount());
                assertThrows(
                        RejectedExecutionException.class,
                        () ->
                                fixture.manager.submit(fixture.other, () -> fail("new write crossed the barrier")));
                releaseCaller.countDown();
                caller.get(2, TimeUnit.SECONDS);
                assertTrue(successor.await(2, TimeUnit.SECONDS));
                assertEquals(1, barrierAcquired.getCount(), "the earlier worker still blocks the barrier");
                releaseWorker.countDown();
                barrier.get(2, TimeUnit.SECONDS);
                assertEquals(0, barrierAcquired.getCount());
            } finally {
                releaseCaller.countDown();
                releaseWorker.countDown();
            }
        }
    }

    @Test
    void barrierOwnerCanWriteRecursivelyButExternalWritesAreRejectedAndNeverDeferred()
            throws Exception {
        try (var fixture = new Fixture()) {
            var mutations = new AtomicInteger();
            try (var outer = fixture.manager.acquireBarrier(DEADLINE)) {
                try (var inner = fixture.manager.acquireBarrier(DEADLINE)) {
                    assertEquals(
                            7, fixture.manager.callSynchronous(() -> fixture.manager.callSynchronous(() -> 7)));
                    fixture.manager.runSynchronous(mutations::incrementAndGet);
                    assertThrows(IllegalStateException.class, () -> fixture.manager.shutdown(DEADLINE));
                }
                var outsider =
                        fixture.threads.submit(
                                () -> {
                                    assertThrows(
                                            RejectedExecutionException.class,
                                            () -> fixture.manager.submit(fixture.item, mutations::incrementAndGet));
                                    assertThrows(
                                            RejectedExecutionException.class,
                                            () ->
                                                    fixture.manager.submitOrdered(
                                                            fixture.item, "item", mutations::incrementAndGet));
                                    assertThrows(
                                            RejectedExecutionException.class,
                                            () -> fixture.manager.runSynchronous(mutations::incrementAndGet));
                                });
                outsider.get(2, TimeUnit.SECONDS);
                assertThrows(
                        RejectedExecutionException.class,
                        () -> fixture.manager.submit(fixture.item, mutations::incrementAndGet));
            }
            try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {
                assertEquals(1, mutations.get(), "rejected writes must not run after close");
            }
            fixture.manager.runSynchronous(mutations::incrementAndGet);
            assertEquals(2, mutations.get());
        }
    }

    @Test
    void admittedWriterCanFinishNestedSynchronousWritesWhileTheBarrierIsPending() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var nestedRelease = new CountDownLatch(1);
            var nestedWrites = new AtomicInteger();
            try {
                fixture.manager.submit(
                        fixture.item,
                        () -> {
                            started.countDown();
                            await(nestedRelease);
                            fixture.manager.runSynchronous(
                                    () -> fixture.manager.runSynchronous(nestedWrites::incrementAndGet));
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                var barrier =
                        fixture.threads.submit(
                                () -> {
                                    try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {
                                        assertEquals(1, nestedWrites.get());
                                    }
                                });
                awaitClosedAdmission(fixture.manager);
                nestedRelease.countDown();
                barrier.get(2, TimeUnit.SECONDS);
                assertEquals(1, nestedWrites.get());
            } finally {
                nestedRelease.countDown();
            }
        }
    }

    @Test
    void barrierAlsoWaitsForAnExternalSynchronousWrite() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try {
                var writer =
                        fixture.threads.submit(
                                () ->
                                        fixture.manager.runSynchronous(
                                                () -> {
                                                    started.countDown();
                                                    await(release);
                                                }));
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertThrows(
                        IllegalStateException.class,
                        () -> fixture.manager.acquireBarrier(Duration.ofMillis(25)));
                fixture.manager.runSynchronous(() -> {});
                release.countDown();
                writer.get(2, TimeUnit.SECONDS);
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {}
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void failedDrainReleasesTheAdmissionGate() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try {
                fixture.manager.submit(
                        fixture.item,
                        () -> {
                            started.countDown();
                            await(release);
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertThrows(
                        IllegalStateException.class,
                        () -> fixture.manager.acquireBarrier(Duration.ofMillis(25)));
                assertEquals(42, fixture.manager.callSynchronous(() -> 42));
                release.countDown();
                try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {}
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void interruptedDrainReleasesTheGateAndPreservesTheInterrupt() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var barrierThread = new AtomicReference<Thread>();
            try {
                fixture.manager.submit(
                        fixture.item,
                        () -> {
                            started.countDown();
                            await(release);
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                var barrier =
                        fixture.threads.submit(
                                () -> {
                                    barrierThread.set(Thread.currentThread());
                                    assertThrows(
                                            IllegalStateException.class, () -> fixture.manager.acquireBarrier(DEADLINE));
                                    return Thread.currentThread().isInterrupted();
                                });
                awaitClosedAdmission(fixture.manager);
                barrierThread.get().interrupt();
                assertTrue(barrier.get(2, TimeUnit.SECONDS));
                fixture.manager.runSynchronous(() -> {});
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void barrierAndShutdownDeadlinesIncludeAdmissionLockAcquisition() throws Exception {
        try (var fixture = new Fixture()) {
            var field = DatabaseWriterManager.class.getDeclaredField("lifecycle");
            field.setAccessible(true);
            var lock = (ReentrantLock) field.get(fixture.manager);
            lock.lock();
            try {
                fixture
                        .threads
                        .submit(
                                () ->
                                        assertThrows(
                                                IllegalStateException.class,
                                                () -> fixture.manager.acquireBarrier(Duration.ofMillis(25))))
                        .get(1, TimeUnit.SECONDS);
                var failed =
                        fixture
                                .threads
                                .submit(() -> fixture.manager.shutdown(Duration.ofMillis(25)))
                                .get(1, TimeUnit.SECONDS);
                assertFalse(failed.completed());
            } finally {
                lock.unlock();
            }
            fixture.manager.runSynchronous(() -> {});
            assertTrue(fixture.manager.shutdown(DEADLINE).completed());
        }
    }

    @Test
    void writerCannotAcquireABarrierOrShutdownItsOwnPermit() {
        try (var fixture = new Fixture()) {
            fixture.manager.runSynchronous(
                    () -> {
                        assertThrows(
                                IllegalStateException.class, () -> fixture.manager.acquireBarrier(DEADLINE));
                        assertThrows(IllegalStateException.class, () -> fixture.manager.shutdown(DEADLINE));
                    });
            assertTrue(fixture.manager.shutdown(DEADLINE).completed());
        }
    }

    @Test
    void heldMaintenanceOperationIsIncludedInShutdownAndCloseCannotReopenAdmission()
            throws Exception {
        try (var fixture = new Fixture()) {
            var held = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try {
                var maintenance =
                        fixture.threads.submit(
                                () -> {
                                    try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {
                                        fixture.manager.runSynchronous(() -> {});
                                        held.countDown();
                                        await(release);
                                    }
                                });
                assertTrue(held.await(2, TimeUnit.SECONDS));
                var result = fixture.manager.shutdown(Duration.ofMillis(25));
                assertFalse(result.completed());
                assertTrue(
                        result.pools().stream()
                                .anyMatch(
                                        status ->
                                                status.name().equals("maintenance") && status.activeAtTimeout() == 1));
                release.countDown();
                maintenance.get(2, TimeUnit.SECONDS);
                assertThrows(
                        RejectedExecutionException.class,
                        () -> fixture.manager.runSynchronous(() -> fail("shutdown gate reopened")));
                assertThrows(
                        RejectedExecutionException.class,
                        () -> fixture.manager.submit(fixture.item, () -> fail("shutdown gate reopened")));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void gracefulShutdownWaitsForBarrierCloseBeforeReportingCompleted() throws Exception {
        try (var fixture = new Fixture()) {
            var held = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try {
                var maintenance =
                        fixture.threads.submit(
                                () -> {
                                    try (var ignored = fixture.manager.acquireBarrier(DEADLINE)) {
                                        held.countDown();
                                        await(release);
                                    }
                                });
                assertTrue(held.await(2, TimeUnit.SECONDS));
                var shutdown = fixture.threads.submit(() -> fixture.manager.shutdown(DEADLINE));
                awaitShutdownAdmission(fixture.manager);
                assertFalse(shutdown.isDone());
                release.countDown();
                maintenance.get(2, TimeUnit.SECONDS);
                assertTrue(shutdown.get(2, TimeUnit.SECONDS).completed());
                assertThrows(
                        RejectedExecutionException.class, () -> fixture.manager.submit(fixture.item, () -> {}));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void preInterruptedShutdownClosesAdmissionAndReportsPendingWrites() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var cancelledMutations = new AtomicInteger();
            var interruptPreserved = new AtomicBoolean();
            try {
                fixture.manager.submitOrdered(
                        fixture.item,
                        "item",
                        () -> {
                            started.countDown();
                            await(release);
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                fixture.manager.submitOrdered(fixture.other, "item", cancelledMutations::incrementAndGet);
                var result =
                        fixture
                                .threads
                                .submit(
                                        () -> {
                                            Thread.currentThread().interrupt();
                                            var stopped = fixture.manager.shutdown(DEADLINE);
                                            interruptPreserved.set(Thread.currentThread().isInterrupted());
                                            return stopped;
                                        })
                                .get(2, TimeUnit.SECONDS);
                assertFalse(result.completed());
                assertTrue(result.interrupted());
                assertTrue(interruptPreserved.get());
                assertEquals(1, result.activeAtTimeoutTaskCount());
                assertEquals(1, result.notStartedTaskCount());
                assertTrue(fixture.pools.values().stream().allMatch(ExecutorService::isShutdown));
                assertThrows(
                        RejectedExecutionException.class,
                        () -> fixture.manager.submit(fixture.other, () -> {}));
                assertThrows(
                        RejectedExecutionException.class, () -> fixture.manager.runSynchronous(() -> {}));
                release.countDown();
                assertTrue(fixture.item.awaitTermination(2, TimeUnit.SECONDS));
                assertEquals(0, cancelledMutations.get());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void shutdownReportsAndCancelsOrderedWritesNotYetDispatched() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var laterMutations = new AtomicInteger();
            try {
                fixture.manager.submitOrdered(
                        fixture.item,
                        "item",
                        () -> {
                            started.countDown();
                            await(release);
                        });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                fixture.manager.submitOrdered(fixture.other, "item", laterMutations::incrementAndGet);
                var result = fixture.manager.shutdown(Duration.ofMillis(25));
                assertFalse(result.completed());
                assertEquals(1, result.notStartedTaskCount());
                assertEquals(0, laterMutations.get());
                release.countDown();
                assertTrue(fixture.item.awaitTermination(2, TimeUnit.SECONDS));
                assertEquals(0, laterMutations.get());
            } finally {
                release.countDown();
            }
        }
    }

    private static void awaitClosedAdmission(DatabaseWriterManager manager) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (true) {
            try {
                manager.runSynchronous(() -> {});
            } catch (RejectedExecutionException closed) {
                return;
            }
            if (System.nanoTime() >= deadline) fail("barrier did not close admission");
            Thread.yield();
        }
    }

    private static void awaitShutdownAdmission(DatabaseWriterManager manager) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (true) {
            try {
                manager.runSynchronous(() -> {});
            } catch (RejectedExecutionException closed) {
                if (closed.getMessage().contains("shutting down")) return;
            }
            if (System.nanoTime() >= deadline) fail("shutdown did not close admission");
            Thread.yield();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ThreadPoolExecutor item = pool();
        final ThreadPoolExecutor other = pool();
        final LinkedHashMap<String, ExecutorService> pools = new LinkedHashMap<>();
        final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
        final DatabaseWriterManager manager;

        Fixture() {
            this(32);
        }

        Fixture(int orderedCapacity) {
            pools.put("item", item);
            pools.put("default", other);
            manager = new DatabaseWriterManager(pools, orderedCapacity);
        }

        private static ThreadPoolExecutor pool() {
            var pool =
                    new ThreadPoolExecutor(
                            1,
                            1,
                            0,
                            TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(1),
                            new ThreadPoolExecutor.AbortPolicy());
            pool.setThreadFactory(
                    runnable -> {
                        var thread = new Thread(runnable);
                        thread.setUncaughtExceptionHandler((ignored, failure) -> {});
                        return thread;
                    });
            return pool;
        }

        @Override
        public void close() {
            threads.shutdownNow();
            manager.shutdown(Duration.ofSeconds(1));
            item.shutdownNow();
            other.shutdownNow();
        }
    }
}
