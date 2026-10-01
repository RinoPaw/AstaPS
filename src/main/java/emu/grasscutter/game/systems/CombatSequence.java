package emu.grasscutter.game.systems;

import java.util.concurrent.atomic.AtomicInteger;

/** Sequence source for Genshin 7.1 server-to-client CombatInvocationsNotify packets. */
public final class CombatSequence {
    private static final AtomicInteger sequence = new AtomicInteger();

    private CombatSequence() {}

    public static int next() {
        return sequence.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
    }
}
