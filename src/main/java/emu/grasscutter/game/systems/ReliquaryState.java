package emu.grasscutter.game.systems;

import com.google.gson.annotations.SerializedName;

import dev.morphia.annotations.Entity;

import emu.grasscutter.net.proto.DKBOEFJCLBPOuterClass.DKBOEFJCLBP;
import emu.grasscutter.net.proto.GKEDIBFPBOPOuterClass.GKEDIBFPBOP;
import emu.grasscutter.net.proto.ReliquaryLockAppendPropFilterOuterClass.ReliquaryLockAppendPropFilter;
import emu.grasscutter.net.proto.ReliquaryLockMainPropFilterOuterClass.ReliquaryLockMainPropFilter;
import emu.grasscutter.net.proto.ReliquaryLockSchemeOuterClass.ReliquaryLockScheme;
import emu.grasscutter.net.proto.ReliquaryWearCustomDataOuterClass.ReliquaryWearCustomData;
import emu.grasscutter.net.proto.Uint32PairOuterClass.Uint32Pair;

import java.util.*;

/** 只保存实际条件，协议对象不直接写入存档。 */
@Entity(useDiscriminator = false)
public final class ReliquaryState {
    public Set<Integer> enabledOptions = new HashSet<>(), disabledSets = new HashSet<>();
    public Map<Integer, List<LockScheme>> schemes = new HashMap<>();
    public Map<String, Wear> wear = new HashMap<>();
    public Map<Integer, List<Integer>> filters = new LinkedHashMap<>();
    public Map<Integer, AvatarFilter> avatarFilters = new HashMap<>();
    public Pending pending;
    public int dustProgress;

    static String wearKey(int avatar, int element, int index) {
        return avatar + ":" + element + ":" + index;
    }

    void deleteWear(int avatar, int element, int index) {
        wear.remove(wearKey(avatar, element, index));
        if (index == 0) {
            var next = wear.remove(wearKey(avatar, element, 1));
            if (next != null) {
                next.index = 0;
                wear.put(wearKey(avatar, element, 0), next);
            }
        }
    }

    void replaceFilters(List<Uint32Pair> pairs) {
        var next = new LinkedHashMap<Integer, List<Integer>>();
        for (var pair : pairs) {
            var values = next.computeIfAbsent(pair.getKey(), k -> new ArrayList<>());
            if (!values.contains(pair.getValue())) values.add(pair.getValue());
        }
        filters = next;
    }

    @Entity(useDiscriminator = false)
    public static class Transmuter {
        public double progress;
        public int extractedThisCycle, cycleEndUnix, scheduleId;
        public Map<Integer, Integer> definedSuites = new HashMap<>();
        private static final long ANCHOR = 1785528000L, PERIOD = 42L * 24 * 3600;

        public void ensureCycle(long now) {
            int begin = Math.toIntExact(ANCHOR + Math.floorDiv(now - ANCHOR, PERIOD) * PERIOD);
            if (scheduleId != begin) {
                scheduleId = begin;
                cycleEndUnix = Math.toIntExact(begin + PERIOD);
                extractedThisCycle = 0;
                definedSuites.clear();
            }
        }
    }

    @Entity(useDiscriminator = false)
    public static class Pending {
        public String itemKey;
        public List<Integer> oldAppend = new ArrayList<>(),
                newAppend = new ArrayList<>(),
                chosen = new ArrayList<>();
    }

    @Entity(useDiscriminator = false)
    public static class MainFilter {
        @SerializedName("equip_type")
        public int equipType;

        @SerializedName("main_prop_type_list")
        public List<Integer> props = new ArrayList<>();
    }

    @Entity(useDiscriminator = false)
    public static class AppendFilter {
        @SerializedName("equip_type")
        public int equipType;

        @SerializedName("required_append_prop_list")
        public List<Integer> required = new ArrayList<>();

        @SerializedName("optional_append_prop_list")
        public List<Integer> optional = new ArrayList<>();

        @SerializedName("min_count")
        public int minCount;
    }

    @Entity(useDiscriminator = false)
    public static class LockScheme {
        @SerializedName("scheme_id")
        public int schemeId;

        public int version;

        @SerializedName("main_prop_filter_list")
        public List<MainFilter> main = new ArrayList<>();

        @SerializedName("append_prop_filter_list")
        public List<AppendFilter> append = new ArrayList<>();

        static LockScheme from(ReliquaryLockScheme proto) {
            var out = new LockScheme();
            out.schemeId = proto.getSchemeId();
            out.version = proto.getVersion();
            for (var v : proto.getMainPropFilterListList()) {
                var row = new MainFilter();
                row.equipType = v.getEquipType();
                row.props = new ArrayList<>(v.getMainPropTypeListList());
                out.main.add(row);
            }
            for (var v : proto.getAppendPropFilterListList()) {
                if (v.getRequiredAppendPropListCount() + v.getOptionalAppendPropListCount() == 0)
                    continue;
                var row = new AppendFilter();
                row.equipType = v.getEquipType();
                row.required = new ArrayList<>(v.getRequiredAppendPropListList());
                row.optional = new ArrayList<>(v.getOptionalAppendPropListList());
                row.minCount = v.getMinCount();
                out.append.add(row);
            }
            return out;
        }

        ReliquaryLockScheme proto(boolean defaults) {
            var out =
                    ReliquaryLockScheme.newBuilder()
                            .setSchemeId(schemeId)
                            .setVersion(version)
                            .setIsDefault(defaults);
            for (var v : main)
                out.addMainPropFilterList(
                        ReliquaryLockMainPropFilter.newBuilder()
                                .setEquipType(v.equipType)
                                .addAllMainPropTypeList(v.props));
            for (var v : append)
                out.addAppendPropFilterList(
                        ReliquaryLockAppendPropFilter.newBuilder()
                                .setEquipType(v.equipType)
                                .addAllRequiredAppendPropList(v.required)
                                .addAllOptionalAppendPropList(v.optional)
                                .setMinCount(v.minCount));
            return out.build();
        }
    }

    @Entity(useDiscriminator = false)
    public static class Wear {
        public int avatarId, element, index;
        public boolean all;
        public List<Integer> sets = new ArrayList<>(),
                main = new ArrayList<>(),
                append = new ArrayList<>();
        public Map<Integer, Integer> mainBySlot = new LinkedHashMap<>();

        static Wear from(ReliquaryWearCustomData p) {
            var v = new Wear();
            v.avatarId = p.getAvatarId();
            v.element = p.getAvatarElementType();
            v.index = p.getIndex();
            v.all = p.getIsAll();
            v.sets = new ArrayList<>(p.getReliquarySetIdListList());
            v.main = new ArrayList<>(p.getMainPropTypeListList());
            v.append = new ArrayList<>(p.getAppendPropTypeListList());
            for (var pair : p.getMainPropTypeMapList())
                v.mainBySlot.put(pair.getKey(), pair.getValue());
            return v;
        }

        ReliquaryWearCustomData proto() {
            var p =
                    ReliquaryWearCustomData.newBuilder()
                            .setAvatarId(avatarId)
                            .setAvatarElementType(element)
                            .setIndex(index)
                            .setIsAll(all)
                            .addAllReliquarySetIdList(sets)
                            .addAllMainPropTypeList(main)
                            .addAllAppendPropTypeList(append);
            mainBySlot.forEach(
                    (key, value) ->
                            p.addMainPropTypeMap(
                                    Uint32Pair.newBuilder().setKey(key).setValue(value)));
            return p.build();
        }
    }

    @Entity(useDiscriminator = false)
    public static class FilterSlot {
        public int equipType, INLHDNOJOMN;
        public List<Integer> affixes = new ArrayList<>();
    }

    @Entity(useDiscriminator = false)
    public static class AvatarFilter {
        public int avatarId;
        public List<Integer> sets = new ArrayList<>();
        public List<FilterSlot> slots = new ArrayList<>();

        GKEDIBFPBOP proto() {
            var p = GKEDIBFPBOP.newBuilder().setAvatarId(avatarId).addAllBJNFCNLFMFD(sets);
            for (var slot : slots)
                p.addKAGPLLAFPIE(
                        DKBOEFJCLBP
                                .newBuilder()
                                .setEquipType(slot.equipType)
                                .setINLHDNOJOMN(slot.INLHDNOJOMN)
                                .addAllAffixList(slot.affixes));
            return p.build();
        }
    }
}
