package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.inventory.GameItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class ItemPersistenceServiceTest {
    @Test
    void firstSaveReservesIdBeforeAdmissionAndCannotResurrectConsumedItem() throws Exception {
        var item = item(null, 3);
        Map<ObjectId, Integer> persisted = new ConcurrentHashMap<>();
        var snapshotTaken = new CountDownLatch(1);
        var releaseSave = new CountDownLatch(1);
        var deleteStarted = new CountDownLatch(1);
        var savedCount = new AtomicInteger();
        var firstAdmission = new AtomicBoolean();
        var idAtAdmission = new AtomicReference<ObjectId>();

        try (var fixture =
                new Fixture(
                        2,
                        current -> {
                            var id = current.getObjectId();
                            int snapshot = current.getCount();
                            savedCount.set(snapshot);
                            snapshotTaken.countDown();
                            awaitRelease(releaseSave);
                            persisted.put(id, snapshot);
                        },
                        current -> {
                            deleteStarted.countDown();
                            persisted.remove(current.getObjectId());
                        },
                        () -> {
                            if (firstAdmission.compareAndSet(false, true)) {
                                idAtAdmission.set(item.getObjectId());
                            }
                        })) {
            try {
                assertNull(item.getObjectId());
                fixture.service.save(item);
                assertNotNull(idAtAdmission.get(), "ID must exist before executor admission");
                assertEquals(idAtAdmission.get(), item.getObjectId());
                assertTrue(snapshotTaken.await(2, TimeUnit.SECONDS));
                assertEquals(3, savedCount.get());

                item.setCount(0);
                fixture.service.delete(item);
                assertFalse(deleteStarted.await(100, TimeUnit.MILLISECONDS));
                releaseSave.countDown();
                fixture.drain();

                assertEquals(0, deleteStarted.getCount());
                assertTrue(persisted.isEmpty(), "Reload must not find a consumed item");
            } finally {
                releaseSave.countDown();
            }
        }
    }

    @Test
    void queuedSaveOfConsumedItemNeverInvokesPositiveUpsert() throws Exception {
        var item = item(null, 5);
        Map<ObjectId, Integer> persisted = new ConcurrentHashMap<>();
        var upserts = new AtomicInteger();
        var deletes = new AtomicInteger();
        var workerStarted = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);

        try (var fixture =
                new Fixture(
                        1,
                        current -> {
                            upserts.incrementAndGet();
                            persisted.put(current.getObjectId(), current.getCount());
                        },
                        current -> {
                            deletes.incrementAndGet();
                            persisted.remove(current.getObjectId());
                        })) {
            try {
                fixture.submit(
                        () -> {
                            workerStarted.countDown();
                            awaitRelease(releaseWorker);
                        });
                assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
                fixture.service.save(item);
                assertNotNull(item.getObjectId());
                item.setCount(0);

                releaseWorker.countDown();
                fixture.drain();
                assertEquals(0, upserts.get());
                assertEquals(1, deletes.get());
                assertTrue(persisted.isEmpty());
            } finally {
                releaseWorker.countDown();
            }
        }
    }

    @Test
    void distinctObjectsWithSameDurableIdStillOrderSaveBeforeDelete() throws Exception {
        var id = new ObjectId();
        var original = item(id, 4);
        var consumedCopy = item(id, 0);
        Map<ObjectId, Integer> persisted = new ConcurrentHashMap<>();
        var calls = new ConcurrentLinkedQueue<String>();
        var snapshotTaken = new CountDownLatch(1);
        var releaseSave = new CountDownLatch(1);
        var deleteStarted = new CountDownLatch(1);

        try (var fixture =
                new Fixture(
                        2,
                        current -> {
                            int snapshot = current.getCount();
                            snapshotTaken.countDown();
                            awaitRelease(releaseSave);
                            persisted.put(current.getObjectId(), snapshot);
                            calls.add("save");
                        },
                        current -> {
                            deleteStarted.countDown();
                            persisted.remove(current.getObjectId());
                            calls.add("delete");
                        })) {
            try {
                assertNotSame(original, consumedCopy);
                fixture.service.save(original);
                assertTrue(snapshotTaken.await(2, TimeUnit.SECONDS));
                fixture.service.delete(consumedCopy);
                assertFalse(deleteStarted.await(100, TimeUnit.MILLISECONDS));

                releaseSave.countDown();
                fixture.drain();
                assertEquals(List.of("save", "delete"), new ArrayList<>(calls));
                assertTrue(persisted.isEmpty());
            } finally {
                releaseSave.countDown();
            }
        }
    }

    @Test
    void successiveUpdatesKeepLastPersistedCount() throws Exception {
        var item = item(null, 2);
        Map<ObjectId, Integer> persisted = new ConcurrentHashMap<>();
        var committedCounts = new ConcurrentLinkedQueue<Integer>();
        var firstCommitted = new CountDownLatch(1);
        var secondSnapshotTaken = new CountDownLatch(1);
        var releaseSecondSave = new CountDownLatch(1);
        var thirdStarted = new CountDownLatch(1);

        try (var fixture =
                new Fixture(
                        2,
                        current -> {
                            int snapshot = current.getCount();
                            if (snapshot == 7) {
                                secondSnapshotTaken.countDown();
                                awaitRelease(releaseSecondSave);
                            } else if (snapshot == 9) {
                                thirdStarted.countDown();
                            }
                            persisted.put(current.getObjectId(), snapshot);
                            committedCounts.add(snapshot);
                            if (snapshot == 2) firstCommitted.countDown();
                        },
                        current -> persisted.remove(current.getObjectId()))) {
            try {
                fixture.service.save(item);
                assertTrue(firstCommitted.await(2, TimeUnit.SECONDS));
                var id = item.getObjectId();

                item.setCount(7);
                fixture.service.save(item);
                assertTrue(secondSnapshotTaken.await(2, TimeUnit.SECONDS));
                item.setCount(9);
                fixture.service.save(item);
                assertFalse(thirdStarted.await(100, TimeUnit.MILLISECONDS));

                releaseSecondSave.countDown();
                fixture.drain();
                assertEquals(id, item.getObjectId());
                assertEquals(List.of(2, 7, 9), new ArrayList<>(committedCounts));
                assertEquals(Map.of(id, 9), persisted);
            } finally {
                releaseSecondSave.countDown();
            }
        }
    }

    private static GameItem item(ObjectId id, int count) throws Exception {
        var item = new GameItem();
        var ownerField = GameItem.class.getDeclaredField("ownerId");
        ownerField.setAccessible(true);
        ownerField.setInt(item, 10001);
        if (id != null) {
            var idField = GameItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(item, id);
        }
        item.setCount(count);
        return item;
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test write release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test write interrupted", exception);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ThreadPoolExecutor executor;
        final DatabaseWriterManager writers;
        final ItemPersistenceService service;
        final AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        Fixture(int threads, Consumer<GameItem> save, Consumer<GameItem> delete) {
            this(threads, save, delete, () -> {});
        }

        Fixture(
                int threads, Consumer<GameItem> save, Consumer<GameItem> delete, Runnable beforeAdmission) {
            executor =
                    new ThreadPoolExecutor(
                            threads,
                            threads,
                            0,
                            TimeUnit.MILLISECONDS,
                            new ArrayBlockingQueue<>(16),
                            new ThreadPoolExecutor.AbortPolicy()) {
                        @Override
                        public void execute(Runnable task) {
                            beforeAdmission.run();
                            super.execute(task);
                        }
                    };
            writers = new DatabaseWriterManager(Map.of("item", executor));
            service =
                    new ItemPersistenceService(
                            writers,
                            executor,
                            item -> recordFailure(() -> save.accept(item)),
                            item -> recordFailure(() -> delete.accept(item)));
        }

        void submit(Runnable task) {
            writers.submit(executor, () -> recordFailure(task));
        }

        void drain() {
            assertTrue(writers.shutdown(Duration.ofSeconds(5)).completed());
            assertNull(workerFailure.get(), "Fake persistence callback failed");
        }

        private void recordFailure(Runnable task) {
            try {
                task.run();
            } catch (Throwable failure) {
                workerFailure.compareAndSet(null, failure);
            }
        }

        @Override
        public void close() throws InterruptedException {
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test item executor did not terminate");
            }
        }
    }
}
