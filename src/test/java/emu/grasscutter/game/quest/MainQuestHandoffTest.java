package emu.grasscutter.game.quest;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
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
    private static final Path offeringLevels = Path.of("data", "offering_levels.json");
    private static boolean offeringLevelsExisted;

    @BeforeAll
    static void initializeConfig() throws Exception {
        offeringLevelsExisted = Files.exists(offeringLevels);
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
        // OfferingHelper creates an empty file when the real progress-manager login runs.
        // Preserve any existing file or any progress written by another process.
        if (!offeringLevelsExisted
                && Files.exists(offeringLevels)
                && Files.readString(offeringLevels).trim().equals("{}")) {
            Files.delete(offeringLevels);
        }
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

    static final class RecoverableMainQuest extends GameMainQuest {
        RecoverableMainQuest(Player player, int id) {
            super(player, id);
        }

        @Override
        public void save() {}
    }

    static final class InMemoryPlayer extends Player {
        @Override
        public void save() {}
    }

    private static void load352AndStatueQuests() {
        loadOpening(352, 0, 3);
        for (int id = 35201; id <= 35205; id++) {
            var quest = JsonUtils.decode("""
                    {"subId":%d,"mainId":352,"order":%d,
                     "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[%d,3]}],
                     "finishCond":[],"failCond":[],"beginExec":[],"finishExec":[],"failExec":[]}
                    """.formatted(id, id - 35199, id - 1), QuestData.class);
            quest.onLoad();
            GameData.getQuestDataMap().put(id, quest);
        }
        var main = JsonUtils.decode("""
                {"id":352,"subQuests":[
                 {"subId":35200,"order":1,"isRewind":true},
                 {"subId":35201,"order":2,"isRewind":true},
                 {"subId":35202,"order":3,"isRewind":true},
                 {"subId":35203,"order":4},
                 {"subId":35204,"order":5,"isRewind":true},
                 {"subId":35205,"order":6,"finishParent":true}]}
                """, MainQuestData.class);
        GameData.getMainQuestDataMap().put(352, main);
        main.onLoad();
        var statue = JsonUtils.decode("""
                {"subId":30302,"mainId":303,"order":1,"acceptCond":[],
                 "finishCond":[],"failCond":[],"beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        statue.onLoad();
        GameData.getQuestDataMap().put(30302, statue);
        GameData.getMainQuestDataMap().put(303, JsonUtils.decode("""
                {"id":303,"subQuests":[{"subId":30302,"order":1}]}
                """, MainQuestData.class));
    }

    private static Player playerWithStatueAnd352() {
        load352AndStatueQuests();
        var player = new InMemoryPlayer();
        player.setSession(new PacketSink());
        var statues = new RecoverableMainQuest(player, 303);
        statues.getChildQuestById(30302).setState(QuestState.QUEST_STATE_FINISHED);
        player.getQuestManager().getMainQuests().put(303, statues);
        var parent = new RecoverableMainQuest(player, 352);
        parent.getChildQuests()
                .put(35200, new OpeningQuest(parent, GameData.getQuestDataMap().get(35200)));
        parent.getChildQuests()
                .put(35205, new OpeningQuest(parent, GameData.getQuestDataMap().get(35205)));
        player.getQuestManager().getMainQuests().put(352, parent);
        return player;
    }

    @Test
    void statueLoginDoesNotConsume35205AndTheOpeningCanBeHandedOff() {
        boolean enabled = GAME_OPTIONS.questing.enabled;
        try {
            GAME_OPTIONS.questing.enabled = true;
            var player = playerWithStatueAnd352();
            var terminal = (OpeningQuest) player.getQuestManager().getQuestById(35205);

            player.getProgressManager().onPlayerLogin();

            assertEquals(QuestState.QUEST_STATE_UNSTARTED, terminal.getState());
            assertEquals(0, terminal.starts);
            assertEquals(0, terminal.getFinishTime());
            player.getQuestManager().startMainQuestIfUnlinked(352);
            assertEquals(
                    QuestState.QUEST_STATE_UNFINISHED,
                    player.getQuestManager().getQuestById(35200).getState());
        } finally {
            GAME_OPTIONS.questing.enabled = enabled;
        }
    }

    @Test
    void loginRewindClearsTheSynthetic35205FinishAndStatueSetupDoesNotRestoreIt() {
        boolean enabled = GAME_OPTIONS.questing.enabled;
        try {
            GAME_OPTIONS.questing.enabled = true;
            var player = playerWithStatueAnd352();
            var manager = player.getQuestManager();
            var parent = manager.getMainQuestById(352);
            var terminal = manager.getQuestById(35205);
            terminal.setState(QuestState.QUEST_STATE_FINISHED);
            terminal.setFinishTime(123);
            terminal.setStartTime(123);
            terminal.setAcceptTime(123);
            assertFalse(manager.canStartMainQuestIfUnlinked(352));

            // QuestManager.onLogin visits active parents' rewind before PlayerProgress.onPlayerLogin.
            parent.rewind();
            player.getProgressManager().onPlayerLogin();

            assertEquals(QuestState.QUEST_STATE_UNFINISHED, manager.getQuestById(35200).getState());
            assertEquals(QuestState.QUEST_STATE_UNSTARTED, terminal.getState());
            assertEquals(0, terminal.getFinishTime());
            assertEquals(0, terminal.getStartTime());
            assertEquals(0, terminal.getAcceptTime());
            assertFalse(parent.isFinished());
        } finally {
            GAME_OPTIONS.questing.enabled = enabled;
        }
    }

    @Test
    void questingOffForgesStarterStatueTalkGateWithoutCreating352() {
        boolean enabled = GAME_OPTIONS.questing.enabled;
        try {
            GAME_OPTIONS.questing.enabled = false;
            load352AndStatueQuests();
            var player = new InMemoryPlayer();
            player.setSession(new PacketSink());
            var statues = new RecoverableMainQuest(player, 303);
            statues.getChildQuestById(30302).setState(QuestState.QUEST_STATE_FINISHED);
            player.getQuestManager().getMainQuests().put(303, statues);

            assertNull(player.getQuestManager().getMainQuestById(352));
            var forged = player.getProgressManager().buildForgedStatueTalkQuests();
            var starterGate =
                    forged.stream()
                            .filter(q -> q.getQuestId() == 35205)
                            .findFirst()
                            .orElseThrow();

            assertEquals(352, starterGate.getParentQuestId());
            assertEquals(QuestState.QUEST_STATE_FINISHED.getValue(), starterGate.getState());
            assertNull(player.getQuestManager().getMainQuestById(352));
            assertNull(player.getQuestManager().getQuestById(35205));
        } finally {
            GAME_OPTIONS.questing.enabled = enabled;
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

        @Override
        public void save() {
            saves++;
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
        loadOpening(352, 0, 3);
        var mainData = JsonUtils.decode("{\"id\":351,\"suggestTrackMainQuestList\":[352]}", MainQuestData.class);
        GameData.getMainQuestDataMap().put(351, mainData);
        var player = new Player();
        player.setSession(new PacketSink());
        var parent = decodeSaved("""
                {"parentQuestId":351,"ownerUid":0,"state":"PARENT_QUEST_STATE_NONE",
                 "childQuests":{},"questVars":[0,0,0,0,0]}
                """, FinishingMainQuest.class);
        parent.setOwner(player);
        var successor = new GameMainQuest(player, 352);
        var opening = new OpeningQuest(successor, GameData.getQuestDataMap().get(35200));
        successor.getChildQuests().put(35200, opening);
        player.getQuestManager().getMainQuests().put(352, successor);

        parent.finish();
        parent.finish();
        assertTrue(parent.isFinished());
        assertEquals(1, parent.saves);
        assertEquals(1, opening.starts);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, opening.getState());
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
