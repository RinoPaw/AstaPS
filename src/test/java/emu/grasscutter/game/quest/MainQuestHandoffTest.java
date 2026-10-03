package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;
import com.google.gson.JsonParser;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public final class MainQuestHandoffTest {
    private static final List<Path> createdDirectories = new ArrayList<>();

    @BeforeAll
    static void initializeConfig() throws Exception {
        // Grasscutter's startup check requires the two resource directories even though these
        // tests supply every quest row themselves. Preserve any existing resource pack/config.
        var root = Path.of("resources");
        var config = Path.of("config.json");
        if (Files.exists(config)) {
            var json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            if (json.has("folderStructure") && json.getAsJsonObject("folderStructure").has("resources")) {
                root = Path.of(json.getAsJsonObject("folderStructure").get("resources").getAsString());
            }
        }
        for (var path : List.of(root, root.resolve("BinOutput"), root.resolve("ExcelBinOutput"))) {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                createdDirectories.add(path);
            }
        }
        // Player uses Configuration; initialize its owner before accessing those static aliases.
        Grasscutter.getLogger();
    }

    @AfterAll
    static void removeEmptyFixtureDirectories() throws Exception {
        for (int i = createdDirectories.size() - 1; i >= 0; i--) {
            // Delete only the empty directories created here; never remove an existing pack.
            try {
                Files.deleteIfExists(createdDirectories.get(i));
            } catch (java.nio.file.DirectoryNotEmptyException ignored) {
            }
        }
    }

    @AfterEach
    void clearData() {
        GameData.getMainQuestDataMap().clear();
        GameData.getQuestDataMap().clear();
        GameData.getBeginCondQuestMap().clear();
    }

    private static void loadOpening(int questId, int predecessor, int state) {
        var quest = JsonUtils.decode("""
                {"subId":35200,"mainId":352,"order":1,
                 "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[%d,%d]}],
                 "finishCond":[],"failCond":[],"beginExec":[],"finishExec":[],"failExec":[]}
                """.formatted(predecessor, state), QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getId(), quest);
        var main = JsonUtils.decode("""
                {"id":%d,"subQuests":[{"subId":35200,"order":1,"isRewind":true}]}
                """.formatted(questId), MainQuestData.class);
        GameData.getMainQuestDataMap().put(main.getId(), main);
        main.onLoad();
    }

    @Test
    void binOutputMergeKeepsThe35200AcceptCondition() {
        loadOpening(352, 0, 3);
        var quest = GameData.getQuestDataMap().get(35200);
        assertArrayEquals(new int[] {0, 3}, quest.getAcceptCond().get(0).getParam());
        assertTrue(quest.isRewind());
        assertTrue(QuestManager.opensUnlinked(352));

        var end = JsonUtils.decode("""
                {"subId":35102,"mainId":351,"acceptCond":[],"finishCond":[],"failCond":[],
                 "beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        end.onLoad();
        GameData.getQuestDataMap().put(35102, end);
        var main = JsonUtils.decode("""
                {"id":351,"suggestTrackMainQuestList":[352],
                 "subQuests":[{"subId":35102,"order":8,"finishParent":true}]}
                """, MainQuestData.class);
        main.onLoad();
        assertTrue(end.isFinishParent());
        assertArrayEquals(new int[] {352}, main.getSuggestTrackMainQuestList());
    }

    @Test
    void realPrerequisitesAndQuestZeroInAnUnstartedStateAreNotForced() {
        loadOpening(352, 35102, 3);
        assertFalse(QuestManager.opensUnlinked(352));
        loadOpening(352, 0, 0);
        assertFalse(QuestManager.opensUnlinked(352));
    }

    private static GameMainQuest savedParent(String state, boolean finished, String children) {
        return decodeSaved("""
                {"parentQuestId":352,"state":"%s","isFinished":%s,"childQuests":%s}
                """.formatted(state, finished, children), GameMainQuest.class);
    }

    private static <T> T decodeSaved(String json, Class<T> type) {
        // MongoDB does not persist runtime links. Honor its @Transient annotation in fixtures too.
        return new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                return field.getAnnotation(dev.morphia.annotations.Transient.class) != null;
            }

            @Override
            public boolean shouldSkipClass(Class<?> ignored) {
                return false;
            }
        }).create().fromJson(json, type);
    }

    @Test
    void aSavedParentWithOnlyUnstartedChildrenCanStillBeHandedOff() {
        loadOpening(352, 0, 3);
        var manager = new QuestManager(new Player());
        assertTrue(manager.canStartMainQuestIfUnlinked(352));
        manager.getMainQuests().put(352, savedParent("PARENT_QUEST_STATE_NONE", false,
                "{\"35200\":{\"state\":\"QUEST_STATE_UNSTARTED\"}}"));
        assertTrue(manager.canStartMainQuestIfUnlinked(352));
    }

    static final class OpeningQuest extends GameQuest {
        int starts;

        OpeningQuest(GameMainQuest parent, QuestData data) {
            super(parent, data);
        }

        @Override
        public void start() {
            starts++;
            setState(QuestState.QUEST_STATE_UNFINISHED);
        }
    }

    @Test
    void handoffStartsTheOpeningOfAnExistingEmptyParentExactlyOnce() {
        loadOpening(352, 0, 3);
        var player = new Player();
        var manager = player.getQuestManager();
        var parent = new GameMainQuest(player, 352);
        var opening = new OpeningQuest(parent, GameData.getQuestDataMap().get(35200));
        parent.getChildQuests().put(35200, opening);
        manager.getMainQuests().put(352, parent);

        manager.startMainQuestIfUnlinked(352);
        manager.startMainQuestIfUnlinked(352);
        assertEquals(1, opening.starts);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, opening.getState());
        assertSame(parent, manager.getMainQuestById(352));
    }

    @Test
    void handoffNeverRestartsAStartedFailedOrFinishedQuestline() {
        loadOpening(352, 0, 3);
        var manager = new QuestManager(new Player());
        for (String state : List.of("QUEST_STATE_UNFINISHED", "QUEST_STATE_FINISHED", "QUEST_STATE_FAILED")) {
            manager.getMainQuests().put(352, savedParent("PARENT_QUEST_STATE_NONE", false,
                    "{\"35200\":{\"state\":\"QUEST_STATE_UNSTARTED\"},\"35201\":{\"state\":\"" + state + "\"}}"));
            assertFalse(manager.canStartMainQuestIfUnlinked(352), state);
        }
        manager.getMainQuests().put(352, savedParent("PARENT_QUEST_STATE_NONE", true, "{}"));
        assertFalse(manager.canStartMainQuestIfUnlinked(352));
        manager.getMainQuests().put(352, savedParent("PARENT_QUEST_STATE_FINISHED", false, "{}"));
        assertFalse(manager.canStartMainQuestIfUnlinked(352));
    }

    static final class SavedMainQuest extends GameMainQuest {
        List<String> calls = new ArrayList<>();
        transient QuestManager manager;

        @Override
        public void finish() {
            calls.add("finish");
        }

        @Override
        public void tryStartFollowingQuests() {
            calls.add("handoff");
            // Successors are added during recovery, so iterating the live map is unsafe.
            manager.getMainQuests().put(352, new GameMainQuest());
        }
    }

    static final class FinishingMainQuest extends GameMainQuest {
        int saves;
        int handoffs;

        @Override
        public void save() {
            saves++;
        }

        @Override
        public void tryStartFollowingQuests() {
            assertTrue(isFinished());
            handoffs++;
        }
    }

    static final class PacketSink extends GameSession {
        PacketSink() {
            super(null);
        }

        @Override
        public void send(BasePacket packet) {}
    }

    @Test
    void finishing351ExecutesTheHandoffAndADuplicateFinishHasNoSideEffects() {
        var mainData = JsonUtils.decode("{\"id\":351,\"suggestTrackMainQuestList\":[352]}", MainQuestData.class);
        GameData.getMainQuestDataMap().put(351, mainData);
        var player = new Player();
        player.setSession(new PacketSink());
        var parent = decodeSaved("""
                {"parentQuestId":351,"ownerUid":0,"state":"PARENT_QUEST_STATE_NONE",
                 "childQuests":{},"questVars":[0,0,0,0,0]}
                """, FinishingMainQuest.class);
        parent.setOwner(player);

        parent.finish();
        parent.finish();
        assertTrue(parent.isFinished());
        assertEquals(1, parent.saves);
        assertEquals(1, parent.handoffs);
    }

    @Test
    void recoveryReplaysOnlyHandoffsFromFinishedParentsWithoutFinishingOrRewardingAgain() {
        var manager = new QuestManager(new Player());
        var finished = decodeSaved("""
                {"parentQuestId":351,"isFinished":true,"state":"PARENT_QUEST_STATE_FINISHED"}
                """, SavedMainQuest.class);
        finished.manager = manager;
        var active = decodeSaved("""
                {"parentQuestId":353,"isFinished":false,"state":"PARENT_QUEST_STATE_NONE"}
                """, SavedMainQuest.class);
        manager.getMainQuests().put(351, finished);
        manager.getMainQuests().put(353, active);

        manager.resumeMainQuestHandoffs();
        assertEquals(List.of("handoff"), finished.calls);
        assertTrue(active.calls.isEmpty());
        assertNotNull(manager.getMainQuestById(352));
    }
}
