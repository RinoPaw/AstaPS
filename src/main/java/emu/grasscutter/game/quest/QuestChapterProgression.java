package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import java.util.Arrays;

/**
 * Reconciles chapter opening dependencies once, at resource load time.
 *
 * <p>Some compatibility QuestExcel tables replace a chapter's real predecessor with
 * STATE_EQUAL [0, FINISHED]. Starting every chapter controller at birth bypasses story progress
 * and interrupts cutscenes. Resolve the original predecessor from its matching BinOutput row
 * before constructing the runtime condition index. The ordinary quest event engine then owns
 * chapter starts, including reconnection and late progression.
 */
public final class QuestChapterProgression {
    private QuestChapterProgression() {}

    public static void reconcileResourceConditions() {
        int resolved = 0;
        int missing = 0;
        for (var chapter : GameData.getChapterDataMap().values()) {
            int beginSubQuestId = chapter.getBeginQuestId();
            if (beginSubQuestId <= 0) continue;

            var quest = GameData.getQuestDataMap().get(beginSubQuestId);
            if (quest == null) {
                missing++;
                continue;
            }

            MainQuestData main = GameData.getMainQuestDataMap().get(quest.getMainId());
            if (main == null || main.getSubQuests() == null) {
                missing++;
                continue;
            }
            var opening = Arrays.stream(main.getSubQuests())
                    .filter(row -> row != null && row.getSubId() == beginSubQuestId)
                    .findFirst().orElse(null);
            if (opening == null) {
                missing++;
                continue;
            }

            if (quest.useChapterOpeningPrerequisites(opening)) {
                resolved++;
                Grasscutter.getLogger().debug(
                        "Chapter opening uses resource prerequisites: chapter={} quest={} main={}",
                        chapter.getId(), beginSubQuestId, quest.getMainId());
            }
        }
        Grasscutter.getLogger().info(
                "Chapter progression: resolvedOpeningDependencies={} missingSourceRows={}",
                resolved, missing);
    }
}
