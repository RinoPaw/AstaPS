package emu.grasscutter.data.quest;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestExec;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads the Genshin-Reverse native Quest truth layer without importing unproven semantics.
 *
 * <p>Genshin-Reverse emits a type name only for numeric ids independently aligned against the
 * pinned 7.1 Quest corpus. This consumer therefore requires both the exported name and id to match
 * a local enum constant before native data may override runtime behavior. Types that are proven by
 * the decoder but not yet represented by AstaPS remain audit-only.
 */
public final class NativeQuestParser {
    private static final Gson GSON = new Gson();

    private NativeQuestParser() {}

    public static NativeQuestData parse(Reader reader) {
        var data = GSON.fromJson(reader, NativeQuestData.class);
        validate(data);
        return data;
    }

    public static Report loadAndApply(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return apply(parse(reader));
        }
    }

    public static Report apply(NativeQuestData data) {
        validate(data);

        int mainQuests = 0;
        int materializedMainQuests = 0;
        int rows = 0;
        int mergedRows = 0;
        int missingRows = 0;
        int unresolvedLists = 0;

        for (var main : safe(data.getMainQuests())) {
            if (main == null || main.getMainId() == 0) continue;
            mainQuests++;

            var nativeRows =
                    safe(main.getQuests()).stream()
                            .filter(Objects::nonNull)
                            .filter(row -> row.getSubId() != 0)
                            .map(
                                    row -> {
                                        var sub = new MainQuestData.SubQuestData();
                                        sub.setSubId(row.getSubId());
                                        sub.setMainId(
                                                row.getMainId() != null
                                                        ? row.getMainId()
                                                        : main.getMainId());
                                        sub.setOrder(row.getOrder());
                                        return sub;
                                    })
                            .toArray(MainQuestData.SubQuestData[]::new);
            var nativeTalks =
                    safe(main.getTalks()).stream()
                            .filter(Objects::nonNull)
                            .filter(talk -> talk.getId() != 0)
                            .map(talk -> new MainQuestData.TalkData(talk.getId(), ""))
                            .toList();

            var runtimeMain = GameData.getMainQuestDataMap().get(main.getMainId());
            if (runtimeMain == null) {
                runtimeMain =
                        new MainQuestData(
                                main.getMainId(),
                                nativeRows,
                                main.getSuggestTrackMainQuestList(),
                                main.getRewardIdList(),
                                nativeTalks);
                GameData.getMainQuestDataMap().put(main.getMainId(), runtimeMain);
                materializedMainQuests++;
            } else {
                runtimeMain.mergeNativeRuntimeMetadata(
                        main.getSuggestTrackMainQuestList(), main.getRewardIdList(), nativeTalks);
            }
            nativeTalks.forEach(
                    talk -> GameData.getQuestTalkMap().put(talk.getId(), main.getMainId()));

            for (var row : safe(main.getQuests())) {
                if (row == null || row.getSubId() == 0) continue;
                rows++;

                var canonical = GameData.getQuestDataMap().get(row.getSubId());
                if (canonical == null) {
                    missingRows++;
                    continue;
                }

                var finishCond = convertContents(row.getFinishCond());
                var failCond = convertContents(row.getFailCond());
                var finishExec = convertExecs(row.getFinishExec());
                var failExec = convertExecs(row.getFailExec());

                if (finishCond == null) unresolvedLists++;
                if (failCond == null) unresolvedLists++;
                if (finishExec == null) unresolvedLists++;
                if (failExec == null) unresolvedLists++;

                canonical.mergeFromNative(
                        NativeQuestOverlay.builder()
                                .subId(row.getSubId())
                                .mainId(row.getMainId() != null ? row.getMainId() : main.getMainId())
                                .order(row.getOrder())
                                .finishCond(finishCond)
                                .failCond(failCond)
                                .finishExec(finishExec)
                                .failExec(failExec)
                                .build());
                mergedRows++;
            }
        }

        return new Report(
                mainQuests,
                materializedMainQuests,
                rows,
                mergedRows,
                missingRows,
                unresolvedLists);
    }

    private static List<QuestData.QuestContentCondition> convertContents(
            List<NativeQuestData.Content> values) {
        if (values == null || values.isEmpty()) return List.of();

        var out = new ArrayList<QuestData.QuestContentCondition>(values.size());
        for (var value : values) {
            if (value == null || value.getValue() != null) return null;

            var type = resolveContentType(value.getTypeId(), value.getType());
            if (type == null) return null;

            var converted = new QuestData.QuestContentCondition();
            converted.setType(type);
            converted.setParam(value.getParam() != null ? value.getParam() : new int[0]);
            converted.setParamStr(value.getParamStr() != null ? value.getParamStr() : "");
            out.add(converted);
        }
        return List.copyOf(out);
    }

    private static List<QuestData.QuestExecParam> convertExecs(List<NativeQuestData.Exec> values) {
        if (values == null || values.isEmpty()) return List.of();

        var out = new ArrayList<QuestData.QuestExecParam>(values.size());
        for (var value : values) {
            if (value == null) return null;

            var type = resolveExecType(value.getTypeId(), value.getType());
            if (type == null) return null;

            var converted = new QuestData.QuestExecParam();
            converted.setType(type);
            converted.setParam(value.getParam() != null ? value.getParam() : new String[0]);
            out.add(converted);
        }
        return List.copyOf(out);
    }

    private static QuestContent resolveContentType(int typeId, String typeName) {
        if (typeName == null || typeName.isBlank()) return null;
        try {
            var type = QuestContent.valueOf(typeName);
            return type.getValue() == typeId ? type : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static QuestExec resolveExecType(int typeId, String typeName) {
        if (typeName == null || typeName.isBlank()) return null;
        try {
            var type = QuestExec.valueOf(typeName);
            return type.getValue() == typeId ? type : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values != null ? values : List.of();
    }

    private static void validate(NativeQuestData data) {
        if (data == null) throw new IllegalArgumentException("quests.json is empty");
        if (data.getSchemaVersion() != 1) {
            throw new IllegalArgumentException(
                    "unsupported quests.json schemaVersion " + data.getSchemaVersion());
        }
        if (data.getGameVersion() == null || !data.getGameVersion().startsWith("7.1")) {
            throw new IllegalArgumentException(
                    "quests.json is not bound to Genshin 7.1: " + data.getGameVersion());
        }
        if (data.getMainQuests() == null) {
            throw new IllegalArgumentException("quests.json has no mainQuests array");
        }

        var coverage = data.getCoverage();
        if (coverage != null) {
            if (coverage.getTotal() != coverage.getFullConsumed() + coverage.getFailed()) {
                throw new IllegalArgumentException(
                        "quests.json coverage total does not equal fullConsumed + failed");
            }
            if (coverage.getFullConsumed() != data.getMainQuests().size()) {
                throw new IllegalArgumentException(
                        "quests.json coverage/fullConsumed does not match mainQuests size");
            }
            int failedCount =
                    data.getFailedMainQuests() != null ? data.getFailedMainQuests().size() : 0;
            if (coverage.getFailed() != failedCount) {
                throw new IllegalArgumentException(
                        "quests.json coverage/failed does not match failedMainQuests size");
            }
        }

        var mainIds = new HashSet<Integer>();
        var subIds = new HashSet<Integer>();
        for (var main : data.getMainQuests()) {
            if (main == null || main.getMainId() == 0) {
                throw new IllegalArgumentException("quests.json contains a mainQuest without mainId");
            }
            if (!mainIds.add(main.getMainId())) {
                throw new IllegalArgumentException(
                        "quests.json contains duplicate mainId " + main.getMainId());
            }
            if (main.getPayloadSize() < 0) {
                throw new IllegalArgumentException(
                        "quests.json contains negative payloadSize for mainId " + main.getMainId());
            }
            var sha = main.getPayloadSha256();
            if (sha != null && !sha.isBlank() && !sha.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException(
                        "quests.json contains invalid payloadSha256 for mainId " + main.getMainId());
            }

            for (var row : safe(main.getQuests())) {
                if (row == null || row.getSubId() == 0) {
                    throw new IllegalArgumentException(
                            "quests.json mainId " + main.getMainId() + " contains a row without subId");
                }
                if (!subIds.add(row.getSubId())) {
                    throw new IllegalArgumentException(
                            "quests.json contains duplicate subId " + row.getSubId());
                }
                if (row.getMainId() != null && row.getMainId() != main.getMainId()) {
                    throw new IllegalArgumentException(
                            "quests.json subId "
                                    + row.getSubId()
                                    + " belongs to mainId "
                                    + row.getMainId()
                                    + " but is nested under "
                                    + main.getMainId());
                }
            }
        }

        var failedIds = new HashSet<Integer>();
        for (var failed : safe(data.getFailedMainQuests())) {
            if (failed == null || failed.getMainId() == 0) {
                throw new IllegalArgumentException(
                        "quests.json contains a failedMainQuest without mainId");
            }
            if (!failedIds.add(failed.getMainId())) {
                throw new IllegalArgumentException(
                        "quests.json contains duplicate failed mainId " + failed.getMainId());
            }
            if (mainIds.contains(failed.getMainId())) {
                throw new IllegalArgumentException(
                        "quests.json mainId "
                                + failed.getMainId()
                                + " appears in both success and failure sets");
            }
        }
    }

    public record Report(
            int mainQuests,
            int materializedMainQuests,
            int rows,
            int mergedRows,
            int missingRows,
            int unresolvedLists) {}
}
