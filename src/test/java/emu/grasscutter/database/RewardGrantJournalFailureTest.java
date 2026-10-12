package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.RewardGrantJournal.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Model acknowledged and ambiguous receipt writes without a live database. */
final class RewardGrantJournalFailureTest {
    private static final Key KEY = new Key(Source.DOMAIN, 10001, "4470.test-run");
    private static final List<Reward> REWARDS = List.of(new Reward(202, 1, 1));

    private static final class FaultStore implements RewardGrantJournal.Store {
        final Map<Key, Receipt> rows = new HashMap<>();
        boolean failBeforeInsert;
        boolean failAfterInsert;
        boolean failBeforeTransition;
        boolean failAfterTransition;

        @Override public boolean insert(Receipt receipt) {
            if (failBeforeInsert) throw new IllegalStateException("insert not applied");
            boolean inserted = rows.putIfAbsent(receipt.key(), receipt) == null;
            if (failAfterInsert) throw new IllegalStateException("ambiguous insert acknowledgment");
            return inserted;
        }

        @Override public boolean transition(Key key, Stage from, Stage to) {
            if (failBeforeTransition) throw new IllegalStateException("transition not applied");
            Receipt old = rows.get(key);
            if (old == null || old.stage() != from) return false;
            rows.put(key, new Receipt(key, to, old.rewards(), old.debits()));
            if (failAfterTransition) throw new IllegalStateException("ambiguous transition acknowledgment");
            return true;
        }

        @Override public List<Receipt> unresolved() {
            return rows.values().stream()
                    .filter(r -> r.stage() == Stage.PREPARED || r.stage() == Stage.CONFIRMED)
                    .toList();
        }
    }

    @Test void failedPrepareBeforeWriteDoesNotCreateReceipt() {
        var store = new FaultStore();
        store.failBeforeInsert = true;
        var journal = new RewardGrantJournal(store);
        assertThrows(IllegalStateException.class, () -> journal.prepare(KEY, REWARDS));
        assertTrue(store.rows.isEmpty());
    }

    @Test void ambiguousPrepareMustNotBeReplayed() {
        var store = new FaultStore();
        store.failAfterInsert = true;
        var journal = new RewardGrantJournal(store);
        assertThrows(IllegalStateException.class, () -> journal.prepare(KEY, REWARDS));
        store.failAfterInsert = false;
        assertFalse(journal.prepare(KEY, REWARDS));
        assertEquals(Stage.PREPARED, journal.unresolved().getFirst().stage());
    }

    @Test void failedConfirmLeavesPreparedAndRejectsNewPrepare() {
        var store = new FaultStore();
        var journal = new RewardGrantJournal(store);
        assertTrue(journal.prepare(KEY, REWARDS));
        store.failBeforeTransition = true;
        assertThrows(IllegalStateException.class, () -> journal.confirm(KEY));
        store.failBeforeTransition = false;
        assertEquals(Stage.PREPARED, journal.unresolved().getFirst().stage());
        assertFalse(journal.prepare(KEY, REWARDS));
    }

    @Test void ambiguousConfirmCanAlreadyBeDurable() {
        var store = new FaultStore();
        var journal = new RewardGrantJournal(store);
        assertTrue(journal.prepare(KEY, REWARDS));
        store.failAfterTransition = true;
        assertThrows(IllegalStateException.class, () -> journal.confirm(KEY));
        assertEquals(Stage.CONFIRMED, journal.unresolved().getFirst().stage());
        store.failAfterTransition = false;
        assertFalse(journal.prepare(KEY, REWARDS));
    }

    @Test void ambiguousCompletionDoesNotAuthorizeReplay() {
        var store = new FaultStore();
        var journal = new RewardGrantJournal(store);
        journal.prepare(KEY, REWARDS);
        journal.confirm(KEY);
        store.failAfterTransition = true;
        assertThrows(IllegalStateException.class, () -> journal.complete(KEY));
        assertEquals(Stage.COMPLETED, store.rows.get(KEY).stage());
        assertFalse(journal.prepare(KEY, REWARDS));
    }
}
