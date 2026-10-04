package emu.grasscutter.database;

import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import emu.grasscutter.game.player.Player;

/**
 * Hands out player UIDs, counting up from 1.
 *
 * <p>The first account on an empty server is UID 1, the next 2, and so on. UIDs are never reused:
 * the next one is always past the highest that exists, so deleting a player leaves a gap rather
 * than handing their number to somebody else.
 *
 * <p>On a server that already has players, numbering continues above whatever they hold. Existing
 * players are not renumbered - their UID is their identity, and other documents reference it.
 */
public final class PlayerUidAllocator {
    /** The UID the first player on an empty server gets. */
    public static final int FIRST_UID = 1;

    /**
     * How far to search upward when the UID after the highest one is somehow taken.
     *
     * <p>That should not happen, since UIDs only ever count up. It is a bounded fallback rather
     * than an unbounded loop so a corrupt state cannot hang account creation.
     */
    private static final int MAX_PROBES = 10_000;

    private PlayerUidAllocator() {}

    /**
     * @param reservedUid a UID this account asked for, or 0 for none.
     * @return the reserved UID when it is free, otherwise the next unused one.
     */
    public static synchronized int next(int reservedUid) {
        // An account may carry a reserved UID; honour it when nobody holds it yet.
        if (reservedUid > 0 && !DatabaseHelper.checkIfPlayerExists(reservedUid)) {
            return reservedUid;
        }

        var candidate = Math.max(FIRST_UID, highestUid() + 1);
        for (var probe = 0; probe < MAX_PROBES; probe++, candidate++) {
            if (!DatabaseHelper.checkIfPlayerExists(candidate)) return candidate;
        }

        // Nothing in the expected range was free, which means the player documents disagree with
        // their own ordering. Fall back to the datastore's counter rather than refusing to create
        // the account.
        int fromCounter;
        do {
            fromCounter = DatabaseManager.getNextId(Player.class);
        } while (DatabaseHelper.checkIfPlayerExists(fromCounter));
        return fromCounter;
    }

    /** The highest UID in use, or 0 when there are no players. */
    private static int highestUid() {
        var highest =
                DatabaseManager.getGameDatastore()
                        .find(
                                Player.class,
                                new FindOptions().sort(Sort.descending("_id")).limit(1))
                        .iterator()
                        .tryNext();
        return highest == null ? 0 : highest.getUid();
    }

    public static synchronized void assign(Player player, int reservedUid) {
        player.setUid(next(reservedUid));
        DatabaseHelper.savePlayer(player);
    }
}
