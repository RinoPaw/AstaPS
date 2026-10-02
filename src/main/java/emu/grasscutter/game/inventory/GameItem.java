package emu.grasscutter.game.inventory;

import dev.morphia.annotations.*;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.EquipOuterClass.Equip;
import emu.grasscutter.net.proto.FurnitureOuterClass.Furniture;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.ItemOuterClass.Item;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.MaterialOuterClass.Material;
import emu.grasscutter.net.proto.ReliquaryOuterClass.Reliquary;
import emu.grasscutter.net.proto.SceneReliquaryInfoOuterClass.SceneReliquaryInfo;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.WeaponOuterClass.Weapon;
import emu.grasscutter.utils.objects.WeightedList;

import java.util.*;
import lombok.*;
import org.bson.types.ObjectId;

@Entity(value = "items", useDiscriminator = false)
public class GameItem {
    @Id private ObjectId id;
    @Indexed private int ownerId;
    @Getter @Setter private int itemId;
    @Getter @Setter private int count;

    @Transient @Getter private long guid; // Player unique id
    @Transient @Getter @Setter private ItemData itemData;

    // Equips
    @Getter @Setter private int level;
    @Getter @Setter private int exp;
    @Getter @Setter private int totalExp;
    @Getter @Setter private int promoteLevel;
    @Getter @Setter private boolean favourite;
    @Getter @Setter private boolean locked;

    // Weapon
    @Getter private List<Integer> affixes;
    @Getter @Setter private int refinement = 0;

    // Relic
    @Getter @Setter private int mainPropId;
    @Getter private List<Integer> appendPropIdList;
    /** Purple define-mark / star on relic card (Reliquary._is_relic_starred). */
    @Getter @Setter private boolean relicStarred;
    /**
     * Affix ids chosen when defined at the Artifact Transmuter (Reliquary._purchased_append_prop_id_list).
     * Client uses this for the define icon / tooltip.
     */
    @Getter @Setter private List<Integer> purchasedAppendPropIdList;
    /**
     * Affix ids that receive shared upgrade guarantees (Reliquary._definite_append_prop_id_list).
     * Official: at least 2 upgrade hits on these lines by +20, split across them.
     */
    @Getter @Setter private List<Integer> definiteAppendPropIdList;

    @Getter @Setter private int equipCharacter;
    @Transient @Getter @Setter private EntityWeapon weaponEntity;
    @Transient @Getter private boolean newItem = false;

    public GameItem() {
        // Morphia only
    }

    public GameItem(int itemId) {
        this(GameData.getItemDataMap().get(itemId));
    }

    public GameItem(int itemId, int count) {
        this(GameData.getItemDataMap().get(itemId), count);
    }

    public GameItem(ItemParamData itemParamData) {
        this(itemParamData.getId(), itemParamData.getCount());
    }

    public GameItem(ItemData data) {
        this(data, 1);
    }

    public GameItem(ItemData data, int count) {
        this.itemId = data.getId();
        this.itemData = data;

        switch (data.getItemType()) {
            case ITEM_VIRTUAL:
                this.count = count;
                break;
            case ITEM_WEAPON:
                this.count = 1;
                this.level = Math.max(this.count, 1); // ??????????????????
                this.affixes = new ArrayList<>(2);
                if (data.getSkillAffix() != null) {
                    for (int skillAffix : data.getSkillAffix()) {
                        if (skillAffix > 0) {
                            this.affixes.add(skillAffix);
                        }
                    }
                }
                break;
            case ITEM_RELIQUARY:
                this.count = 1;
                this.level = 1;
                this.appendPropIdList = new ArrayList<>();
                // Create main property
                ReliquaryMainPropData mainPropData =
                        GameDepot.getRandomRelicMainProp(data.getMainPropDepotId());
                if (mainPropData != null) {
                    this.mainPropId = mainPropData.getId();
                }
                // Create extra stats
                this.addAppendProps(data.getAppendPropNum());
                break;
            default:
                this.count = Math.min(count, data.getStackLimit());
        }
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwner(Player player) {
        this.ownerId = player.getUid();
        this.guid = player.getNextGameGuid();
    }

    public void checkIsNew(Inventory inventory) {
        // display notification when player obtain new item
        if (inventory.getItemById(this.itemId) == null) {
            this.newItem = true;
        }
    }

    public ObjectId getObjectId() {
        return id;
    }

    public ItemType getItemType() {
        return this.itemData.getItemType();
    }

    public static int getMinPromoteLevel(int level) {
        if (level > 80) {
            return 6;
        } else if (level > 70) {
            return 5;
        } else if (level > 60) {
            return 4;
        } else if (level > 50) {
            return 3;
        } else if (level > 40) {
            return 2;
        } else if (level > 20) {
            return 1;
        }
        return 0;
    }

    public int getEquipSlot() {
        return this.getItemData().getEquipType().getValue();
    }

    public boolean isEquipped() {
        return this.getEquipCharacter() > 0;
    }

    public boolean isDestroyable() {
        return !this.isLocked() && !this.isEquipped();
    }

    public void addAppendProp() {
        if (this.appendPropIdList == null) {
            this.appendPropIdList = new ArrayList<>();
        }

        if (this.appendPropIdList.size() < 4) {
            this.addNewAppendProp();
        } else {
            this.upgradeRandomAppendProp();
        }
    }

    /** Kept for source compatibility; artifact roll weighting is global. */
    public void addAppendProp(ArtifactRollBias ignored) {
        this.addAppendProp();
    }

    public void addAppendProps(int quantity) {
        int num = Math.max(quantity, 0);
        for (int i = 0; i < num; i++) {
            this.addAppendProp();
        }
    }

    /** Kept for source compatibility; artifact roll weighting is global. */
    public void addAppendProps(int quantity, ArtifactRollBias ignored) {
        this.addAppendProps(quantity);
    }

    private Set<FightProperty> getAppendFightProperties() {
        Set<FightProperty> props = new HashSet<>();
        // Previously this would check no more than the first four affixes, however custom artifacts may
        // not respect this order.
        for (int appendPropId : this.appendPropIdList) {
            ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
            if (affixData != null) {
                props.add(affixData.getFightProp());
            }
        }
        return props;
    }

    private void addNewAppendProp() {
        List<ReliquaryAffixData> affixList =
                GameDepot.getRelicAffixList(this.itemData.getAppendPropDepotId());

        if (affixList == null) {
            return;
        }

        // Build blacklist - Dont add same stat as main/sub stat
        Set<FightProperty> blacklist = this.getAppendFightProperties();
        ReliquaryMainPropData mainPropData =
                GameData.getReliquaryMainPropDataMap().get(this.mainPropId);
        if (mainPropData != null) {
            blacklist.add(mainPropData.getFightProp());
        }

        // First choose the stat type. Type bias applies only when a new line is created.
        var typeBaseWeights = new HashMap<FightProperty, Double>();
        for (ReliquaryAffixData affix : affixList) {
            if (affix.getWeight() > 0 && !blacklist.contains(affix.getFightProp())) {
                typeBaseWeights.merge(affix.getFightProp(), (double) affix.getWeight(), Double::sum);
            }
        }

        var typeList = new WeightedList<FightProperty>();
        for (var entry : typeBaseWeights.entrySet()) {
            typeList.add(entry.getValue() * ArtifactRollBias.typeWeight(entry.getKey()), entry.getKey());
        }
        if (typeList.size() == 0) {
            return;
        }
        FightProperty selectedType = typeList.next();

        // Then choose the numeric tier inside that type. Tier bias is independent of type bias.
        var tierList = new WeightedList<ReliquaryAffixData>();
        for (ReliquaryAffixData affix : affixList) {
            if (affix.getFightProp() == selectedType && affix.getWeight() > 0) {
                tierList.add(affix.getWeight() * ArtifactRollBias.GLOBAL.weigh(affix), affix);
            }
        }
        if (tierList.size() == 0) {
            return;
        }

        this.appendPropIdList.add(tierList.next().getId());
    }

    private void upgradeRandomAppendProp() {
        List<ReliquaryAffixData> affixList =
                GameDepot.getRelicAffixList(this.itemData.getAppendPropDepotId());

        if (affixList == null) {
            return;
        }

        // Build whitelist of fight props already on the relic
        Set<FightProperty> whitelist = this.getAppendFightProperties();

        // Defined artifacts: the chosen substats must gain at least 2 upgrades in total before max level,
        // split evenly when several are chosen.
        Set<FightProperty> guarantee = this.getDefiniteFightPropsNeedingGuarantee();
        if (!guarantee.isEmpty()) {
            Set<FightProperty> forced = new HashSet<>(whitelist);
            forced.retainAll(guarantee);
            if (!forced.isEmpty()) {
                whitelist = forced;
            }
        }

        // Choose which existing line receives the upgrade using the same global type bias as new lines.
        var typeBaseWeights = new HashMap<FightProperty, Double>();
        for (ReliquaryAffixData affix : affixList) {
            if (affix.getUpgradeWeight() > 0 && whitelist.contains(affix.getFightProp())) {
                typeBaseWeights.merge(
                        affix.getFightProp(), (double) affix.getUpgradeWeight(), Double::sum);
            }
        }

        var typeList = new WeightedList<FightProperty>();
        for (var entry : typeBaseWeights.entrySet()) {
            typeList.add(
                    entry.getValue() * ArtifactRollBias.typeWeight(entry.getKey()), entry.getKey());
        }
        if (typeList.size() == 0) {
            return;
        }
        FightProperty selectedType = typeList.next();

        // Once the target line is fixed, bias the value tier independently.
        var tierList = new WeightedList<ReliquaryAffixData>();
        for (ReliquaryAffixData affix : affixList) {
            if (affix.getFightProp() == selectedType && affix.getUpgradeWeight() > 0) {
                tierList.add(
                        affix.getUpgradeWeight() * ArtifactRollBias.GLOBAL.weigh(affix), affix);
            }
        }
        if (tierList.size() == 0) {
            return;
        }

        this.appendPropIdList.add(tierList.next().getId());
    }

    /** Fight props from definite append ids that still need guaranteed upgrade hits. */
    private Set<FightProperty> getDefiniteFightPropsNeedingGuarantee() {
        Set<FightProperty> defs = this.resolveDefiniteFightProps();
        if (defs.isEmpty()) {
            return Set.of();
        }
        int hits = 0;
        for (int appendPropId : this.appendPropIdList) {
            ReliquaryAffixData affixData = GameData.getReliquaryAffixDataMap().get(appendPropId);
            if (affixData != null && defs.contains(affixData.getFightProp())) {
                hits++;
            }
        }
        // Initial define rolls already occupy one slot each; only count extra upgrades.
        int upgradeHits = Math.max(0, hits - defs.size());
        if (upgradeHits >= 2) {
            return Set.of();
        }
        return defs;
    }

    private Set<FightProperty> resolveDefiniteFightProps() {
        Set<FightProperty> out = new HashSet<>();
        if (this.definiteAppendPropIdList == null) {
            return out;
        }
        for (int id : this.definiteAppendPropIdList) {
            ReliquaryAffixData affix = GameData.getReliquaryAffixDataMap().get(id);
            if (affix != null && affix.getFightProp() != null) {
                out.add(affix.getFightProp());
            } else {
                // Allow storing FightProperty ids directly (e.g. 20/22/28).
                FightProperty fp = FightProperty.getPropById(id);
                if (fp != null && fp != FightProperty.FIGHT_PROP_NONE) {
                    out.add(fp);
                }
            }
        }
        return out;
    }

    /**
     * Mark as Artifact Transmuter defined: purchased/definite drive the purple guarantee UI.
     *
     * <p>Do not set {@code relicStarred}. That bit is the bag star toggle
     * ({@code SetReliquaryStarState}); leaving it true on define pushes clients into a broken
     * StarUp enhance panel (Lv.00 / Option A / ? cost). e3a3240 set both; production shows only
     * purchased+definite is safe for enhance.
     */
    public void markAsDefinedReliquary(List<Integer> chosenAffixIds) {
        if (chosenAffixIds == null || chosenAffixIds.isEmpty()) {
            return;
        }
        List<Integer> copy = new ArrayList<>(chosenAffixIds);
        this.purchasedAppendPropIdList = new ArrayList<>(copy);
        this.definiteAppendPropIdList = new ArrayList<>(copy);
        // Keep existing player star as-is; never force-on at define time.
    }

    @PostLoad
    public void onLoad() {
        if (this.itemData == null) {
            this.itemData = GameData.getItemDataMap().get(getItemId());
        }
        if (this.purchasedAppendPropIdList == null) {
            this.purchasedAppendPropIdList = new ArrayList<>();
        }
        if (this.definiteAppendPropIdList == null) {
            this.definiteAppendPropIdList = new ArrayList<>();
        }
    }

    public void save() {
        if (this.count > 0 && this.ownerId > 0) {
            DatabaseHelper.saveItem(this);
        } else if (this.getObjectId() != null) {
            DatabaseHelper.deleteItem(this);
        }
    }

    public SceneWeaponInfo createSceneWeaponInfo() {
        var weaponInfo =
                SceneWeaponInfo.newBuilder()
                        .setEntityId(this.getWeaponEntity() != null ? this.getWeaponEntity().getId() : 0)
                        .setItemId(this.getItemId())
                        .setGuid(this.getGuid())
                        .setLevel(this.getLevel())
                        .setGadgetId(this.getItemData().getGadgetId())
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder().setIsInited(getAffixes().size() > 0));

        if (this.getAffixes() != null && this.getAffixes().size() > 0) {
            for (int affix : this.getAffixes()) {
                weaponInfo.putAffixMap(affix, this.getRefinement());
            }
        }

        return weaponInfo.build();
    }

    public SceneReliquaryInfo createSceneReliquaryInfo() {
        SceneReliquaryInfo relicInfo =
                SceneReliquaryInfo.newBuilder()
                        .setItemId(this.getItemId())
                        .setGuid(this.getGuid())
                        .setLevel(this.getLevel())
                        .build();

        return relicInfo;
    }

    public Weapon toWeaponProto() {
        Weapon.Builder weapon =
                Weapon.newBuilder()
                        .setLevel(this.getLevel())
                        .setExp(this.getExp())
                        .setPromoteLevel(this.getPromoteLevel());

        if (this.getAffixes() != null && this.getAffixes().size() > 0) {
            for (int affix : this.getAffixes()) {
                weapon.putAffixMap(affix, this.getRefinement());
            }
        }

        return weapon.build();
    }

    public Reliquary toReliquaryProto() {
        Reliquary.Builder relic =
                Reliquary.newBuilder()
                        .setLevel(this.getLevel())
                        .setExp(this.getExp())
                        .setPromoteLevel(this.getPromoteLevel())
                        .setMainPropId(this.getMainPropId())
                        .addAllAppendPropIdList(this.getAppendPropIdList());

        // Defined relics: purple guarantee = purchased/definite. Do NOT wire _is_relic_starred
        // for them - that bit opens a broken StarUp enhance UI on current clients.
        boolean defined =
                this.purchasedAppendPropIdList != null && !this.purchasedAppendPropIdList.isEmpty();
        relic.setIsRelicStarred(!defined && this.isRelicStarred());
        if (this.purchasedAppendPropIdList != null && !this.purchasedAppendPropIdList.isEmpty()) {
            relic.addAllPurchasedAppendPropIdList(this.purchasedAppendPropIdList);
        }
        if (this.definiteAppendPropIdList != null && !this.definiteAppendPropIdList.isEmpty()) {
            relic.addAllDefiniteAppendPropIdList(this.definiteAppendPropIdList);
        }
        return relic.build();
    }

    public Item toProto() {
        Item.Builder proto = Item.newBuilder().setGuid(this.getGuid()).setItemId(this.getItemId());

        switch (getItemType()) {
            case ITEM_WEAPON:
                Weapon weapon = this.toWeaponProto();
                proto.setEquip(Equip.newBuilder().setWeapon(weapon).setIsLocked(this.isLocked()).build());
                break;
            case ITEM_RELIQUARY:
                Reliquary relic = this.toReliquaryProto();
                proto.setEquip(Equip.newBuilder().setReliquary(relic).setIsLocked(this.isLocked()).build());
                break;
            case ITEM_FURNITURE:
                Furniture furniture = Furniture.newBuilder().setCount(getCount()).build();
                proto.setFurniture(furniture);
                break;
            default:
                Material material = Material.newBuilder().setCount(getCount()).build();
                proto.setMaterial(material);
                break;
        }

        return proto.build();
    }

    public ItemHint toItemHintProto() {
        var proto = ItemHint.newBuilder()
            .setItemId(getItemId())
            .setGuid(this.getGuid())
            .setCount(getCount())
            .setIsNew(this.isNewItem())
            .build();
        return proto;
    }

    public ItemParam toItemParam() {
        return ItemParam.newBuilder().setItemId(this.getItemId()).setCount(this.getCount()).build();
    }
}