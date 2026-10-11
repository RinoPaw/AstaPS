package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.RewardGrantJournal.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RewardGrantJournalTest {
    private static final Key CLAIM = new Key(Source.MAIL, 10001, "deadbeef01");
    private static final List<Reward> REWARDS = List.of(new Reward(202, 100, 1));

    private static final class FakeStore implements RewardGrantJournal.Store {
        private final Map<Key, Receipt> receipts = new HashMap<>();

        @Override
        public boolean insert(Receipt receipt) {
            return receipts.putIfAbsent(receipt.key(), receipt) == null;
        }

        @Override
        public boolean transition(Key key, Stage from, Stage to) {
            Receipt old = receipts.get(key);
            if (old == null || old.stage() != from) return false;
            receipts.put(key, new Receipt(key, to, old.rewards()));
            return true;
        }

        @Override
        public List<Receipt> unresolved() {
            return receipts.values().stream()
                    .filter(r -> r.stage() != Stage.COMPLETED)
                    .toList();
        }
    }

    @Test
    void startsAsPendingAndRejectsDuplicateClaims() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertTrue(journal.prepare(CLAIM, REWARDS));
        assertFalse(journal.prepare(CLAIM, List.of(new Reward(201, 1, 1))));
        assertEquals(REWARDS, journal.unresolved().getFirst().rewards());
        assertEquals(Stage.PREPARED, journal.unresolved().getFirst().stage());
    }

    @Test
    void confirmedGrantIsVisibleForReconciliationUntilCompleted() {
        var journal = new RewardGrantJournal(new FakeStore());
        journal.prepare(CLAIM, REWARDS);
        journal.confirm(CLAIM);
        assertEquals(Stage.CONFIRMED, journal.unresolved().getFirst().stage());
        journal.complete(CLAIM);
        assertTrue(journal.unresolved().isEmpty());
        assertFalse(journal.prepare(CLAIM, REWARDS));
    }

    @Test
    void rejectsOutOfOrderAndRepeatedStateTransitions() {
        var journal = new RewardGrantJournal(new FakeStore());
        assertThrows(IllegalStateException.class, () -> journal.confirm(CLAIM));
        journal.prepare(CLAIM, REWARDS);
        assertThrows(IllegalStateException.class, () -> journal.complete(CLAIM));
        journal.confirm(CLAIM);
        assertThrows(IllegalStateException.class, () -> journal.confirm(CLAIM));
        journal.complete(CLAIM);
        assertThrows(IllegalStateException.class, () -> journal.complete(CLAIM));
    }

    @Test
    void rejectsUnsafeIdentitiesAndRewards() {
        assertThrows(IllegalArgumentException.class, () -> new Key(Source.MAIL, 0, "id"));
        assertThrows(IllegalArgumentException.class, () -> new Key(Source.MAIL, 10, "../../x"));
        assertThrows(IllegalArgumentException.class, () -> new Reward(202, -5, 1));
        assertThrows(IllegalArgumentException.class, () -> new Reward(202, 1, -1));
        assertThrows(IllegalArgumentException.class, () ->
                new Receipt(CLAIM, Stage.PREPARED, new ArrayList<>()));
    }

    @Test
    void copiesTheRewardSnapshot() {
        var input = new ArrayList<>(REWARDS);
        var journal = new RewardGrantJournal(new FakeStore());
        journal.prepare(CLAIM, input);
        input.clear();
        assertEquals(REWARDS, journal.unresolved().getFirst().rewards());
        assertEquals("mail:10001:deadbeef01", CLAIM.storageId());
    }
}
