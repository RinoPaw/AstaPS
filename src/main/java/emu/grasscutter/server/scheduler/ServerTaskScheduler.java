package emu.grasscutter.server.scheduler;

import emu.grasscutter.server.threading.ManagedThreadPoolExecutor;
import emu.grasscutter.server.threading.ThreadPoolConfig;
import emu.grasscutter.server.threading.ThreadPoolConfigResolver;
import emu.grasscutter.server.threading.ThreadPoolType;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs short synchronous tasks on the game tick and dispatches asynchronous work to a managed pool.
 *
 * <p>All integer delay and period parameters are measured in game ticks. Their wall-clock duration
 * therefore follows the configured game tick rate; they are not seconds.
 */
public final class ServerTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTaskScheduler.class);
    private static final int DEFAULT_ASYNC_QUEUE_CAPACITY = 1024;
    private static final long DEFAULT_ASYNC_KEEP_ALIVE_SECONDS = 60L;

    private final ConcurrentHashMap<Integer, ServerTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AsyncServerTask> asyncTasks = new ConcurrentHashMap<>();
    private final AtomicInteger nextTaskId = new AtomicInteger();
    private final ExecutorService asyncExecutor;

    public ServerTaskScheduler() {
        this(createAsyncExecutor());
    }

    ServerTaskScheduler(ExecutorService asyncExecutor) {
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
    }

    private static ExecutorService createAsyncExecutor() {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int coreThreads = Math.max(1, Math.min(4, processors / 2));
        int maxThreads = Math.max(coreThreads, Math.min(8, processors));
        ThreadPoolConfig config =
                ThreadPoolConfigResolver.resolve(
                        "SCHEDULER_ASYNC",
                        ThreadPoolType.SCHEDULER,
                        coreThreads,
                        maxThreads,
                        DEFAULT_ASYNC_QUEUE_CAPACITY,
                        DEFAULT_ASYNC_KEEP_ALIVE_SECONDS);
        int queueCapacity =
                config.queueCapacity() > 0 ? config.queueCapacity() : DEFAULT_ASYNC_QUEUE_CAPACITY;
        var threadCounter = new AtomicInteger();

        return new ManagedThreadPoolExecutor(
                config,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    var thread =
                            new Thread(
                                    runnable,
                                    "scheduler-async-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Runs one scheduler pass from the game tick thread.
     *
     * <p>Synchronous task bodies and async completion callbacks run on this thread. Async task bodies
     * run in the scheduler's managed executor and are never waited on here.
     */
    public void runTasks() {
        for (ServerTask task : this.tasks.values()) {
            if (task.shouldRun()) task.run();
            if (task.shouldCancel()) this.tasks.remove(task.getTaskId(), task);
        }

        for (AsyncServerTask task : this.asyncTasks.values()) {
            if (!task.hasStarted()) {
                try {
                    task.start(this.asyncExecutor);
                } catch (RejectedExecutionException ex) {
                    if (this.asyncTasks.remove(task.getTaskId(), task)) {
                        LOGGER.warn(
                                "Async task {} was rejected by the scheduler pool.",
                                task.getTaskId(),
                                ex);
                    }
                    continue;
                }
            }

            if (task.isFinished() && this.asyncTasks.remove(task.getTaskId(), task)) {
                Throwable failure = task.getFailure();
                if (failure != null) {
                    LOGGER.error("Exception during async task {}.", task.getTaskId(), failure);
                }

                try {
                    task.complete();
                } catch (Throwable callbackFailure) {
                    LOGGER.error(
                            "Exception during async task {} callback.",
                            task.getTaskId(),
                            callbackFailure);
                }
            }
        }
    }

    public ServerTask getTask(int taskId) {
        return this.tasks.get(taskId);
    }

    public AsyncServerTask getAsyncTask(int taskId) {
        return this.asyncTasks.get(taskId);
    }

    /** Cancels either kind of scheduler task with this ID. */
    public void cancelTask(int taskId) {
        ServerTask task = this.tasks.remove(taskId);
        if (task != null) task.markCancelled();

        AsyncServerTask asyncTask = this.asyncTasks.remove(taskId);
        if (asyncTask != null) asyncTask.cancel();
    }

    /** Schedules asynchronous work to be submitted on the next game tick. */
    public int scheduleAsyncTask(Runnable runnable) {
        return this.scheduleAsyncTask(runnable, null);
    }

    /**
     * Schedules asynchronous work to be submitted on the next game tick.
     *
     * <p>The callback runs on a later game tick after the async body has finished, including when the
     * body completed exceptionally.
     */
    public int scheduleAsyncTask(Runnable runnable, @Nullable Runnable callback) {
        Objects.requireNonNull(runnable, "runnable");
        int taskId = this.nextTaskId();
        this.asyncTasks.put(taskId, new AsyncServerTask(runnable, callback, taskId));
        return taskId;
    }

    /** Schedules a synchronous task for the next game tick. */
    public int scheduleTask(Runnable runnable) {
        return this.scheduleServerTask(runnable, -1, -1);
    }

    /** Schedules a synchronous task after {@code delay} game ticks. */
    public int scheduleDelayedTask(Runnable runnable, int delay) {
        if (delay < 0) throw new IllegalArgumentException("delay must be >= 0");
        return this.scheduleServerTask(runnable, -1, delay);
    }

    /** Schedules a synchronous task every {@code period} game ticks, starting on the next tick. */
    public int scheduleRepeatingTask(Runnable runnable, int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        return this.scheduleServerTask(runnable, period, 0);
    }

    /**
     * Schedules a synchronous task after {@code delay} game ticks, then every {@code period} ticks.
     */
    public int scheduleDelayedRepeatingTask(Runnable runnable, int period, int delay) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        if (delay < 0) throw new IllegalArgumentException("delay must be >= 0");
        return this.scheduleServerTask(runnable, period, delay);
    }

    private int scheduleServerTask(Runnable runnable, int period, int delay) {
        Objects.requireNonNull(runnable, "runnable");
        int taskId = this.nextTaskId();
        this.tasks.put(taskId, new ServerTask(runnable, taskId, period, delay));
        return taskId;
    }

    private int nextTaskId() {
        while (true) {
            int candidate = this.nextTaskId.getAndIncrement();
            if (!this.tasks.containsKey(candidate) && !this.asyncTasks.containsKey(candidate)) {
                return candidate;
            }
        }
    }
}
