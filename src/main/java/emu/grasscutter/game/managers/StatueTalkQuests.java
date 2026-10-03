package emu.grasscutter.game.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/** Maps scene-3 Statue of the Seven area ids to their quest-303 activation children. */
public final class StatueTalkQuests {
    private StatueTalkQuests() {}

    private static final Int2IntMap AREA_TO_QUEST = new Int2IntOpenHashMap();
    private static final Int2IntMap AREA_TO_NPC = new Int2IntOpenHashMap();
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
        // Mondstadt / Liyue / Inazuma / Chasm
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

        // Sumeru rainforest
        put(20, 30318);
        put(21, 30319);
        put(29, 30320);
        put(23, 30321);
        put(24, 30322);
        put(25, 30323);
        put(22, 30324);

        // Sumeru desert
        put(26, 30325);
        put(27, 30326);
        put(28, 30327);
        put(32, 30328);
        put(30, 30329);
        put(31, 30330);

        // Fontaine
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

        // Natlan / Nod-Krai / Snezhnaya-era 7.1 data.
        put(43, 30341);
        put(44, 30342);
        put(45, 30343);
        put(46, 30344);
        put(48, 30345);
        put(49, 30346);
        putArea(53, 30347, 6910);
        putArea(51, 30348, 6906);
        putArea(70, 30349, 7900); // point 1515
        putArea(71, 30350, 7901); // point 1516
        putArea(72, 30351, 7902); // point 1517
        putArea(52, 30352, 6907);
        put(73, 30353); // point 1613
        put(74, 30354); // point 1614
        put(75, 30355); // point 1615
        put(77, 30356); // point 1885
        put(76, 30357); // point 1752
        put(55, 30358); // point 1757
        put(56, 30359); // point 1758
        put(57, 30360); // point 1759
        put(58, 30361); // point 1760
        put(59, 30362); // point 1761
    }

    private static void put(int areaId, int questId) {
        AREA_TO_QUEST.put(areaId, questId);
    }

    private static void putArea(int areaId, int questId, int npcId) {
        AREA_TO_QUEST.put(areaId, questId);
        if (npcId > 0) {
            AREA_TO_NPC.put(areaId, npcId);
        }
    }

    /** @return activation quest id, or 0 if unknown */
    public static int questForArea(int areaId) {
        if (!AREA_TO_QUEST.containsKey(areaId)) {
            return 0;
        }
        return AREA_TO_QUEST.get(areaId);
    }

    /** @return goddess npcId for area tip, or 0 if unknown */
    public static int npcForArea(int areaId) {
        if (!AREA_TO_NPC.containsKey(areaId)) {
            return 0;
        }
        return AREA_TO_NPC.get(areaId);
    }

    public static Int2IntMap all() {
        return AREA_TO_QUEST;
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

    /**
     * SceneNpcBorn {@code configId} for a Statue of the Seven goddess NPC. Hardcoded because
     * PointData.npcId is easy to miss in Gson and an empty set silently dropped Fontaine F.
     */
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
