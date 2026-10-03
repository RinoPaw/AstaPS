package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import java.util.List;

/** Restores chapter-controller quests that are linked to active main-quest series by resources. */
public final class QuestChapterBootstrap {
    private QuestChapterBootstrap() {}

    /**
     * Starts missing hidden chapter controllers for the player's currently active quest series.
     *
     * <p>Some 7.1 extracted chapter controllers open with {@code STATE_EQUAL [0, FINISHED]}. Quest 0
     * does not exist, so that condition cannot become true through the ordinary condition sweep.
     * The relationship itself is still intact in the resources: a main quest carries its series,
     * ChapterData maps that series to a begin subquest, and that subquest identifies the controller
     * main quest. Follow that chain rather than hard-coding controller quest IDs.
     */
    public static void startForActiveMainQuests(QuestManager questManager) {
        if (questManager == null) return;

        // Starting a controller mutates mainQuests, so snapshot the source quests first.
        List<Integer> sourceMainQuestIds =
                questManager.getMainQuests().values().stream()
                        .filter(mainQuest -> !mainQuest.isFinished())
                        .map(GameMainQuest::getParentQuestId)
                        .toList();

        for (int sourceMainQuestId : sourceMainQuestIds) {
            var sourceData = GameData.getMainQuestDataMap().get(sourceMainQuestId);
            if (sourceData == null || sourceData.getSeries() <= 0) continue;

            var chapter = GameData.getChapterDataMap().get(sourceData.getSeries());
            if (chapter == null || chapter.getBeginQuestId() <= 0) continue;

            var beginQuestData = GameData.getQuestDataMap().get(chapter.getBeginQuestId());
            if (beginQuestData == null) continue;

            int controllerMainQuestId = beginQuestData.getMainId();
            if (controllerMainQuestId == sourceMainQuestId) continue;

            if (QuestManager.opensUnlinked(controllerMainQuestId)) {
                Grasscutter.getLogger()
                        .debug(
                                "Chapter bootstrap: uid={} sourceMain={} series={} chapter={} beginSub={} controllerMain={}",
                                questManager.getPlayer().getUid(),
                                sourceMainQuestId,
                                sourceData.getSeries(),
                                chapter.getId(),
                                chapter.getBeginQuestId(),
                                controllerMainQuestId);
                questManager.startMainQuestIfUnlinked(controllerMainQuestId);
            }
        }
    }
}
