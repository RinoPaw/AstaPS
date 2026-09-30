package emu.grasscutter.server.scheduler;

import emu.grasscutter.Grasscutter;

/** A synchronous scheduler task measured in game ticks. */
public final class ServerTask implements Runnable {
    private final Runnable runnable;
    private final int taskId;
    private final int period;
    private int ticks;
    private int remainingTicks;
    private volatile boolean completed;
    private volatile boolean cancelled;

    public ServerTask(Runnable runnable, int taskId, int period, int delay) {
        if (period == 0 || period < -1) {
            throw new IllegalArgumentException("period must be -1 or > 0");
        }
        if (delay < -1) throw new IllegalArgumentException("delay must be >= -1");

        this.runnable = runnable;
        this.taskId = taskId;
        this.period = period;
        this.remainingTicks = delay > 0 ? delay : 1;
    }

    public int getTaskId() {
        return this.taskId;
    }

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

        ++this.ticks;
        if (--this.remainingTicks > 0) return false;

        if (this.period > 0) {
            this.remainingTicks = this.period;
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
