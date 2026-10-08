package emu.grasscutter.data.excels.quest;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.quest.enums.*;
import java.util.*;
import javax.annotation.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "QuestExcelConfigData.json")
@Getter
@ToString
public class QuestData extends GameResource {
    @Getter private int subId;
    @Getter private int mainId;
    @Getter private int order;
    @Getter private long descTextMapHash;

    @Getter private boolean finishParent;
    @Getter private boolean isRewind;

    @Getter private LogicType acceptCondComb;
    @Getter private LogicType finishCondComb;
    @Getter private LogicType failCondComb;

    @Getter private List<QuestAcceptCondition> acceptCond;
    @Getter private List<QuestContentCondition> finishCond;
    @Getter private List<QuestContentCondition> failCond;
    @Getter private List<QuestExecParam> beginExec;
    @Getter private List<QuestExecParam> finishExec;
    @Getter private List<QuestExecParam> failExec;
    @Getter private Guide guide;

    @Getter private List<Integer> trialAvatarList;
    @Getter private List<ItemParamData> gainItems;

    public static String questConditionKey(
            @Nonnull Enum<?> type, int firstParam, @Nullable String paramsStr) {
        return type.name() + firstParam + (paramsStr != null ? paramsStr : "");
    }

    // ResourceLoader not happy if you remove getId() ~~
    public int getId() {
        return subId;
    }

    public void onLoad() {
        this.acceptCond = acceptCond.stream().filter(p -> p.getType() != null).toList();
        this.finishCond = finishCond.stream().filter(p -> p.getType() != null).toList();
        this.failCond = failCond.stream().filter(p -> p.getType() != null).toList();

        this.beginExec = beginExec.stream().filter(p -> p.type != null).toList();
        this.finishExec = finishExec.stream().filter(p -> p.type != null).toList();
        this.failExec = failExec.stream().filter(p -> p.type != null).toList();

        if (this.acceptCondComb == null) this.acceptCondComb = LogicType.LOGIC_NONE;

        if (this.finishCondComb == null) this.finishCondComb = LogicType.LOGIC_NONE;

        if (this.failCondComb == null) this.failCondComb = LogicType.LOGIC_NONE;

        if (this.gainItems == null) this.gainItems = Collections.emptyList();

        this.addToCache();
    }

    /**
     * Keep QuestExcel actions authoritative when present, but use the per-subquest BinOutput
     * actions when the Excel export has no usable entries. Quest 35302 requires its beginExec
     * group-suite refresh to spawn the combat-training slime.
     */
    public void applyFrom(MainQuestData.SubQuestData additionalData) {
        this.isRewind = additionalData.isRewind();
        this.finishParent = additionalData.isFinishParent();
        this.beginExec = effectiveExecList(this.beginExec, additionalData.getBeginExec());
        this.finishExec = effectiveExecList(this.finishExec, additionalData.getFinishExec());
        this.failExec = effectiveExecList(this.failExec, additionalData.getFailExec());
        this.gainItems = effectiveGainItems(this.gainItems, additionalData.getGainItems());

        // Reviewed compatibility prerequisites override synthetic QuestExcel chains within Mondstadt only.
        // A flattened quest-0 placeholder must not start the chapter before hilltop 35202.
        var corrected = selectReviewedMondstadtAcceptConditions(
                this.mainId, this.acceptCond, additionalData.getAcceptCond());
        if (!sameAcceptConditions(this.acceptCond, corrected)) {
            removeFromAcceptCache();
            this.acceptCond = corrected;
            addToCache();
        }
        if (REVIEWED_MONDSTADT_MAIN_QUESTS.contains(this.mainId)
                && additionalData.getAcceptCondComb() != null) {
            this.acceptCondComb = additionalData.getAcceptCondComb();
        }

        // Asta's flattened QuestExcel sometimes loses nontrivial finish logic.
        // Preserve its populated combinator, but restore a reviewed BinOutput
        // combinator when Excel only has LOGIC_NONE and there are multiple predicates.
        this.finishCondComb = effectiveMondstadtFinishLogic(
                this.mainId, this.finishCondComb, additionalData.getFinishCondComb(),
                this.finishCond == null ? 0 : this.finishCond.size());

        // Keep this data-only merge free of Grasscutter bootstrap side effects:
        // resource-level diagnostics are emitted by ResourceLoader after loading.
    }


    static LogicType effectiveMondstadtFinishLogic(
            int mainId, LogicType excel, LogicType bin, int predicateCount) {
        if (!REVIEWED_MONDSTADT_MAIN_QUESTS.contains(mainId)
                || predicateCount < 2
                || bin == null || bin == LogicType.LOGIC_NONE
                || (excel != null && excel != LogicType.LOGIC_NONE)) {
            return excel;
        }
        return bin;
    }

    static List<QuestExecParam> effectiveExecList(
            List<QuestExecParam> excel, List<QuestExecParam> bin) {
        var validExcel = validExecs(excel);
        return validExcel.isEmpty() ? validExecs(bin) : validExcel;
    }

    /**
     * Preserve explicit QuestExcel rewards; recover converter-dropped rewards from
     * native BinOutput when the flattened row contains none.
     * E.g. native 35402 grants item 1021 on completing Amber's introductory talk.
     */
    static List<ItemParamData> effectiveGainItems(
            List<ItemParamData> excel, List<ItemParamData> bin) {
        var validExcel = validGainItems(excel);
        return validExcel.isEmpty() ? validGainItems(bin) : validExcel;
    }

    private static List<ItemParamData> validGainItems(List<ItemParamData> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() > 0 && item.getCount() > 0)
                .toList();
    }

    private static List<QuestExecParam> validExecs(List<QuestExecParam> execs) {
        if (execs == null || execs.isEmpty()) return Collections.emptyList();
        return execs.stream()
                .filter(Objects::nonNull)
                .filter(exec -> exec.getType() != null)
                .toList();
    }

    /**
     * Reviewed compatibility policy only. Native ordinary 7.1 Quest files no longer
     * own acceptCond; BinOutput values here come from historical restoration.
     * Mainline scope follows Genshin-Reverse's 7.1 Mondstadt manifest; 361 is
     * retained as the forest transition despite WQ type.
     */
    private static final Set<Integer> REVIEWED_MONDSTADT_MAIN_QUESTS = Set.of(
            351, 359, 361,
            363, 352, 353, 355, 354, 360, 356, 357, 358, 306, 307, 308, 309, 311,
            370, 371, 372, 373, 374, 375, 376, 377, 20101, 379, 380, 381, 382, 383, 384,
            397, 388, 389, 390, 393, 394, 398, 396);

    public static boolean isReviewedMondstadtMainQuest(int id) {
        return REVIEWED_MONDSTADT_MAIN_QUESTS.contains(id);
    }

    /** Use populated reviewed BinOutput conditions; retain Excel for absent conditions. */
    static List<QuestAcceptCondition> selectReviewedMondstadtAcceptConditions(
            int mainId, List<QuestAcceptCondition> excel, List<QuestAcceptCondition> nativeValues) {
        if (!REVIEWED_MONDSTADT_MAIN_QUESTS.contains(mainId)
                || nativeValues == null || nativeValues.isEmpty()) return excel;
        var validNative = nativeValues.stream()
                .filter(Objects::nonNull)
                .filter(c -> c.getType() != null && c.getParam() != null && c.getParam().length > 0)
                .toList();
        return validNative.isEmpty() ? excel : validNative;
    }

    static boolean sameAcceptConditions(
            List<QuestAcceptCondition> a, List<QuestAcceptCondition> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            var left = a.get(i);
            var right = b.get(i);
            if (left == null || right == null) return false;
            if (left.getType() != right.getType()
                    || !Arrays.equals(left.getParam(), right.getParam())
                    || !Objects.equals(left.getParamStr(), right.getParamStr())) return false;
        }
        return true;
    }

    /** Invalidate old QuestExcel condition index entries before installing BinOutput keys. */
    private void removeFromAcceptCache() {
        if (this.acceptCond == null) return;
        var keys = new HashSet<String>();
        if (this.acceptCond.isEmpty()) {
            keys.add(questConditionKey(QuestCond.QUEST_COND_NONE, 0, null));
        } else {
            for (var cond : this.acceptCond) {
                if (cond != null && cond.getType() != null
                        && cond.getParam() != null && cond.getParam().length > 0) {
                    keys.add(cond.asKey());
                }
            }
        }
        for (var key : keys) {
            var quests = GameData.getBeginCondQuestMap().get(key);
            if (quests != null) {
                quests.remove(this);
                if (quests.isEmpty()) GameData.getBeginCondQuestMap().remove(key);
            }
        }
    }

    private void addToCache() {
        if (this.acceptCond == null) {
            Grasscutter.getLogger().warn("missing AcceptConditions for quest {}", getSubId());
            return;
        }

        var cacheMap = GameData.getBeginCondQuestMap();
        if (getAcceptCond().isEmpty()) {
            var list =
                    cacheMap.computeIfAbsent(
                            QuestData.questConditionKey(QuestCond.QUEST_COND_NONE, 0, null),
                            e -> new ArrayList<>());
            list.add(this);
        } else {
            this.getAcceptCond()
                    .forEach(
                            questCondition -> {
                                if (questCondition.getType() == null) {
                                    Grasscutter.getLogger().warn("null accept type for quest {}", getSubId());
                                    return;
                                }

                                var key = questCondition.asKey();
                                var list = cacheMap.computeIfAbsent(key, e -> new ArrayList<>());
                                list.add(this);
                            });
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class QuestExecParam {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        QuestExec type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        String[] param;

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        String count;
    }

    public static class QuestAcceptCondition extends QuestCondition<QuestCond> {}

    public static class QuestContentCondition extends QuestCondition<QuestContent> {}

    @Data
    public static class QuestCondition<TYPE extends Enum<?> & QuestTrigger> {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        private TYPE type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        private int[] param;

        @SerializedName(
                value = "_param_str",
                alternate = {"param_str"})
        private String paramStr = "";

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        private int count;

        public String asKey() {
            return questConditionKey(getType(), getParam()[0], getParamStr());
        }
    }

    @Data
    public static class Guide {
        private String type;
        private List<String> param;
        private int guideScene;
    }
}
