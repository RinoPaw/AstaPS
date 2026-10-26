package emu.grasscutter.game.managers.blossom;

import java.util.List;

/** Native ley-line kinds. Country availability and counts remain in native Excel. */
public enum BlossomType {
    WEALTH("BLOSSOM_REFRESH_SCOIN", 1, 70360056, 70210109, "wealth"),
    REVELATION("BLOSSOM_REFRESH_EXP", 3, 70360057, 70210110, "revelation");

    private final String refreshType;
    private final int refreshTypeId;
    private final int gadgetId;
    private final int rewardChestGadgetId;
    private final String rewardKind;

    BlossomType(String refreshType, int refreshTypeId, int gadgetId, int chestId, String rewardKind) {
        this.refreshType = refreshType;
        this.refreshTypeId = refreshTypeId;
        this.gadgetId = gadgetId;
        this.rewardChestGadgetId = chestId;
        this.rewardKind = rewardKind;
    }

    public String refreshType() { return refreshType; }
    public int refreshTypeId() { return refreshTypeId; }
    public int getGadgetId() { return gadgetId; }
    public int getRewardChestGadgetId() { return rewardChestGadgetId; }
    public String rewardKind() { return rewardKind; }

    public static BlossomType forRefresh(String refreshType) {
        for (var type : values()) if (type.refreshType.equals(refreshType)) return type;
        return null;
    }

    public static BlossomType valueOf(int gadgetId) {
        for (var type : values()) if (type.gadgetId == gadgetId) return type;
        return null;
    }

    public static List<BlossomType> all() { return List.of(values()); }
}
