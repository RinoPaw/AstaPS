package emu.grasscutter.game.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/** Canonical 7.1 Statue-of-the-Seven Quest-303 mappings for scene 3. */
public final class StatueTalkQuests {
    private StatueTalkQuests() {}

    private static final Int2IntMap AREA_TO_QUEST = new Int2IntOpenHashMap();
    private static final Int2IntMap AREA_TO_NPC = new Int2IntOpenHashMap();
    private static final Int2IntMap QUEST_TO_POINT = new Int2IntOpenHashMap();
    private static final Int2ObjectMap<int[]> QUEST_TO_UNLOCK_AREAS =
            new Int2ObjectOpenHashMap<>();
    private static final IntSet GODDESS_NPC_IDS = new IntOpenHashSet();
    private static volatile boolean goddessNpcIdsLoaded;

    /**
     * SotS pillar gadgets that sometimes omit {@code maxSpringVolume} in scene3_point (Nod-Krai
     * City 7 points 1515–1517 use type {@code KDEHKECBDBO}).
     */
    private static final IntSet STATUE_GADGETS =
            new IntOpenHashSet(new int[] {70130009, 70130010, 70130011, 73176017});

    // scene3_point.json maxSpringVolume>0 npcId (starter 1201 has no npcId on point 7)
    private static final IntSet KNOWN_GODDESS_NPCS =
            new IntOpenHashSet(
                    new int[] {
                        1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1268, 1272, 1273,
                        1274, 1275, 1277, 1278, 1279, 1281, 1282, 1283, 1284, 1285, 1286, 1287,
                        1288, 1289, 1290, 4900, 4901, 4902, 4903, 4905, 4906, 5900, 5901, 5902,
                        5903, 5904, 5905, 5906, 5907, 6900, 6901, 6902, 6903, 6906, 6907, 6909,
                        6910, 6911, 7900, 7901, 7902, 7906, 7907, 7908, 7909, 7910, 7911
                    });

    static {
        // Area -> activation Talk gate. These include every Quest-303 statue present in 7.1.
        put(3, 30302);
        put(2, 30303);
        put(4, 30304);
        put(5, 30305);
        put(7, 30306);
        put(6, 30307);
        put(8, 30308);
        put(9, 30309);
        put(10, 30310);
        put(11, 30311);
        put(12, 30312);
        put(13, 30313);
        put(14, 30314);
        put(17, 30315);
        put(18, 30316);
        put(19, 30317);
        put(20, 30318);
        put(21, 30319);
        put(29, 30320);
        put(23, 30321);
        put(24, 30322);
        put(25, 30323);
        put(22, 30324);
        put(26, 30325);
        put(27, 30326);
        put(28, 30327);
        put(32, 30328);
        put(30, 30329);
        put(31, 30330);
        put(33, 30331);
        put(34, 30332);
        put(35, 30333);
        put(36, 30334);
        put(37, 30335);
        put(38, 30336);
        put(39, 30337);
        put(40, 30338);
        put(41, 30339);
        put(42, 30340);
        put(43, 30341);
        put(44, 30342);
        put(45, 30343);
        put(46, 30344);
        put(48, 30345);
        put(49, 30346);
        putArea(51, 30348, 6906);
        putArea(52, 30352, 6907);
        putArea(53, 30347, 6910);
        putArea(70, 30349, 7900);
        putArea(71, 30350, 7901);
        putArea(72, 30351, 7902);
        put(73, 30353);
        put(74, 30354);
        put(75, 30355);
        put(77, 30356);
        put(76, 30357);
        put(55, 30358);
        put(56, 30359);
        put(57, 30360);
        put(58, 30361);
        put(59, 30362);

        // Exact 7.1 Quest/303 finishExec data: quest -> point + areas unlocked by completion.
        putUnlock(30302, 3, 3);
        putUnlock(30303, 4, 2);
        putUnlock(30304, 29, 4);
        putUnlock(30305, 72, 5);
        putUnlock(30306, 73, 7);
        putUnlock(30307, 74, 6);
        putUnlock(30308, 75, 8);
        putUnlock(30309, 182, 9);
        putUnlock(30310, 204, 10);
        putUnlock(30311, 234, 11, 16);
        putUnlock(30312, 235, 12);
        putUnlock(30313, 236, 13);
        putUnlock(30314, 293, 14);
        putUnlock(30315, 318, 17);
        putUnlock(30316, 316, 18);
        putUnlock(30317, 442, 19);
        putUnlock(30318, 462, 20);
        putUnlock(30319, 463, 21);
        putUnlock(30320, 464, 29);
        putUnlock(30321, 465, 23);
        putUnlock(30322, 471, 24);
        putUnlock(30323, 472, 25);
        putUnlock(30324, 625, 22);
        putUnlock(30325, 534, 26);
        putUnlock(30326, 535, 27);
        putUnlock(30327, 536, 28);
        putUnlock(30328, 629, 32);
        putUnlock(30329, 744, 30);
        putUnlock(30330, 811, 31);
        putUnlock(30331, 770, 33);
        putUnlock(30332, 771, 34);
        putUnlock(30333, 772, 35);
        putUnlock(30334, 814, 36);
        putUnlock(30335, 815, 37);
        putUnlock(30336, 922, 38);
        putUnlock(30337, 923, 39);
        putUnlock(30338, 947, 40);
        putUnlock(30339, 948, 41);
        putUnlock(30340, 1121, 42);
        putUnlock(30341, 1166, 43);
        putUnlock(30342, 1167, 44);
        putUnlock(30343, 1168, 45);
        putUnlock(30344, 1169, 46);
        putUnlock(30345, 1232, 48);
        putUnlock(30346, 1233, 49);
        putUnlock(30347, 1429, 53);
        putUnlock(30348, 1359, 51);
        putUnlock(30349, 1515, 70);
        putUnlock(30350, 1516, 71);
        putUnlock(30351, 1517, 72);
        putUnlock(30352, 1376, 52, 54);
        putUnlock(30353, 1613, 73);
        putUnlock(30354, 1614, 74);
        putUnlock(30355, 1615, 75);
        putUnlock(30356, 1885, 77);
        putUnlock(30357, 1752, 76);
        putUnlock(30358, 1757, 55);
        putUnlock(30359, 1758, 56);
        putUnlock(30360, 1759, 57);
        putUnlock(30361, 1760, 58);
        putUnlock(30362, 1761, 59);
    }

    private static void put(int areaId, int questId) {
        AREA_TO_QUEST.put(areaId, questId);
    }

    private static void putArea(int areaId, int questId, int npcId) {
        AREA_TO_QUEST.put(areaId, questId);
        if (npcId > 0) AREA_TO_NPC.put(areaId, npcId);
    }

    private static void putUnlock(int questId, int pointId, int... areaIds) {
        QUEST_TO_POINT.put(questId, pointId);
        QUEST_TO_UNLOCK_AREAS.put(questId, areaIds);
    }

    /** @return gate quest id, or 0 if unknown */
    public static int questForArea(int areaId) {
        return AREA_TO_QUEST.getOrDefault(areaId, 0);
    }

    /** @return goddess npcId for area tip, or 0 if unknown */
    public static int npcForArea(int areaId) {
        return AREA_TO_NPC.getOrDefault(areaId, 0);
    }

    public static Int2IntMap all() {
        return AREA_TO_QUEST;
    }

    public static boolean isUnlockQuest(int questId) {
        return QUEST_TO_POINT.containsKey(questId);
    }

    public static int pointForQuest(int questId) {
        return QUEST_TO_POINT.getOrDefault(questId, 0);
    }

    public static int[] unlockAreasForQuest(int questId) {
        var areas = QUEST_TO_UNLOCK_AREAS.get(questId);
        return areas == null ? new int[0] : areas.clone();
    }

    /** Quest-303 entries whose official finishExec also increments quest progress 31801. */
    public static boolean addsQuestProgress31801(int questId) {
        return (questId >= 30305 && questId <= 30309) || questId == 30338 || questId == 30339;
    }

    /**
     * True for Statue of the Seven scene points — including Nod-Krai City 7 pillars that have no
     * {@code maxSpringVolume} in point json (client still EnterTrans on them).
     */
    public static boolean isStatuePoint(PointData pd) {
        if (pd == null) return false;
        if (pd.getMaxSpringVolume() > 0) return true;
        if (STATUE_GADGETS.contains(pd.getGadgetId())) return true;
        String type = pd.getType();
        return type != null && type.contains("KDEHKECBDBO");
    }

    /** SceneNpcBorn {@code configId} for a Statue-of-the-Seven goddess NPC. */
    public static boolean isGoddessNpc(int configId) {
        if (configId <= 0) return false;
        if (KNOWN_GODDESS_NPCS.contains(configId)) return true;
        ensureGoddessNpcIds();
        return GODDESS_NPC_IDS.contains(configId);
    }

    private static void ensureGoddessNpcIds() {
        if (goddessNpcIdsLoaded) return;
        synchronized (GODDESS_NPC_IDS) {
            if (goddessNpcIdsLoaded) return;
            for (var entry : GameData.getScenePointEntryMap().values()) {
                if (entry == null || entry.getPointData() == null) continue;
                var pd = entry.getPointData();
                if (isStatuePoint(pd) && pd.getNpcId() > 0) {
                    GODDESS_NPC_IDS.add(pd.getNpcId());
                }
            }
            GODDESS_NPC_IDS.addAll(AREA_TO_NPC.values());
            goddessNpcIdsLoaded = true;
        }
    }
}
