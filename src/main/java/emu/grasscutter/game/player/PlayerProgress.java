package emu.grasscutter.game.player;

import dev.morphia.annotations.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import it.unimi.dsi.fastutil.ints.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.*;

/** Tracks progress the player made in the world, like obtained items, seen characters and more */
@Getter
@Entity
public class PlayerProgress {
    @Setter @Transient private Player player;
    private Map<Integer, ItemEntry> itemHistory;

    /*
     * Dungeon IDs which have completed successfully. DungeonManager records every successful
     * settle here, including repeatable domains, so this also acts as durable clear history.
     */
    private IntArrayList completedDungeons;

    // keep track of EXEC_ADD_QUEST_PROGRESS count, will be used in CONTENT_ADD_QUEST_PROGRESS
    // not sure where to put this, this should be saved to DB but not to individual quest, since
    // it will be hard to loop and compare
    private Map<String, Integer> questProgressCountMap;

    private Map<Integer, ItemGiveRecord> itemGivings;
    private Map<Integer, BargainRecord> bargains;

    /** Adventurer Handbook (Investigation/preparation) targetId -> progress. */
    private Map<Integer, Integer> investigationTargetProgress;
    /** Adventurer Handbook targetId -> state (2=complete, 3=reward taken). */
    private Map<Integer, Integer> investigationTargetState;
    /** Adventurer Handbook chapterId -> state (3=reward taken). */
    private Map<Integer, Integer> investigationChapterState;
    /** Cumulative chest opens counted for handbook tasks. */
    @Setter private int handbookChestOpenCount;
    /** rankLevel -> historical obtain count for handbook relic tasks. */
    private Map<Integer, Integer> handbookReliquaryHistoryCount;

    public PlayerProgress() {
        this.questProgressCountMap = new ConcurrentHashMap<>();
        this.completedDungeons = new IntArrayList();
        this.itemHistory = new Int2ObjectOpenHashMap<>();
        this.itemGivings = new Int2ObjectOpenHashMap<>();
        this.bargains = new Int2ObjectOpenHashMap<>();
        this.investigationTargetProgress = new ConcurrentHashMap<>();
        this.investigationTargetState = new ConcurrentHashMap<>();
        this.investigationChapterState = new ConcurrentHashMap<>();
        this.handbookReliquaryHistoryCount = new ConcurrentHashMap<>();
    }

    public int getInvestigationTargetProgress(int targetId) {
        ensureInvestigationMaps();
        return investigationTargetProgress.getOrDefault(targetId, 0);
    }

    public void setInvestigationTargetProgress(int targetId, int progress) {
        ensureInvestigationMaps();
        investigationTargetProgress.put(targetId, Math.max(0, progress));
    }

    public int getInvestigationTargetState(int targetId) {
        ensureInvestigationMaps();
        return investigationTargetState.getOrDefault(targetId, 0);
    }

    public void setInvestigationTargetState(int targetId, int state) {
        ensureInvestigationMaps();
        investigationTargetState.put(targetId, state);
    }

    public int getInvestigationChapterState(int chapterId) {
        ensureInvestigationMaps();
        return investigationChapterState.getOrDefault(chapterId, 0);
    }

    public void setInvestigationChapterState(int chapterId, int state) {
        ensureInvestigationMaps();
        investigationChapterState.put(chapterId, state);
    }

    public int getHandbookChestOpenCount() {
        return handbookChestOpenCount;
    }

    public int addHandbookChestOpen(int amount) {
        if (amount > 0) {
            handbookChestOpenCount += amount;
        }
        return handbookChestOpenCount;
    }

    public int getHandbookReliquaryHistoryCount(int rankLevel) {
        ensureInvestigationMaps();
        return handbookReliquaryHistoryCount.getOrDefault(rankLevel, 0);
    }

    public void raiseHandbookReliquaryHistoryCount(int rankLevel, int count) {
        ensureInvestigationMaps();
        handbookReliquaryHistoryCount.merge(rankLevel, Math.max(0, count), Math::max);
    }

    public int addHandbookReliquaryHistory(int rankLevel, int amount) {
        ensureInvestigationMaps();
        return handbookReliquaryHistoryCount.merge(rankLevel, Math.max(0, amount), Integer::sum);
    }

    private void ensureInvestigationMaps() {
        if (investigationTargetProgress == null) {
            investigationTargetProgress = new ConcurrentHashMap<>();
        }
        if (investigationTargetState == null) {
            investigationTargetState = new ConcurrentHashMap<>();
        }
        if (investigationChapterState == null) {
            investigationChapterState = new ConcurrentHashMap<>();
        }
        if (handbookReliquaryHistoryCount == null) {
            handbookReliquaryHistoryCount = new ConcurrentHashMap<>();
        }
    }

    /**
     * Marks a dungeon as completed. Triggers the quest event.
     *
     * @param dungeonId The dungeon which was completed.
     */
    /**
     * A cleared dungeon is historical progress, but FINISH_DUNGEON is a
     * per-run quest event. A second successful run must emit the event too.
     *
     * @return true only when the dungeon was added to history for the first time
     */
    static boolean recordDungeonClear(
            IntList completed, int dungeonId, Runnable emitCompletion) {
        if (completed == null || dungeonId <= 0) return false;
        boolean firstClear;
        synchronized (completed) {
            firstClear = !completed.contains(dungeonId);
            if (firstClear) completed.add(dungeonId);
        }
        // Emit for every successful run, including repeatable domains.
        emitCompletion.run();
        return firstClear;
    }

    public void markDungeonAsComplete(int dungeonId) {
        var owner = getPlayer();
        boolean firstClear = recordDungeonClear(
                this.getCompletedDungeons(), dungeonId, () -> {
                    if (owner != null) {
                        owner.getQuestManager().queueEvent(
                                QuestContent.QUEST_CONTENT_FINISH_DUNGEON, dungeonId);
                    }
                });
        if (owner == null) {
            Grasscutter.getLogger().warn(
                    "Unable to execute 'QUEST_CONTENT_FINISH_DUNGEON' for dungeon {}: player is null.",
                    dungeonId);
            return;
        }
        // Quest replays depend on the historical first clear surviving relog.
        if (firstClear) owner.save();
        Grasscutter.getLogger().debug(
                "Dungeon {} cleared for uid={} (firstClear={}).",
                dungeonId, owner.getUid(), firstClear);
    }

    public boolean hasPlayerObtainedItemHistorically(int itemId) {
        return itemHistory.containsKey(itemId);
    }

    public int addToItemHistory(int itemId, int count) {
        val itemEntry = itemHistory.computeIfAbsent(itemId, (key) -> new ItemEntry(itemId));
        return itemEntry.addToObtainedCount(count);
    }

    public int getCurrentProgress(String progressId) {
        return questProgressCountMap.getOrDefault(progressId, -1);
    }

    public int addToCurrentProgress(String progressId, int count) {
        return questProgressCountMap.merge(progressId, count, Integer::sum);
    }

    public int resetCurrentProgress(String progressId) {
        return questProgressCountMap.merge(progressId, 0, Integer::min);
    }

    @Entity
    @NoArgsConstructor
    public static class ItemEntry {
        @Getter private int itemId;
        @Getter @Setter private int obtainedCount;

        ItemEntry(int itemId) {
            this.itemId = itemId;
        }

        int addToObtainedCount(int amount) {
            this.obtainedCount += amount;
            return this.obtainedCount;
        }
    }
}