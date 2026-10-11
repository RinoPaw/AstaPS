package emu.grasscutter.game.battlepass;

import emu.grasscutter.database.RewardGrantJournal;

/** Stable across repeated requests for the same player, schedule and BP reward. */
final class BattlePassClaimIdentity {
    private BattlePassClaimIdentity() {}

    static RewardGrantJournal.Key key(int uid, int scheduleId, int seasonBegin, int rewardId) {
        if (scheduleId <= 0 || seasonBegin <= 0 || rewardId <= 0) {
            throw new IllegalArgumentException("Invalid Battle Pass claim identity");
        }
        return new RewardGrantJournal.Key(RewardGrantJournal.Source.BATTLE_PASS, uid,
                scheduleId + "." + seasonBegin + "." + rewardId);
    }
}
