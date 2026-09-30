package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Performs a complete, synchronous hard delete of an account and its player-owned data. */
public final class AccountDeletionService {
    private static final long LOGOUT_TIMEOUT_SECONDS = 10;
    private static final long DATABASE_BARRIER_TIMEOUT_SECONDS = 30;

    private AccountDeletionService() {}

    public static synchronized void delete(Account account) {
        disconnectAndWait(account.getId());

        // Resolve the player after logout so the final onLogout saves have already been submitted.
        Player player = DatabaseHelper.getPlayerByAccount(account, Player.class);

        // Hold every database writer at a common fence. This drains writes that were queued before
        // deletion and prevents those writes from recreating documents while the hard delete runs.
        try (DatabaseWriteBarrier ignored = DatabaseWriteBarrier.acquire()) {
            if (player != null) {
                deletePlayerData(player.getUid());
            }
            deleteAccountDocument(account.getId());
        }
    }

    private static void disconnectAndWait(String accountId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOGOUT_TIMEOUT_SECONDS);
        var gameServer = Grasscutter.getGameServer();
        Player online = gameServer.getPlayerByAccountId(accountId);

        while (online != null) {
            if (online.getSession() != null) {
                online.getSession().close();
            }

            while (gameServer.getPlayerByAccountId(accountId) == online) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException(
                            "Timed out waiting for account " + accountId + " to log out before deletion");
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while waiting for account " + accountId + " to log out", e);
                }
            }

            // A reconnect can race the first disconnect. Keep the account offline for the delete.
            online = gameServer.getPlayerByAccountId(accountId);
        }
    }

    private static void deletePlayerData(int uid) {
        var database = DatabaseManager.getGameDatabase();

        database.getCollection("achievements").deleteMany(eq("uid", uid));
        database.getCollection("activities").deleteMany(eq("uid", uid));
        database.getCollection("homes").deleteMany(eq("ownerUid", uid));
        database.getCollection("mail").deleteMany(eq("ownerUid", uid));
        database.getCollection("avatars").deleteMany(eq("ownerId", uid));
        database.getCollection("gachas").deleteMany(eq("ownerId", uid));
        database.getCollection("items").deleteMany(eq("ownerId", uid));
        database.getCollection("quests").deleteMany(eq("ownerUid", uid));
        database.getCollection("battlepass").deleteMany(eq("ownerUid", uid));
        database.getCollection("dailytasks").deleteMany(eq("ownerUid", uid));
        database.getCollection("group_instances").deleteMany(eq("ownerUid", uid));
        database.getCollection("open_world_spawns").deleteMany(eq("ownerUid", uid));
        database.getCollection("music_game_beatmaps").deleteMany(eq("authorUid", uid));

        database.getCollection("friendships").deleteMany(eq("ownerId", uid));
        database.getCollection("friendships").deleteMany(eq("friendId", uid));

        DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(dev.morphia.query.experimental.filters.Filters.eq("id", uid))
                .delete();
    }

    private static void deleteAccountDocument(String accountId) {
        DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(dev.morphia.query.experimental.filters.Filters.eq("id", accountId))
                .delete();
    }

    private static final class DatabaseWriteBarrier implements AutoCloseable {
        private final List<PoolFence> fences;

        private DatabaseWriteBarrier(List<PoolFence> fences) {
            this.fences = fences;
        }

        private static DatabaseWriteBarrier acquire() {
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
