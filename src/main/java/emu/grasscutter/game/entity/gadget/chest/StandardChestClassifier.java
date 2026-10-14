package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.scripts.data.SceneGadget;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies the four ordinary exploration chest qualities without assigning rewards.
 *
 * <p>GadgetExcel `type = Chest` also includes SearchPoint, shrines, event props, quest
 * objects, Mora chests, furnishing chests and boss flowers. Unknown evidence is rejected;
 * the existing chest interaction and native drop paths remain responsible for those objects.
 */
public final class StandardChestClassifier {
    private StandardChestClassifier() {}

    public enum Kind {
        COMMON("common"),
        EXQUISITE("exquisite"),
        PRECIOUS("precious"),
        LUXURIOUS("luxurious");

        private final String selector;

        Kind(String selector) {
            this.selector = selector;
        }

        public String selector() {
            return selector;
        }
    }

    public enum Exclusion {
        NONE,
        NOT_CHEST,
        NOT_OPEN_WORLD,
        NO_SCENE_GROUP,
        BOSS_FLOWER,
        EXPLICIT_DROP,
        UNRECOGNIZED_PREFAB,
        UNVERIFIED_SOURCE,
        SPECIAL_SOURCE,
        QUALITY_CONFLICT
    }

    /** `kind` is non-null only for an eligible ordinary chest. */
    public record Classification(Kind kind, Exclusion exclusion) {
        public boolean isStandard() {
            return kind != null && exclusion == Exclusion.NONE;
        }
    }

    // Every accepted level is backed by the regular four-grade puzzle/combat
    // drop_tag population. Lv3 and arbitrary suffixes are not given a guessed grade.
    private static final Pattern STANDARD_PREFAB =
            Pattern.compile(
                    "^SceneObj_Chest_(?:Default|Locked|Bramble|Frozen)_Lv([1245])"
                            + "(?:_(?:UnderWater|AboveWater|Amphibious|V70|RemusLanternFish|FollowMove))?$");

    // Other GadgetExcel "Chest" rows include event Chest, EssenceChest, TicketChest,
    // SearchPoint, Relic, Hero, CrudeChest, Rune, and quest-specific art. Never
    // interpret their incidental Lv digits or drop_tag grade as ordinary loot.
    private static final Pattern ORDINARY_SOURCE =
            Pattern.compile(
                    "^(?:雪山)?(?:解谜|战斗)(低级|中级|高级|超级)"
                            + "(?:蒙德|璃月|稻妻|须弥|枫丹|纳塔|至冬|挪德卡莱|诺德克莱|群岛)$");

    private static final Classification NOT_CHEST =
            excluded(Exclusion.NOT_CHEST);

    private static Classification excluded(Exclusion reason) {
        return new Classification(null, reason);
    }

    /**
     * Read-only classification. Do not call this in place of ChestDrop's normal
     * interaction or use the result alone to mark a gadget claimed.
     */
    public static Classification classify(EntityGadget gadget) {
        if (gadget == null) return NOT_CHEST;
        GadgetData data = gadget.getGadgetData();
        return classify(
                data == null ? null : data.getType(),
                data == null ? null : data.getJsonName(),
                gadget.getMetaGadget(),
                gadget.getScene() == null ? null : gadget.getScene().getSceneType(),
                gadget.getGroupId());
    }

    /** Separated inputs keep regression tests independent of a running world. */
    static Classification classify(
            EntityType type,
            String jsonName,
            SceneGadget meta,
            SceneType sceneType,
            int groupId) {
        if (type != EntityType.Chest) return NOT_CHEST;
        if (sceneType != SceneType.SCENE_WORLD) return excluded(Exclusion.NOT_OPEN_WORLD);
        if (groupId <= 0) return excluded(Exclusion.NO_SCENE_GROUP);
        if (meta != null && meta.boss_chest != null) return excluded(Exclusion.BOSS_FLOWER);

        // Native and quest roots (including the empty 1000100 sentinel) are
        // authoritative for that object; never replace them with standard fixed loot.
        if (meta != null && (meta.chest_drop_id > 0 || meta.drop_id > 0)) {
            return excluded(Exclusion.EXPLICIT_DROP);
        }

        Kind prefabKind = kindFromPrefab(jsonName);
        if (prefabKind == null) return excluded(Exclusion.UNRECOGNIZED_PREFAB);

        // A standard-looking prefab can still represent a Shrine, an event chest,
        // or a quest-created object. Require an ordinary scene source, not just
        // an appearance or the `explore.name = "chest"` bookkeeping marker.
        String tag = meta == null ? null : meta.drop_tag;
        if (tag == null || tag.isBlank()) return excluded(Exclusion.UNVERIFIED_SOURCE);
        Matcher source = ORDINARY_SOURCE.matcher(tag);
        if (!source.matches()) return excluded(Exclusion.SPECIAL_SOURCE);

        Kind sourceKind =
                switch (source.group(1)) {
                    case "低级" -> Kind.COMMON;
                    case "中级" -> Kind.EXQUISITE;
                    case "高级" -> Kind.PRECIOUS;
                    case "超级" -> Kind.LUXURIOUS;
                    default -> null;
                };
        if (sourceKind != prefabKind) return excluded(Exclusion.QUALITY_CONFLICT);
        return new Classification(prefabKind, Exclusion.NONE);
    }

    private static Kind kindFromPrefab(String name) {
        if (name == null) return null;
        if ("SceneObj_Chest_Rock_Lv1".equals(name)) return Kind.COMMON;
        Matcher prefab = STANDARD_PREFAB.matcher(name);
        if (!prefab.matches()) return null;
        return switch (prefab.group(1)) {
            case "1" -> Kind.COMMON;
            case "2" -> Kind.EXQUISITE;
            case "4" -> Kind.PRECIOUS;
            case "5" -> Kind.LUXURIOUS;
            default -> null;
        };
    }
}
