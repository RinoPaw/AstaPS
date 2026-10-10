package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.scripts.constants.ScriptGadgetState;
import org.junit.jupiter.api.Test;

/** The domain fixture has a distinct active base and a default-state clickable tree. */
final class DomainRewardStatueStateTest {
    @Test
    void rewardTreeMustKeepDefaultClientState() {
        assertEquals(
                ScriptGadgetState.Default,
                DomainRewardStatueHelper.rewardGadgetStateAfterSettle(
                        DomainRewardStatueHelper.REWARD_TREE_GADGET_ID));
    }

    @Test
    void statueBaseFollowsNativeSettleTrigger() {
        assertEquals(
                ScriptGadgetState.StatueActive,
                DomainRewardStatueHelper.rewardGadgetStateAfterSettle(70340012));
        assertTrue(DomainRewardStatueHelper.isExitRewardStatue(70340012));
        assertTrue(DomainRewardStatueHelper.isExitRewardStatue(70350008));
    }

    @Test
    void synthetic40773PairHasOnlyOneClaimTarget() {
        assertTrue(DomainRewardStatueHelper.isRewardClaimTarget(40773, 70340012));
        assertFalse(DomainRewardStatueHelper.isRewardClaimTarget(40773, 70350008));
        // The compatibility exception is scoped to 40773.
        assertTrue(DomainRewardStatueHelper.isRewardClaimTarget(40501, 70350008));
    }

    @Test
    void otherExitStatuesRetainExistingActiveState() {
        assertEquals(
                ScriptGadgetState.StatueActive,
                DomainRewardStatueHelper.rewardGadgetStateAfterSettle(70340011));
        assertEquals(
                ScriptGadgetState.StatueActive,
                DomainRewardStatueHelper.rewardGadgetStateAfterSettle(70380008));
    }
}
