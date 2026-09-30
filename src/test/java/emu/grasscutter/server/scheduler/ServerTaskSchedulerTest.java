package emu.grasscutter.server.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Pins the scheduler semantics that used to depend on its 2022 one-second game loop. */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public final class ServerTaskSchedulerTest {
    private ManualExecutor executor;
    private ServerTaskScheduler scheduler;

    private void createScheduler() {
        this.executor = new ManualExecutor();
        this.scheduler = new ServerTaskScheduler(this.executor);
    }

    private AtomicLong createSchedulerWithClock() {
        this.executor = new ManualExecutor();
        var now = new AtomicLong();
        this.scheduler = new ServerTaskScheduler(this.executor, now::get);
        return now;
    }

    @Test
    @DisplayName("an explicit tick task waits a full period after its first run")
    public void delayedRepeatingTickCadence() {
        var task = new ServerTask(() -> {}, 1, 3, 5);
        var runs = new ArrayList<Integer>();

        for (int i = 0; i < 11; i++) {
            if (task.shouldRun()) runs.add(task.getTicks());
        }

        assertEquals(List.of(5, 8, 11), runs);
    }

    @Test
    @DisplayName("legacy integer delays keep their historical real-second meaning")
    public void integerDelayUsesWallClockSeconds() {
        AtomicLong now = createSchedulerWithClock();
        var runs = new AtomicInteger();
        this.scheduler.scheduleDelayedTask(runs::incrementAndGet, 1);

        this.scheduler.runTasks();
        assertEquals(0, runs.get());

        now.set(TimeUnit.MILLISECONDS.toNanos(999));
        this.scheduler.runTasks();
        assertEquals(0, runs.get());

        now.set(TimeUnit.SECONDS.toNanos(1));
        this.scheduler.runTasks();
        assertEquals(1, runs.get());
    }

    @Test
    @DisplayName("duration repeating tasks use wall time and fixed-delay cadence")
    public void durationRepeatingCadence() {
        AtomicLong now = createSchedulerWithClock();
        var runs = new AtomicInteger();
        this.scheduler.scheduleDelayedRepeatingTask(
                runs::incrementAndGet, Duration.ofMillis(300), Duration.ofMillis(100));

        now.set(TimeUnit.MILLISECONDS.toNanos(100));
        this.scheduler.runTasks();
        assertEquals(1, runs.get());

        now.set(TimeUnit.MILLISECONDS.toNanos(399));
        this.scheduler.runTasks();
        assertEquals(1, runs.get());

        now.set(TimeUnit.MILLISECONDS.toNanos(400));
        this.scheduler.runTasks();
        assertEquals(2, runs.get());
    }

    @Test
    @DisplayName("explicit tick APIs stay coupled to the game loop")
    public void explicitTickDelayUsesSchedulerTicks() {
        createScheduler();
        var runs = new AtomicInteger();
        this.scheduler.scheduleDelayedTaskTicks(runs::incrementAndGet, 5);

        for (int i = 0; i < 4; i++) this.scheduler.runTasks();
        assertEquals(0, runs.get());

        this.scheduler.runTasks();
        assertEquals(1, runs.get());
    }

    @Test
    @DisplayName("an async-only queue is serviced without a synchronous task keeping it alive")
    public void asyncOnlyTaskRuns() {
        createScheduler();
        var runs = new AtomicInteger();
        int taskId = this.scheduler.scheduleAsyncTask(runs::incrementAndGet);
        AsyncServerTask task = this.scheduler.getAsyncTask(taskId);
        assertNotNull(task);

        this.scheduler.runTasks();
        assertEquals(1, this.executor.queuedTasks());
        assertEquals(0, runs.get());

        this.executor.runNext();
        assertEquals(1, runs.get());
        assertTrue(task.isFinished());

        this.scheduler.runTasks();
        assertNull(this.scheduler.getAsyncTask(taskId));
    }

    @Test
    @DisplayName("repeated scheduler ticks cannot submit the same async task twice")
    public void asyncTaskStartsOnce() {
        createScheduler();
        var runs = new AtomicInteger();
        int taskId = this.scheduler.scheduleAsyncTask(runs::incrementAndGet);
        AsyncServerTask task = this.scheduler.getAsyncTask(taskId);
        assertNotNull(task);

        for (int i = 0; i < 20; i++) this.scheduler.runTasks();
        assertEquals(1, this.executor.queuedTasks());
        assertEquals(0, runs.get());

        this.executor.runNext();
        assertEquals(1, runs.get());
        assertTrue(task.isFinished());

        for (int i = 0; i < 5; i++) this.scheduler.runTasks();
        assertEquals(1, runs.get());
        assertEquals(0, this.executor.queuedTasks());
        assertNull(this.scheduler.getAsyncTask(taskId));
    }

    @Test
    @DisplayName("a failing async task still finishes, is removed, and runs its callback")
    public void failedAsyncTaskIsCleanedUp() {
        createScheduler();
        var callbacks = new AtomicInteger();
        int taskId =
                this.scheduler.scheduleAsyncTask(
                        () -> {
                            throw new IllegalStateException("boom");
                        },
                        callbacks::incrementAndGet);
        AsyncServerTask task = this.scheduler.getAsyncTask(taskId);
        assertNotNull(task);

        this.scheduler.runTasks();
        assertEquals(1, this.executor.queuedTasks());
        this.executor.runNext();

        assertTrue(task.isFinished());
        assertInstanceOf(IllegalStateException.class, task.getFailure());

        this.scheduler.runTasks();
        assertEquals(1, callbacks.get());
        assertNull(this.scheduler.getAsyncTask(taskId));
    }

    @Test
    @DisplayName("invalid wall-clock and tick periods are rejected when scheduled")
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
        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleRepeatingTask(() -> {}, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleDelayedTask(() -> {}, Duration.ofMillis(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleRepeatingTaskTicks(() -> {}, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> this.scheduler.scheduleDelayedTaskTicks(() -> {}, -1));
    }

    /**
     * Deterministic executor for scheduler state-machine tests.
     *
     * <p>Keeping the submitted FutureTask queued lets a test call runTasks repeatedly before the
     * worker is allowed to run. That checks duplicate submission without relying on thread timing,
     * sleeps, latches, or a test worker that can deadlock Gradle itself.
     */
    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            this.shutdown = true;
            var remaining = new ArrayList<Runnable>(this.queue);
            this.queue.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown && this.queue.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return this.isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            if (this.shutdown) throw new RejectedExecutionException("executor is shut down");
            this.queue.addLast(command);
        }

        int queuedTasks() {
            return this.queue.size();
        }

        void runNext() {
            Runnable command = this.queue.pollFirst();
            if (command == null) throw new AssertionError("no queued task to run");
            command.run();
        }
    }
}
