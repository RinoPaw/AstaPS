package emu.grasscutter.server.scheduler;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/** A server task whose body runs outside the game tick thread. */
public final class AsyncServerTask implements Runnable {
    private final Runnable task;
    private final int taskId;
    @Nullable private final Runnable callback;
    private final AtomicReference<FutureTask<Void>> future = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    @Nullable private volatile Object result;

    public AsyncServerTask(Runnable task, int taskId) {
        this(task, null, taskId);
    }

    public AsyncServerTask(Runnable task, @Nullable Runnable callback, int taskId) {
        this.task = task;
        this.callback = callback;
        this.taskId = taskId;
    }

    public int getTaskId() {
        return this.taskId;
    }

    public boolean hasStarted() {
        return this.future.get() != null;
    }

    public boolean isFinished() {
        FutureTask<Void> future = this.future.get();
        return future != null && future.isDone();
    }

    public boolean isCancelled() {
        return this.cancelled.get();
    }

    boolean start(Executor executor) {
        if (this.cancelled.get()) return false;

        var submitted = new FutureTask<Void>(this, null);
        if (!this.future.compareAndSet(null, submitted)) return false;

        if (this.cancelled.get()) {
            submitted.cancel(true);
            return false;
        }

        try {
            executor.execute(submitted);
            return true;
        } catch (RuntimeException | Error ex) {
            this.future.compareAndSet(submitted, null);
            throw ex;
        }
    }

    public void cancel() {
        this.cancelled.set(true);
        FutureTask<Void> future = this.future.get();
        if (future != null) future.cancel(true);
    }

    @Nullable public Throwable getFailure() {
        FutureTask<Void> future = this.future.get();
        if (future == null || !future.isDone()) return null;

        try {
            future.get();
            return null;
        } catch (CancellationException ex) {
            return ex;
        } catch (ExecutionException ex) {
            return ex.getCause();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ex;
        }
    }

    @Override
    public void run() {
        this.task.run();
    }

    public void complete() {
        if (this.callback != null) this.callback.run();
    }

    @Nullable public Object getResult() {
        return this.result;
    }

    public void setResult(@Nullable Object result) {
        this.result = result;
    }
}
