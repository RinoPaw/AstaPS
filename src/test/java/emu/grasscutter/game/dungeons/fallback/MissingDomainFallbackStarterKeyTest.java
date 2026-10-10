package emu.grasscutter.game.dungeons.fallback;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class MissingDomainFallbackStarterKeyTest {
    @Test
    void acceptsConfiguredFontaineWeaponDomainStarter() {
        assertTrue(MissingDomainFallbackManager.isConfiguredStarterKey(
                40773, 240773001, 9001, 70350096));
        assertTrue(MissingDomainFallbackManager.isConfiguredStarterKey(
                40770, 240770001, 9001, 70360010));
    }

    @Test
    void doesNotArmRewardStatueOrOtherGroupGadgets() {
        assertFalse(MissingDomainFallbackManager.isConfiguredStarterKey(
                40773, 240773004, 9001, 70350096));
        assertFalse(MissingDomainFallbackManager.isConfiguredStarterKey(
                40773, 240773001, 5001, 70350096));
        assertFalse(MissingDomainFallbackManager.isConfiguredStarterKey(
                40773, 240773001, 9001, 70350008));
        assertFalse(MissingDomainFallbackManager.isConfiguredStarterKey(
                12345, 240773001, 9001, 70350096));
    }
}
