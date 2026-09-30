package emu.grasscutter.server.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the scheduler semantics that used to depend on its 2022 one-second game loop. */
public final class ServerTaskSchedulerTest {
    private ExecutorService executor;
    private ServerTaskScheduler scheduler;

    private void createScheduler() {
        this.executor = Executors.newSingleThreadExecutor();
        this.scheduler = new ServerTaskScheduler(this.executor);
    }

    @AfterEach
    public void shutdownExecutor() {
        if (this.executor != null) this.executor.shutdownNow();
    }

    @Test
    @DisplayName("a delayed repeating task waits a full period after its first run")
    public void delayedRepeatingCadence() {
        var task = new ServerTask(() -> {}, 1, 3, 5);
        var runs = new ArrayList<Integer>();

        for (int i = 0; i < 11; i++) {
            if (task.shouldRun()) runs.add(task.getTicks());
        }

        assertEquals(List.of(5, 8, 11), runs);
    }

    @Test
    @DisplayName("an async-only queue is serviced without a synchronous task keeping it alive")
    public void asyncOnlyTaskRuns() throws Exception {
        createScheduler();
        var ran = new CountDownLatch(1);
        int taskId = this.scheduler.scheduleAsyncTask(ran::countDown);
        AsyncServerTask task = this.scheduler.getAsyncTask(taskId);
        assertNotNull(task);

        this.scheduler.runTasks();

        assertTrue(ran.await(1, TimeUnit.SECONDS));
        waitFor(task::isFinished);
        this.scheduler.runTasks();
        assertNull(this.scheduler.getAsyncTask(taskId));
    }

    @Test
    @DisplayName("repeated scheduler ticks cannot submit the same async task twice")
    public void asyncTaskStartsOnce() throws Exception {
        createScheduler();
        var workerStarted = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        this.executor.submit(
                () -> {
                    workerStarted.countDown();
                    try {
                        releaseWorker.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertTrue(workerStarted.await(1, TimeUnit.SECONDS));

        var runs = new AtomicInteger();
        int taskId = this.scheduler.scheduleAsyncTask(runs::incrementAndGet);
        AsyncServerTask task = this.scheduler.getAsyncTask(taskId);
        assertNotNull(task);

        for (int i = 0; i < 20; i++) this.scheduler.runTasks();
        releaseWorker.countDown();

        waitFor(() -> runs.get() == 1);
        waitFor(task::isFinished);
        for (int i = 0; i < 5; i++) this.scheduler.runTasks();
        assertEquals(1, runs.get());
        assertNull(this.scheduler.getAsyncTask(taskId));
    }

    @Test
    @DisplayName("a failing async task still finishes, is removed, and runs its callback")
    public void failedAsyncTaskIsCleanedUp() throws Exception {
        createScheduler();
        var callback = new CountDownLatch(1);
        int taskId =
                this.scheduler.scheduleAsyncTask(
                        () -> {
                            throw new IllegalStateException("boom");
                        },
                        callback::countDown);
        AsyncServerTask task = this.scheduler.getAsyncTask(taskId);
        assertNotNull(task);

        this.scheduler.runTasks();
        waitFor(task::isFinished);
        assertInstanceOf(IllegalStateException.class, task.getFailure());

        this.scheduler.runTasks();
        assertEquals(0L, callback.getCount());
        assertNull(this.scheduler.getAsyncTask(taskId));
    }

    @Test
    @DisplayName("invalid tick periods are rejected when the task is scheduled")
    public void invalidPeriodsAreRejected() {
        createScheduler();

        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleRepeatingTask(() -> {}, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleDelayedRepeatingTask(() -> {}, -1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleDelayedTask(() -> {}, -1));
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertTrue(condition.getAsBoolean());
    }
}
