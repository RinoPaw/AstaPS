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
    public enum QuestSource {
        QUEST_EXCEL,
        BIN_OUTPUT
    }

    public enum QuestField {
        SUB_ID,
        MAIN_ID,
        ORDER,
        DESC_TEXT_MAP_HASH,
        MP_BLOCK,
        REWIND,
        FINISH_PARENT,
        ACCEPT_COND_COMB,
        FINISH_COND_COMB,
        FAIL_COND_COMB,
        ACCEPT_COND,
        FINISH_COND,
        FAIL_COND,
        BEGIN_EXEC,
        FINISH_EXEC,
        FAIL_EXEC,
        GUIDE,
        SHOW_TYPE,
        BAN_TYPE,
        SHOW_GUIDE,
        TRIAL_AVATAR_LIST,
        GAIN_ITEMS
    }

    private enum SourcePolicy {
        QUEST_EXCEL_PRIMARY,
        BIN_OUTPUT_PRIMARY
    }

    private enum Comparison {
        SAME,
        EXCEL_ONLY,
        BIN_ONLY,
        CONFLICT
    }

    @Getter private int subId;
    @Getter private int mainId;
    @Getter private int order;
    @Getter private long descTextMapHash;

    private Boolean isMpBlock;
    private Boolean isRewind;
    private Boolean finishParent;

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

    @Getter private String showType;
    @Getter private String banType;
    @Getter private String showGuide;

    @Getter private List<Integer> trialAvatarList;
    @Getter private List<ItemParamData> gainItems;

    private transient EnumSet<QuestField> questExcelFields;
    private transient EnumMap<QuestField, QuestSource> fieldSources;

    private static final EnumMap<QuestField, long[]> normalizationAudit =
            new EnumMap<>(QuestField.class);
    private static final int MAX_CONFLICT_LOGS = 32;
    private static int conflictLogs;
    private static long binOnlyRows;

    public static String questConditionKey(
            @Nonnull Enum<?> type, int firstParam, @Nullable String paramsStr) {
        return type.name() + firstParam + (paramsStr != null ? paramsStr : "");
    }

    // ResourceLoader not happy if you remove getId() ~~
    public int getId() {
        return subId;
    }

    public boolean isMpBlock() {
        return Boolean.TRUE.equals(this.isMpBlock);
    }

    public boolean isRewind() {
        return Boolean.TRUE.equals(this.isRewind);
    }

    public boolean isFinishParent() {
        return Boolean.TRUE.equals(this.finishParent);
    }

    /**
     * Returns the source selected by the provisional runtime normalization policy.
     *
     * <p>This is provenance only. It does not prove that the selected serialized source owns the
     * field in the client. Source identity remains tracked in Genshin-Reverse #18/#19.
     */
    public QuestSource getFieldSource(QuestField field) {
        return sources().get(field);
    }

    public static void clearNormalizationAudit() {
        normalizationAudit.clear();
        conflictLogs = 0;
        binOnlyRows = 0;
    }

    public static String getNormalizationAuditSummary() {
        long same = 0;
        long excelOnly = 0;
        long binOnly = 0;
        long conflict = 0;
        for (long[] counts : normalizationAudit.values()) {
            same += counts[Comparison.SAME.ordinal()];
            excelOnly += counts[Comparison.EXCEL_ONLY.ordinal()];
            binOnly += counts[Comparison.BIN_ONLY.ordinal()];
            conflict += counts[Comparison.CONFLICT.ordinal()];
        }
        return String.format(
                Locale.ROOT,
                "binOnlyRows=%d same=%d excelOnly=%d binOnly=%d conflict=%d",
                binOnlyRows,
                same,
                excelOnly,
                binOnly,
                conflict);
    }

    public void onLoad() {
        captureQuestExcelPresence();
        sanitize();
        addToCache();
    }

    public static QuestData fromBinOutput(
            MainQuestData.SubQuestData binData, int containingMainQuestId) {
        var quest = new QuestData();
        quest.subId = binData.getSubId();
        binOnlyRows++;
        quest.mergeFromBinOutput(binData, containingMainQuestId);
        return quest;
    }

    /**
     * Merges one embedded BinOutput subquest into the canonical runtime row.
     *
     * <p>The preference table is intentionally provisional. It represents the best current runtime
     * materialization policy while exact 7.1 source identity is still unresolved. There are no
     * quest-id-specific rules here.
     */
    public void mergeFromBinOutput(
            MainQuestData.SubQuestData binData, int containingMainQuestId) {
        if (binData == null || binData.getSubId() == 0) return;

        removeFromCache();

        boolean hasExcelRow = hasQuestExcelField(QuestField.SUB_ID);
        recordComparison(
                QuestField.SUB_ID,
                hasExcelRow,
                this.subId,
                true,
                binData.getSubId(),
                this.subId != 0 ? this.subId : binData.getSubId());
        if (!hasExcelRow) {
            this.subId = binData.getSubId();
            sources().put(QuestField.SUB_ID, QuestSource.BIN_OUTPUT);
        }

        Integer binMainId =
                binData.getMainId() != null ? binData.getMainId() : containingMainQuestId;
        this.mainId =
                resolve(
                        QuestField.MAIN_ID,
                        this.mainId,
                        hasQuestExcelField(QuestField.MAIN_ID),
                        binMainId,
                        binMainId != null);
        this.order =
                resolve(
                        QuestField.ORDER,
                        this.order,
                        hasQuestExcelField(QuestField.ORDER),
                        binData.getOrder(),
                        binData.getOrder() != null);
        this.descTextMapHash =
                resolve(
                        QuestField.DESC_TEXT_MAP_HASH,
                        this.descTextMapHash,
                        hasQuestExcelField(QuestField.DESC_TEXT_MAP_HASH),
                        binData.getDescTextMapHash(),
                        binData.getDescTextMapHash() != null);

        this.isMpBlock =
                resolve(
                        QuestField.MP_BLOCK,
                        this.isMpBlock,
                        hasQuestExcelField(QuestField.MP_BLOCK),
                        binData.getMpBlock(),
                        binData.getMpBlock() != null);
        this.isRewind =
                resolve(
                        QuestField.REWIND,
                        this.isRewind,
                        hasQuestExcelField(QuestField.REWIND),
                        binData.getRewind(),
                        binData.getRewind() != null);
        this.finishParent =
                resolve(
                        QuestField.FINISH_PARENT,
                        this.finishParent,
                        hasQuestExcelField(QuestField.FINISH_PARENT),
                        binData.getFinishParent(),
                        binData.getFinishParent() != null);

        this.acceptCondComb =
                resolve(
                        QuestField.ACCEPT_COND_COMB,
                        this.acceptCondComb,
                        hasQuestExcelField(QuestField.ACCEPT_COND_COMB),
                        binData.getAcceptCondComb(),
                        binData.getAcceptCondComb() != null);
        this.finishCondComb =
                resolve(
                        QuestField.FINISH_COND_COMB,
                        this.finishCondComb,
                        hasQuestExcelField(QuestField.FINISH_COND_COMB),
                        binData.getFinishCondComb(),
                        binData.getFinishCondComb() != null);
        this.failCondComb =
                resolve(
                        QuestField.FAIL_COND_COMB,
                        this.failCondComb,
                        hasQuestExcelField(QuestField.FAIL_COND_COMB),
                        binData.getFailCondComb(),
                        binData.getFailCondComb() != null);

        var binAcceptCond = meaningfulConditions(binData.getAcceptCond());
        var binFinishCond = meaningfulConditions(binData.getFinishCond());
        var binFailCond = meaningfulConditions(binData.getFailCond());
        this.acceptCond =
                resolve(
                        QuestField.ACCEPT_COND,
                        this.acceptCond,
                        hasQuestExcelField(QuestField.ACCEPT_COND),
                        binAcceptCond,
                        !binAcceptCond.isEmpty());
        this.finishCond =
                resolve(
                        QuestField.FINISH_COND,
                        this.finishCond,
                        hasQuestExcelField(QuestField.FINISH_COND),
                        binFinishCond,
                        !binFinishCond.isEmpty());
        this.failCond =
                resolve(
                        QuestField.FAIL_COND,
                        this.failCond,
                        hasQuestExcelField(QuestField.FAIL_COND),
                        binFailCond,
                        !binFailCond.isEmpty());

        var binBeginExec = meaningfulExecs(binData.getBeginExec());
        var binFinishExec = meaningfulExecs(binData.getFinishExec());
        var binFailExec = meaningfulExecs(binData.getFailExec());
        this.beginExec =
                resolve(
                        QuestField.BEGIN_EXEC,
                        this.beginExec,
                        hasQuestExcelField(QuestField.BEGIN_EXEC),
                        binBeginExec,
                        !binBeginExec.isEmpty());
        this.finishExec =
                resolve(
                        QuestField.FINISH_EXEC,
                        this.finishExec,
                        hasQuestExcelField(QuestField.FINISH_EXEC),
                        binFinishExec,
                        !binFinishExec.isEmpty());
        this.failExec =
                resolve(
                        QuestField.FAIL_EXEC,
                        this.failExec,
                        hasQuestExcelField(QuestField.FAIL_EXEC),
                        binFailExec,
                        !binFailExec.isEmpty());

        this.guide =
                resolve(
                        QuestField.GUIDE,
                        this.guide,
                        hasQuestExcelField(QuestField.GUIDE) && meaningfulGuide(this.guide),
                        binData.getGuide(),
                        meaningfulGuide(binData.getGuide()));
        this.showType =
                resolve(
                        QuestField.SHOW_TYPE,
                        this.showType,
                        hasQuestExcelField(QuestField.SHOW_TYPE),
                        binData.getShowType(),
                        meaningfulString(binData.getShowType()));
        this.banType =
                resolve(
                        QuestField.BAN_TYPE,
                        this.banType,
                        hasQuestExcelField(QuestField.BAN_TYPE),
                        binData.getBanType(),
                        meaningfulString(binData.getBanType()));
        this.showGuide =
                resolve(
                        QuestField.SHOW_GUIDE,
                        this.showGuide,
                        hasQuestExcelField(QuestField.SHOW_GUIDE),
                        binData.getShowGuide(),
                        meaningfulString(binData.getShowGuide()));

        this.trialAvatarList =
                resolve(
                        QuestField.TRIAL_AVATAR_LIST,
                        this.trialAvatarList,
                        hasQuestExcelField(QuestField.TRIAL_AVATAR_LIST),
                        binData.getTrialAvatarList(),
                        meaningfulList(binData.getTrialAvatarList()));
        this.gainItems =
                resolve(
                        QuestField.GAIN_ITEMS,
                        this.gainItems,
                        hasQuestExcelField(QuestField.GAIN_ITEMS),
                        binData.getGainItems(),
                        meaningfulList(binData.getGainItems()));

        sanitize();
        addToCache();
    }

    private void captureQuestExcelPresence() {
        questExcelFields().add(QuestField.SUB_ID);
        questExcelFields().add(QuestField.MAIN_ID);
        questExcelFields().add(QuestField.ORDER);
        sources().put(QuestField.SUB_ID, QuestSource.QUEST_EXCEL);
        sources().put(QuestField.MAIN_ID, QuestSource.QUEST_EXCEL);
        sources().put(QuestField.ORDER, QuestSource.QUEST_EXCEL);

        markExcelIf(QuestField.DESC_TEXT_MAP_HASH, this.descTextMapHash != 0);
        markExcelIf(QuestField.MP_BLOCK, this.isMpBlock != null);
        markExcelIf(QuestField.REWIND, this.isRewind != null);
        markExcelIf(QuestField.FINISH_PARENT, this.finishParent != null);
        markExcelIf(QuestField.ACCEPT_COND_COMB, this.acceptCondComb != null);
        markExcelIf(QuestField.FINISH_COND_COMB, this.finishCondComb != null);
        markExcelIf(QuestField.FAIL_COND_COMB, this.failCondComb != null);
        markExcelIf(QuestField.ACCEPT_COND, this.acceptCond != null);
        markExcelIf(QuestField.FINISH_COND, this.finishCond != null);
        markExcelIf(QuestField.FAIL_COND, this.failCond != null);
        markExcelIf(QuestField.BEGIN_EXEC, this.beginExec != null);
        markExcelIf(QuestField.FINISH_EXEC, this.finishExec != null);
        markExcelIf(QuestField.FAIL_EXEC, this.failExec != null);
        markExcelIf(QuestField.GUIDE, meaningfulGuide(this.guide));
        markExcelIf(QuestField.SHOW_TYPE, meaningfulString(this.showType));
        markExcelIf(QuestField.BAN_TYPE, meaningfulString(this.banType));
        markExcelIf(QuestField.SHOW_GUIDE, meaningfulString(this.showGuide));
        markExcelIf(QuestField.TRIAL_AVATAR_LIST, this.trialAvatarList != null);
        markExcelIf(QuestField.GAIN_ITEMS, this.gainItems != null);
    }

    private void markExcelIf(QuestField field, boolean present) {
        if (!present) return;
        questExcelFields().add(field);
        sources().put(field, QuestSource.QUEST_EXCEL);
    }

    private boolean hasQuestExcelField(QuestField field) {
        return questExcelFields().contains(field);
    }

    private EnumSet<QuestField> questExcelFields() {
        if (this.questExcelFields == null) {
            this.questExcelFields = EnumSet.noneOf(QuestField.class);
        }
        return this.questExcelFields;
    }

    private EnumMap<QuestField, QuestSource> sources() {
        if (this.fieldSources == null) {
            this.fieldSources = new EnumMap<>(QuestField.class);
        }
        return this.fieldSources;
    }

    private <T> T resolve(
            QuestField field,
            T excelValue,
            boolean excelPresent,
            T binValue,
            boolean binPresent) {
        recordComparison(field, excelPresent, excelValue, binPresent, binValue, this.subId);

        if (policyFor(field) == SourcePolicy.BIN_OUTPUT_PRIMARY && binPresent) {
            sources().put(field, QuestSource.BIN_OUTPUT);
            return binValue;
        }
        if (excelPresent) {
            sources().put(field, QuestSource.QUEST_EXCEL);
            return excelValue;
        }
        if (binPresent) {
            sources().put(field, QuestSource.BIN_OUTPUT);
            return binValue;
        }
        return excelValue;
    }

    private static SourcePolicy policyFor(QuestField field) {
        return switch (field) {
            case SUB_ID,
                    MAIN_ID,
                    ORDER,
                    DESC_TEXT_MAP_HASH,
                    TRIAL_AVATAR_LIST,
                    GAIN_ITEMS -> SourcePolicy.QUEST_EXCEL_PRIMARY;
            case MP_BLOCK,
                    REWIND,
                    FINISH_PARENT,
                    ACCEPT_COND_COMB,
                    FINISH_COND_COMB,
                    FAIL_COND_COMB,
                    ACCEPT_COND,
                    FINISH_COND,
                    FAIL_COND,
                    BEGIN_EXEC,
                    FINISH_EXEC,
                    FAIL_EXEC,
                    GUIDE,
                    SHOW_TYPE,
                    BAN_TYPE,
                    SHOW_GUIDE -> SourcePolicy.BIN_OUTPUT_PRIMARY;
        };
    }

    private static void recordComparison(
            QuestField field,
            boolean excelPresent,
            Object excelValue,
            boolean binPresent,
            Object binValue,
            int questId) {
        Comparison comparison;
        if (!excelPresent && binPresent) {
            comparison = Comparison.BIN_ONLY;
        } else if (excelPresent && !binPresent) {
            comparison = Comparison.EXCEL_ONLY;
        } else if (!excelPresent) {
            return;
        } else if (Objects.deepEquals(excelValue, binValue)) {
            comparison = Comparison.SAME;
        } else {
            comparison = Comparison.CONFLICT;
        }

        normalizationAudit
                .computeIfAbsent(field, ignored -> new long[Comparison.values().length])
                [comparison.ordinal()]++;

        if (comparison == Comparison.CONFLICT && conflictLogs < MAX_CONFLICT_LOGS) {
            conflictLogs++;
            Grasscutter.getLogger()
                    .debug(
                            "Quest normalization conflict: quest={} field={} provisional={}",
                            questId,
                            field,
                            policyFor(field));
        }
    }

    private void sanitize() {
        this.acceptCond = meaningfulConditions(this.acceptCond);
        this.finishCond = meaningfulConditions(this.finishCond);
        this.failCond = meaningfulConditions(this.failCond);

        this.beginExec = meaningfulExecs(this.beginExec);
        this.finishExec = meaningfulExecs(this.finishExec);
        this.failExec = meaningfulExecs(this.failExec);

        if (this.acceptCondComb == null) this.acceptCondComb = LogicType.LOGIC_NONE;
        if (this.finishCondComb == null) this.finishCondComb = LogicType.LOGIC_NONE;
        if (this.failCondComb == null) this.failCondComb = LogicType.LOGIC_NONE;

        if (this.trialAvatarList == null) this.trialAvatarList = Collections.emptyList();
        if (this.gainItems == null) this.gainItems = Collections.emptyList();
    }

    private static <T extends QuestCondition<?>> List<T> meaningfulConditions(List<T> values) {
        if (values == null) return Collections.emptyList();
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.getType() != null)
                .toList();
    }

    private static List<QuestExecParam> meaningfulExecs(List<QuestExecParam> values) {
        if (values == null) return Collections.emptyList();
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value.type != null)
                .toList();
    }

    private static boolean meaningfulGuide(Guide value) {
        return value != null
                && (meaningfulString(value.type)
                        || meaningfulList(value.param)
                        || value.guideScene != 0
                        || meaningfulString(value.guideStyle)
                        || meaningfulString(value.guideLayer));
    }

    private static boolean meaningfulString(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean meaningfulList(Collection<?> value) {
        return value != null && !value.isEmpty();
    }

    private void removeFromCache() {
        if (this.acceptCond == null) return;
        if (this.acceptCond.isEmpty()) {
            removeCacheEntry(questConditionKey(QuestCond.QUEST_COND_NONE, 0, null));
            return;
        }

        for (var condition : this.acceptCond) {
            var params = condition.getParam();
            if (condition.getType() == null || params == null || params.length == 0) continue;
            removeCacheEntry(condition.asKey());
        }
    }

    private void removeCacheEntry(String key) {
        var values = GameData.getBeginCondQuestMap().get(key);
        if (values == null) return;
        values.remove(this);
        if (values.isEmpty()) GameData.getBeginCondQuestMap().remove(key);
    }

    private void addToCache() {
        var cacheMap = GameData.getBeginCondQuestMap();
        if (getAcceptCond().isEmpty()) {
            var list =
                    cacheMap.computeIfAbsent(
                            QuestData.questConditionKey(QuestCond.QUEST_COND_NONE, 0, null),
                            ignored -> new ArrayList<>());
            if (!list.contains(this)) list.add(this);
            return;
        }

        this.getAcceptCond()
                .forEach(
                        questCondition -> {
                            var params = questCondition.getParam();
                            if (questCondition.getType() == null || params == null || params.length == 0) {
                                Grasscutter.getLogger()
                                        .warn("invalid accept condition for quest {}", getSubId());
                                return;
                            }

                            var key = questCondition.asKey();
                            var list = cacheMap.computeIfAbsent(key, ignored -> new ArrayList<>());
                            if (!list.contains(this)) list.add(this);
                        });
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
        private String guideStyle;
        private String guideLayer;
    }
}
