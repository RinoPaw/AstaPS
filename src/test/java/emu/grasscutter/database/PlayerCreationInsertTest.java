package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlayerCreationInsertTest {
    @Test
    void concurrentFirstPlayerCreationUsesDifferentReservedUids() throws Exception {
        var store = new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>();
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        var reservations = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        PlayerUidReservation.Store reservationStore =
                new PlayerUidReservation.Store() {
                    @Override
                    public int highestUid() {
                        return store.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                    }

                    @Override
                    public boolean playerExists(int uid) {
                        return store.containsKey(uid);
                    }

                    @Override
                    public boolean tryReserve(int uid) {
                        return reservations.add(uid);
                    }

                    @Override
                    public int reserveNext(int floor) {
                        counter.accumulateAndGet(floor, Math::max);
                        return counter.incrementAndGet();
                    }
                };

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> create =
                    () -> {
                        start.await();
                        int uid = PlayerUidReservation.next(0, reservationStore);
                        assertNull(store.putIfAbsent(uid, Boolean.TRUE));
                        return uid;
                    };
            var one = executor.submit(create);
            var two = executor.submit(create);
            start.countDown();
            assertNotEquals(one.get(), two.get());
        }
    }

    @Test
    void duplicatePlayerIdCannotOverwriteExistingPlayer() {
        var existing = new Player();
        existing.setUid(7);
        var incoming = new Player();
        Map<Integer, Player> players = new ConcurrentHashMap<>();
        players.put(7, existing);

        assertThrows(
                IllegalStateException.class,
                () ->
                        PlayerUidAllocator.assignReserved(
                                incoming,
                                7,
                                player -> {
                                    if (players.putIfAbsent(player.getUid(), player) != null) {
                                        throw new IllegalStateException("duplicate _id");
                                    }
                                }));
        assertSame(existing, players.get(7));
    }
}
