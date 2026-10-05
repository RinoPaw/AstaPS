package emu.grasscutter.game;

final class HandbookActionLimits {
    static final int MAX_ITEM_AMOUNT = 100_000;
    static final int MAX_SPAWN_AMOUNT = 100;

    private HandbookActionLimits() {}

    static int itemAmount(long amount) {
        return boundedAmount(amount, MAX_ITEM_AMOUNT);
    }

    static int spawnAmount(long amount) {
        return boundedAmount(amount, MAX_SPAWN_AMOUNT);
    }

    private static int boundedAmount(long amount, int max) {
        if (amount <= 0 || amount > max) {
            return -1;
        }
        return (int) amount;
    }
}
