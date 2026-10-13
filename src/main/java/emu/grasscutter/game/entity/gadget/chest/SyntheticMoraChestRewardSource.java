package emu.grasscutter.game.entity.gadget.chest;

/**
 * Restricts a temporary, native-table-backed payout route to synthetic Mora chests.
 * The source marker's identity comes from the synthetic group range and regional ID.
 * This is not a claim that the resource table's probabilities are verified for 7.1.
 */
public final class SyntheticMoraChestRewardSource {
    private static final int GROUP_BASE = 910700000;

    private SyntheticMoraChestRewardSource() {}

    public static boolean applies(int groupId, String tag) {
        if (tag == null || groupId <= GROUP_BASE || groupId >= GROUP_BASE + 100000) {
            return false;
        }
        int id = groupId - GROUP_BASE;
        if (id >= 40000 && id < 50000) return "摩拉石箱须弥".equals(tag);
        if (id >= 30000 && id < 40000) return "摩拉石箱纳塔".equals(tag);
        if (id >= 20000 && id < 30000) return "摩拉石箱枫丹".equals(tag);
        return false; // Nod-Krai has no validated Mora table; Snezhnaya is a separate owner.
    }
}
