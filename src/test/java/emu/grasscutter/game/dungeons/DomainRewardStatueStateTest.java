package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.scripts.constants.ScriptGadgetState;
import org.junit.jupiter.api.Test;

/** Synthetic Fontaine fixtures have an active base and noninteractive accessory. */
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
    void syntheticFontaineWeaponScenesHaveOneClaimTarget() {
        for (int sceneId = 40770; sceneId <= 40773; sceneId++) {
            assertTrue(DomainRewardStatueHelper.isFontaineWeaponCompatibilityScene(sceneId));
            assertTrue(DomainRewardStatueHelper.isRewardClaimTarget(sceneId, 70340012));
            assertFalse(DomainRewardStatueHelper.isRewardClaimTarget(sceneId, 70350008));
            assertFalse(DomainRewardStatueHelper.isRewardClaimTarget(sceneId, 70360010));
        }

        // Other domain scenes keep the existing gadget behavior.
        assertFalse(DomainRewardStatueHelper.isFontaineWeaponCompatibilityScene(40769));
        assertFalse(DomainRewardStatueHelper.isFontaineWeaponCompatibilityScene(40774));
        assertTrue(DomainRewardStatueHelper.isRewardClaimTarget(40501, 70350008));
        assertTrue(DomainRewardStatueHelper.isRewardClaimTarget(40774, 70350008));
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
