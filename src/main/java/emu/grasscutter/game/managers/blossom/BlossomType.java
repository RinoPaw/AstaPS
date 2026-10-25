package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import java.io.IOException;
import java.util.*;

/**
 * Data-defined blossom kind. The catalog maps refresh rows to native Lua worktops;
 * adding a kind does not require changing Java or the camp allocator.
 */
public record BlossomType(
        String refreshType, int refreshTypeId, int gadgetId,
        int rewardChestGadgetId, String rewardKind) {

    private static final List<BlossomType> ALL = load();
    private static final Map<String, BlossomType> BY_REFRESH = indexByRefresh(ALL);
    private static final Map<Integer, BlossomType> BY_GADGET = indexByGadget(ALL);

    private static final class Config {
        String refreshType;
        int refreshTypeId;
        int gadgetId;
        int rewardChestGadgetId;
        String rewardKind;
    }

    private static List<BlossomType> load() {
        try {
            var rows = JsonUtils.loadToList(
                    FileUtils.getDataPath("BlossomTypes.json"), Config.class);
            if (rows == null || rows.isEmpty())
                throw new IllegalStateException("BlossomTypes.json has no blossom kinds");
            return rows.stream().map(row -> new BlossomType(
                    row.refreshType, row.refreshTypeId, row.gadgetId,
                    row.rewardChestGadgetId, row.rewardKind)).toList();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load BlossomTypes.json", error);
        }
    }

    static Map<String, BlossomType> indexByRefresh(Collection<BlossomType> types) {
        var result = new HashMap<String, BlossomType>();
        for (var type : types) {
            if (type.refreshType() == null || type.refreshType().isBlank()
                    || type.refreshTypeId() <= 0 || type.gadgetId() <= 0
                    || type.rewardChestGadgetId() <= 0 || type.rewardKind() == null
                    || type.rewardKind().isBlank()
                    || result.putIfAbsent(type.refreshType(), type) != null)
                throw new IllegalArgumentException("Invalid or duplicate blossom refresh: " + type);
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, BlossomType> indexByGadget(Collection<BlossomType> types) {
        var result = new HashMap<Integer, BlossomType>();
        var nativeTypes = new HashSet<Integer>();
        for (var type : types) {
            if (!nativeTypes.add(type.refreshTypeId())
                    || result.putIfAbsent(type.gadgetId(), type) != null)
                throw new IllegalArgumentException("Duplicate native blossom identity: " + type);
        }
        return Map.copyOf(result);
    }

    public static BlossomType forRefresh(String refreshType) {
        return BY_REFRESH.get(refreshType);
    }

    public static BlossomType valueOf(int gadgetId) {
        return BY_GADGET.get(gadgetId);
    }

    public static Collection<BlossomType> all() {
        return ALL;
    }

    public int getGadgetId() { return gadgetId; }
    public int getRewardChestGadgetId() { return rewardChestGadgetId; }
}
