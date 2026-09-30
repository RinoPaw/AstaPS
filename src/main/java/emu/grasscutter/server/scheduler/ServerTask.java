package emu.grasscutter.server.scheduler;

import emu.grasscutter.Grasscutter;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

/** A synchronous scheduler task backed by either game ticks or a monotonic wall clock. */
public final class ServerTask implements Runnable {
    private final Runnable runnable;
    private final int taskId;

    // Tick-based mode.
    private final int periodTicks;
    private int ticks;
    private int remainingTicks;

    // Wall-clock mode. A null clock means this is a tick-based task.
    @Nullable private final LongSupplier nanoTime;
    private final long periodNanos;
    private long nextRunNanos;

    private volatile boolean completed;
    private volatile boolean cancelled;

    /** Creates an explicit game-tick task. */
    public ServerTask(Runnable runnable, int taskId, int period, int delay) {
        if (period == 0 || period < -1) {
            throw new IllegalArgumentException("period must be -1 or > 0");
        }
        if (delay < -1) throw new IllegalArgumentException("delay must be >= -1");

        this.runnable = Objects.requireNonNull(runnable, "runnable");
        this.taskId = taskId;
        this.periodTicks = period;
        this.remainingTicks = delay > 0 ? delay : 1;
        this.nanoTime = null;
        this.periodNanos = 0L;
    }

    /** Creates a wall-clock task. A null period means one-shot. */
    ServerTask(
            Runnable runnable,
            int taskId,
            @Nullable Duration period,
            Duration delay,
            LongSupplier nanoTime) {
        this.runnable = Objects.requireNonNull(runnable, "runnable");
        this.taskId = taskId;
        this.periodTicks = -1;
        this.remainingTicks = 0;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");

        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) throw new IllegalArgumentException("delay must be >= 0");
        long delayNanos = durationToNanos(delay, true, "delay");

        if (period == null) {
            this.periodNanos = 0L;
        } else {
            Objects.requireNonNull(period, "period");
            this.periodNanos = durationToNanos(period, false, "period");
        }

        // System.nanoTime is intentionally allowed to wrap. Subtraction in shouldRun remains valid
        // for intervals shorter than half of the nanoTime range, and absurd multi-century delays are
        // rejected by Duration#toNanos before they get here.
        this.nextRunNanos = this.nanoTime.getAsLong() + delayNanos;
    }

    private static long durationToNanos(Duration duration, boolean allowZero, String name) {
        if (duration.isNegative() || (!allowZero && duration.isZero())) {
            throw new IllegalArgumentException(name + (allowZero ? " must be >= 0" : " must be > 0"));
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " is too large", ex);
        }
    }

    public int getTaskId() {
        return this.taskId;
    }

    /** Number of scheduler polls observed by an explicit tick-based task. */
    public int getTicks() {
        return this.ticks;
    }

    /** Cancels the task before its next execution. */
    public void cancel() {
        Grasscutter.getGameServer().getScheduler().cancelTask(this.taskId);
    }

    void markCancelled() {
        this.cancelled = true;
    }

    public boolean shouldRun() {
        if (this.cancelled || this.completed) return false;

        if (this.nanoTime != null) {
            long now = this.nanoTime.getAsLong();
            if (now - this.nextRunNanos < 0L) return false;

            if (this.periodNanos > 0L) {
                // Fixed-delay semantics prevent a stalled game loop from replaying a burst of missed
                // timer events on the first healthy tick.
                this.nextRunNanos = now + this.periodNanos;
            } else {
                this.completed = true;
            }
            return true;
        }

        ++this.ticks;
        if (--this.remainingTicks > 0) return false;

        if (this.periodTicks > 0) {
            this.remainingTicks = this.periodTicks;
        } else {
            this.completed = true;
        }
        return true;
    }

    public boolean shouldCancel() {
        return this.cancelled || this.completed;
    }

    @Override
    public void run() {
        try {
            this.runnable.run();
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Exception during task: ", ex);
        }
    }
}
