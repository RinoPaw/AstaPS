package emu.grasscutter.game.battlepass;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.database.RewardGrantJournal;
import org.junit.jupiter.api.Test;

final class BattlePassClaimIdentityTest {
    @Test
    void retriesResolveToTheSameReceipt() {
        var first = BattlePassClaimIdentity.key(10001, 6700, 1785528000, 310001);
        var retry = BattlePassClaimIdentity.key(10001, 6700, 1785528000, 310001);
        assertEquals(first.storageId(), retry.storageId());
        assertEquals(RewardGrantJournal.Source.BATTLE_PASS, first.source());
    }

    @Test
    void distinctRewardsPlayersAndSeasonsDoNotShareReceipts() {
        var key = BattlePassClaimIdentity.key(10001, 6700, 1785528000, 310001);
        assertNotEquals(key, BattlePassClaimIdentity.key(10002, 6700, 1785528000, 310001));
        assertNotEquals(key, BattlePassClaimIdentity.key(10001, 6700, 1785528000, 310002));
        assertNotEquals(key, BattlePassClaimIdentity.key(10001, 6700, 1795528000, 310001));
        assertNotEquals(key, BattlePassClaimIdentity.key(10001, 6701, 1785528000, 310001));
    }

    @Test
    void rejectsInvalidClaimIdentifiers() {
        assertThrows(IllegalArgumentException.class, () ->
                BattlePassClaimIdentity.key(0, 6700, 1785528000, 310001));
        assertThrows(IllegalArgumentException.class, () ->
                BattlePassClaimIdentity.key(10001, 0, 1785528000, 310001));
        assertThrows(IllegalArgumentException.class, () ->
                BattlePassClaimIdentity.key(10001, 6700, 0, 310001));
        assertThrows(IllegalArgumentException.class, () ->
                BattlePassClaimIdentity.key(10001, 6700, 1785528000, 0));
    }
}
