package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GetDailyDungeonEntryInfoRspOuterClass.GetDailyDungeonEntryInfoRsp;
import emu.grasscutter.server.packet.send.PacketGetDailyDungeonEntryInfoRsp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("GameData")
@ExtendWith(ServerResourceFixture.class)
final class DungeonEntryUnlockTest {
    private static final Gson GSON = new Gson();

    @Test
    void decodesSevenOneConditionsAndFirstUnlockReward() {
        var entry = entry(
                """
                {"id":10,"sceneId":3,"dungeonEntryId":44,"rewardDataId":220403,
                 "condComb":"LOGIC_OR","NCPHMNJMPKB":[
                   {"type":"DUNGEON_ENTRY_CONDITION_LEVEL","param1":10},
                   {"type":"DUNGEON_ENTRY_CONDITION_QUEST","param1":35802}]}
                """);

        assertEquals(220403, entry.getRewardDataId());
        assertEquals(10, entry.getLevelCondition());
        assertEquals(35802, entry.getQuestCondition());
        assertFalse(DungeonEntryUnlock.conditionsMet(entry, 9, id -> false));
        assertTrue(DungeonEntryUnlock.conditionsMet(entry, 10, id -> false));
        assertTrue(DungeonEntryUnlock.conditionsMet(entry, 1, id -> id == 35802));
    }

    @Test
    void defaultAndConditionlessEntriesDoNotRequireArtificialGates() {
        var defaultEntry = entry(
                "{\"id\":1,\"isDefaultOpen\":true,\"NCPHMNJMPKB\":[{},{}]}");
        var conditionless = entry("{\"id\":1002,\"NCPHMNJMPKB\":[{},{}]}");
        var andEntry = entry(
                """
                {"id":11,"condComb":"LOGIC_AND",
                 "NCPHMNJMPKB":[
                   {"type":"DUNGEON_ENTRY_CONDITION_LEVEL","param1":10},
                   {"type":"DUNGEON_ENTRY_CONDITION_QUEST","param1":35802}]}
                """);

        assertTrue(DungeonEntryUnlock.conditionsMet(defaultEntry, 1, id -> false));
        assertTrue(DungeonEntryUnlock.conditionsMet(conditionless, 1, id -> false));
        assertFalse(DungeonEntryUnlock.conditionsMet(andEntry, 10, id -> false));
        assertTrue(DungeonEntryUnlock.conditionsMet(andEntry, 10, id -> true));
    }

    @Test
    void entranceRewardUsesEntryConfigNotWaypointRewardTable() {
        int configId = 990881;
        int sceneId = 990;
        int pointId = 772;
        var entries = GameData.getDungeonEntryDataMap();
        var previous = entries.put(configId, entry(
                "{\"id\":990881,\"sceneId\":990,\"dungeonEntryId\":772,\"rewardDataId\":220403}"));
        try {
            assertEquals(220403, DungeonEntryUnlock.rewardId(sceneId, pointId));
            assertEquals(0, DungeonEntryUnlock.rewardId(sceneId, pointId + 1));
        } finally {
            if (previous == null) entries.remove(configId);
            else entries.put(configId, previous);
        }
    }

    @Test
    void handbookReflectsUnlockAndCompletionSeparately() throws Exception {
        int configId = 990882;
        int sceneId = 991;
        int pointId = 773;
        int dungeonId = 990883;
        int pointKey = (sceneId << 16) + pointId;
        var entries = GameData.getDungeonEntryDataMap();
        var points = GameData.getScenePointEntryMap();
        var previousEntry = entries.put(configId, entry(
                "{\"id\":990882,\"sceneId\":991,\"dungeonEntryId\":773,"
                + "\"isShowInAdvHandbook\":true}"));
        var previousPoint = points.put(pointKey,
                new ScenePointEntry(sceneId, GSON.fromJson(
                        "{\"id\":773,\"$type\":\"DungeonEntry\",\"dungeonIds\":[990883]}",
                        PointData.class)));
        try {
            var player = new Player();

            var initially = response(player, sceneId);
            assertFalse(initially.getDailyDungeonInfoList(0).getIsQuickOpen());
            assertFalse(initially.getDailyDungeonInfoList(0)
                    .getRecommendDungeonEntryInfo().getIsPassed());

            player.getUnlockedScenePoints(sceneId).add(pointId);
            var unlocked = response(player, sceneId);
            assertTrue(unlocked.getDailyDungeonInfoList(0).getIsQuickOpen());
            assertFalse(unlocked.getDailyDungeonInfoList(0)
                    .getRecommendDungeonEntryInfo().getIsPassed());

            player.getPlayerProgress().getCompletedDungeons().add(dungeonId);
            var finished = response(player, sceneId);
            assertTrue(finished.getDailyDungeonInfoList(0).getIsQuickOpen());
            assertTrue(finished.getDailyDungeonInfoList(0)
                    .getRecommendDungeonEntryInfo().getIsPassed());

            player.getForceLockedScenePoints(sceneId).add(pointId);
            assertFalse(response(player, sceneId)
                    .getDailyDungeonInfoList(0).getIsQuickOpen());
        } finally {
            if (previousEntry == null) entries.remove(configId);
            else entries.put(configId, previousEntry);
            if (previousPoint == null) points.remove(pointKey);
            else points.put(pointKey, previousPoint);
        }
    }

    private static GetDailyDungeonEntryInfoRsp response(Player player, int sceneId)
            throws Exception {
        return GetDailyDungeonEntryInfoRsp.parseFrom(
                new PacketGetDailyDungeonEntryInfoRsp(player, sceneId).getData());
    }

    private static DungeonEntryData entry(String json) {
        return GSON.fromJson(json, DungeonEntryData.class);
    }
}
