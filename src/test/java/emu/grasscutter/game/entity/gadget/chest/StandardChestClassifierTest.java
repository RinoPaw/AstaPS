package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Exclusion;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Kind;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.scripts.data.SceneBossChest;
import emu.grasscutter.scripts.data.SceneGadget;
import org.junit.jupiter.api.Test;

class StandardChestClassifierTest {
    private static SceneGadget meta(String tag) {
        var gadget = new SceneGadget();
        gadget.drop_tag = tag;
        return gadget;
    }

    private static StandardChestClassifier.Classification identify(
            String prefab, SceneGadget meta) {
        return StandardChestClassifier.classify(
                EntityType.Chest, prefab, meta, SceneType.SCENE_WORLD, 123400001);
    }

    private static void standard(String prefab, String tag, Kind kind) {
        var result = identify(prefab, meta(tag));
        assertTrue(result.isStandard(), () -> prefab + ", " + tag + ": " + result.exclusion());
        assertEquals(kind, result.kind());
        assertEquals(Exclusion.NONE, result.exclusion());
    }

    private static void excluded(
            String prefab, SceneGadget meta, Exclusion reason) {
        var result = identify(prefab, meta);
        assertFalse(result.isStandard());
        assertNull(result.kind(), "Excluded objects must never expose a reward quality");
        assertEquals(reason, result.exclusion());
    }

    @Test
    void recognizesAllFourRegularChestGrades() {
        standard("SceneObj_Chest_Default_Lv1", "解谜低级蒙德", Kind.COMMON);
        standard("SceneObj_Chest_Locked_Lv2", "战斗中级璃月", Kind.EXQUISITE);
        standard("SceneObj_Chest_Default_Lv4", "解谜高级稻妻", Kind.PRECIOUS);
        standard("SceneObj_Chest_Locked_Lv5", "战斗超级须弥", Kind.LUXURIOUS);
    }

    @Test
    void supportsOnlyKnownRegularVariations() {
        standard("SceneObj_Chest_Bramble_Lv1", "战斗低级蒙德", Kind.COMMON);
        standard("SceneObj_Chest_Frozen_Lv2", "解谜中级璃月", Kind.EXQUISITE);
        standard("SceneObj_Chest_Default_Lv2_Amphibious", "解谜中级枫丹", Kind.EXQUISITE);
        standard("SceneObj_Chest_Locked_Lv4_UnderWater", "战斗高级枫丹", Kind.PRECIOUS);
        standard("SceneObj_Chest_Default_Lv5_V70", "解谜超级纳塔", Kind.LUXURIOUS);
        standard("SceneObj_Chest_Locked_Lv2_RemusLanternFish", "解谜中级枫丹", Kind.EXQUISITE);
        standard("SceneObj_Chest_Rock_Lv1", "解谜低级璃月", Kind.COMMON);
        standard("SceneObj_Chest_Default_Lv1", "雪山解谜低级蒙德", Kind.COMMON);
        standard("SceneObj_Chest_Locked_Lv2", "雪山战斗中级蒙德", Kind.EXQUISITE);
        standard("SceneObj_Chest_Default_Lv1", "战斗低级群岛", Kind.COMMON);
    }

    @Test
    void excludesBossFlowersBeforeQualitySelection() {
        var script = meta("解谜超级蒙德");
        script.boss_chest = new SceneBossChest();
        excluded("SceneObj_Chest_Default_Lv5", script, Exclusion.BOSS_FLOWER);
    }

    @Test
    void preservesAllExplicitNativeAndQuestDropReferences() {
        for (int dropId : new int[] {1050256, 2004900, 52040000}) {
            var script = meta("战斗低级蒙德");
            script.chest_drop_id = dropId;
            excluded("SceneObj_Chest_Default_Lv1", script, Exclusion.EXPLICIT_DROP);
        }
        var sentinel = meta("战斗低级蒙德");
        sentinel.chest_drop_id = 1000100;
        assertTrue(identify("SceneObj_Chest_Default_Lv1", sentinel).isStandard());
        var direct = meta("解谜低级蒙德");
        direct.drop_id = 8001000;
        excluded("SceneObj_Chest_Default_Lv1", direct, Exclusion.EXPLICIT_DROP);
    }

    @Test
    void excludesShrinesEvenWhenUsingOrdinaryLuxuriousPrefab() {
        excluded("SceneObj_Chest_Default_Lv5", meta("灵龛蒙德"), Exclusion.SPECIAL_SOURCE);
        excluded("SceneObj_Chest_Default_Lv5", meta("灵龛璃月"), Exclusion.SPECIAL_SOURCE);
        excluded("SceneObj_Chest_Default_Lv5", meta("灵龛稻妻"), Exclusion.SPECIAL_SOURCE);
    }

    @Test
    void excludesEventAndUnusualRewardTagFamilies() {
        for (String tag :
                new String[] {
                    "逐月节低级蒙德",
                    "渊下宫活动低级稻妻",
                    "雪山玉髓高级蒙德",
                    "摩拉石箱须弥",
                    "书页章节1稻妻",
                    "搜刮点解谜通用须弥",
                    "水上搜刮点璃月"
                }) {
            excluded("SceneObj_Chest_Default_Lv1", meta(tag), Exclusion.SPECIAL_SOURCE);
        }
    }

    @Test
    void excludesGadgetExcelChestEntriesThatAreNotOrdinaryTreasureBoxes() {
        for (String prefab :
                new String[] {
                    "SearchPoint",
                    "SearchPoint_OnWater",
                    "SceneObj_Chest_Relic",
                    "SceneObj_Chest_Relic02",
                    "SceneObj_EssenceChest_Default_Lv1",
                    "SceneObj_Ticket_Chest_Locked_Lv2",
                    "SceneObj_MichiaeMatsuri_Chest_01",
                    "SceneObj_Area_Xm_Property_Prop_SumeruCrudeChest_01",
                    "SceneObj_Area_Nk_Property_Prop_NodKraiCrudeChest_01",
                    "SceneObj_Chest_Hero",
                    "SceneObj_Desert_DeshretRune",
                    "SceneObj_Drop_Chest_Lv1",
                    "SceneObj_Chest_NormalChest",
                    "SceneObj_Chest_Default_Lv3_V70",
                    "SceneObj_Chest_Locked_Lv1_UnrecognizedVariant"
                }) {
            excluded(prefab, meta("解谜低级蒙德"), Exclusion.UNRECOGNIZED_PREFAB);
        }
    }

    @Test
    void rejectsMissingAndContradictorySourceEvidence() {
        excluded("SceneObj_Chest_Default_Lv1", null, Exclusion.UNVERIFIED_SOURCE);
        excluded("SceneObj_Chest_Default_Lv1", meta(""), Exclusion.UNVERIFIED_SOURCE);
        excluded("SceneObj_Chest_Default_Lv1", meta("   "), Exclusion.UNVERIFIED_SOURCE);
        excluded("SceneObj_Chest_Default_Lv1", meta("解谜中级蒙德"), Exclusion.QUALITY_CONFLICT);
        excluded("SceneObj_Chest_Default_Lv4", meta("解谜低级须弥"), Exclusion.QUALITY_CONFLICT);
        excluded("SceneObj_Chest_Default_Lv2", meta("战斗超级璃月"), Exclusion.QUALITY_CONFLICT);
    }

    @Test
    void requiresAWorldChestWithAuthoredGroup() {
        var script = meta("解谜低级蒙德");
        assertEquals(Exclusion.NOT_CHEST,
                StandardChestClassifier.classify(
                        EntityType.Gadget, "SceneObj_Chest_Default_Lv1",
                        script, SceneType.SCENE_WORLD, 1).exclusion());
        assertEquals(Exclusion.NOT_OPEN_WORLD,
                StandardChestClassifier.classify(
                        EntityType.Chest, "SceneObj_Chest_Default_Lv1",
                        script, SceneType.SCENE_DUNGEON, 1).exclusion());
        assertEquals(Exclusion.NOT_OPEN_WORLD,
                StandardChestClassifier.classify(
                        EntityType.Chest, "SceneObj_Chest_Default_Lv1",
                        script, SceneType.SCENE_ACTIVITY, 1).exclusion());
        assertEquals(Exclusion.NO_SCENE_GROUP,
                StandardChestClassifier.classify(
                        EntityType.Chest, "SceneObj_Chest_Default_Lv1",
                        script, SceneType.SCENE_WORLD, 0).exclusion());
    }

    @Test
    void exportsExactSelectorValuesOnlyForAcceptedKinds() {
        assertEquals("common", Kind.COMMON.selector());
        assertEquals("exquisite", Kind.EXQUISITE.selector());
        assertEquals("precious", Kind.PRECIOUS.selector());
        assertEquals("luxurious", Kind.LUXURIOUS.selector());
    }
}
