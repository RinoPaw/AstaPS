package emu.grasscutter.data.quest;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
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
 * Applies the Genshin-Reverse native Quest truth layer without importing unproven semantics.
 *
 * <p>The numeric ids below are intentionally small: they are the names independently confirmed by
 * the pinned 7.1 native reader. Numeric ids that are merely known from older/public resources stay
 * parsed but do not override runtime quest behavior.
 */
public final class NativeQuestParser {
    private static final Gson GSON = new Gson();

    private static final Map<Integer, QuestContent> CONFIRMED_CONTENT =
            Map.of(
                    4, QuestContent.QUEST_CONTENT_FINISH_PLOT,
                    6, QuestContent.QUEST_CONTENT_TRIGGER_FIRE,
                    21, QuestContent.QUEST_CONTENT_TEAM_DEAD,
                    23, QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT);

    private static final Map<Integer, QuestExec> CONFIRMED_EXEC =
            Map.of(
                    14, QuestExec.QUEST_EXEC_ROLLBACK_QUEST,
                    17, QuestExec.QUEST_EXEC_LOCK_POINT,
                    19, QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE);

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
        int rows = 0;
        int mergedRows = 0;
        int missingRows = 0;
        int unresolvedLists = 0;

        for (var main : safe(data.getMainQuests())) {
            if (main == null || main.getMainId() == 0) continue;
            mainQuests++;

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

        return new Report(mainQuests, rows, mergedRows, missingRows, unresolvedLists);
    }

    private static List<QuestData.QuestContentCondition> convertContents(
            List<NativeQuestData.Content> values) {
        if (values == null || values.isEmpty()) return List.of();

        var out = new ArrayList<QuestData.QuestContentCondition>(values.size());
        for (var value : values) {
            if (value == null || value.getValue() != null) return null;

            var type = CONFIRMED_CONTENT.get(value.getTypeId());
            if (type == null || !nameMatches(value.getType(), type.name())) return null;

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

            var type = CONFIRMED_EXEC.get(value.getTypeId());
            if (type == null || !nameMatches(value.getType(), type.name())) return null;

            var converted = new QuestData.QuestExecParam();
            converted.setType(type);
            converted.setParam(value.getParam() != null ? value.getParam() : new String[0]);
            out.add(converted);
        }
        return List.copyOf(out);
    }

    private static boolean nameMatches(String nativeName, String expected) {
        return nativeName == null || nativeName.isBlank() || nativeName.equals(expected);
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
        if (coverage != null && coverage.getFullConsumed() != data.getMainQuests().size()) {
            throw new IllegalArgumentException(
                    "quests.json coverage/fullConsumed does not match mainQuests size");
        }
        if (coverage != null
                && data.getFailedMainQuests() != null
                && coverage.getFailed() != data.getFailedMainQuests().size()) {
            throw new IllegalArgumentException(
                    "quests.json coverage/failed does not match failedMainQuests size");
        }
    }

    public record Report(
            int mainQuests, int rows, int mergedRows, int missingRows, int unresolvedLists) {}
}
