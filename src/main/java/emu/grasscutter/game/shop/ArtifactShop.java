package emu.grasscutter.game.shop;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer.GameOptions.ArtifactShopOptions;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.ReliquaryMainPropData;
import emu.grasscutter.game.dungeons.DungeonDropEntry;
import emu.grasscutter.game.dungeons.DungeonDropLoader;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.objects.WeightedList;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import lombok.Getter;

/**
 * Lists official artifact pieces as shop goods. Buying one hands out a freshly rolled artifact
 * rather than a fixed one: the main stat comes from the slot's real pool and substats use the
 * server-wide artifact roll rules.
 */
public class ArtifactShop {
    /** Well clear of the ~101,070,304 the excel goods ids reach. */
    private static final int GOODS_ID_BASE = 200_000_000;

    /** Virtual item id used by the game for Original Resin. */
    private static final int ORIGINAL_RESIN_ID = 106;

    /** One ordinary artifact-domain claim. */
    private static final int DEFAULT_RESIN_COST = 20;

    /**
     * CityData.cityId -> verified general-goods shop type in the current Shop.json.
     *
     * <p>1 Mondstadt, 2 Liyue, 3 Inazuma, 4 Sumeru, 5 Fontaine. Newer regions stay unrouted until
     * their real city shop is identified from current resources.
     */
    private static final Map<Integer, Integer> REGIONAL_SHOPS =
            Map.of(1, 1004, 2, 1008, 3, 1056, 4, 1074, 5, 1093);

    /** Shop type -> CityData.cityId, for validating shop requests. */
    private static final Map<Integer, Integer> SHOP_CITIES =
            Map.of(1004, 1, 1008, 2, 1056, 3, 1074, 4, 1093, 5);

    /** Flower, plume, sands, goblet, circlet - the order the bag shows them in. */
    private static final List<EquipType> SLOT_ORDER =
            List.of(
                    EquipType.EQUIP_BRACER,
                    EquipType.EQUIP_NECKLACE,
                    EquipType.EQUIP_SHOES,
                    EquipType.EQUIP_RING,
                    EquipType.EQUIP_DRESS);

    /**
     * The main stat each slot can actually roll, with the game's own odds.
     *
     * <p>The pool has to be spelled out because the shipped ReliquaryMainPropExcelConfigData is
     * flattened - every entry in every depot carries the same weight, and the depots hold stats the
     * slot never rolls - so drawing from one straight gives you a Sands of Eon with flat HP on it.
     */
    private static final Map<EquipType, Map<FightProperty, Double>> MAIN_STATS =
            Map.of(
                    EquipType.EQUIP_BRACER, Map.of(FightProperty.FIGHT_PROP_HP, 100d),
                    EquipType.EQUIP_NECKLACE, Map.of(FightProperty.FIGHT_PROP_ATTACK, 100d),
                    EquipType.EQUIP_SHOES,
                            Map.of(
                                    FightProperty.FIGHT_PROP_HP_PERCENT, 26.68d,
                                    FightProperty.FIGHT_PROP_ATTACK_PERCENT, 26.66d,
                                    FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 26.66d,
                                    FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 10d,
                                    FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 10d),
                    EquipType.EQUIP_RING,
                            Map.ofEntries(
                                    Map.entry(FightProperty.FIGHT_PROP_HP_PERCENT, 19.25d),
                                    Map.entry(FightProperty.FIGHT_PROP_ATTACK_PERCENT, 19.25d),
                                    Map.entry(FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 19d),
                                    Map.entry(FightProperty.FIGHT_PROP_FIRE_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ELEC_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_WATER_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ICE_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_WIND_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ROCK_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_GRASS_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_PHYSICAL_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 2.5d)),
                    EquipType.EQUIP_DRESS,
                            Map.of(
                                    FightProperty.FIGHT_PROP_HP_PERCENT, 22d,
                                    FightProperty.FIGHT_PROP_ATTACK_PERCENT, 22d,
                                    FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 22d,
                                    FightProperty.FIGHT_PROP_CRITICAL, 10d,
                                    FightProperty.FIGHT_PROP_CRITICAL_HURT, 10d,
                                    FightProperty.FIGHT_PROP_HEAL_ADD, 10d,
                                    FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 4d));

    /** The piece behind each of our goods ids. Empty while the shop is switched off. */
    @Getter private final Int2ObjectMap<ItemData> goods = new Int2ObjectOpenHashMap<>();

    /** City owning each generated goods id. 0 means the id is not one of ours. */
    private final Int2IntMap goodsCity = new Int2IntOpenHashMap();

    /**
     * Every difficulty at one world-map domain entrance points to the union of artifact sets that
     * entrance can award. This is what lets clearing an early difficulty prove the domain once,
     * while Adventure Rank can unlock higher-rarity versions later.
     */
    private final Int2ObjectMap<Set<Integer>> domainSetsByDungeon = new Int2ObjectOpenHashMap<>();

    /**
     * Installs domain artifacts into the general-goods shop belonging to the domain's city.
     *
     * <p>The domain-to-city relationship comes from DungeonExcelConfigData.cityId and the set list
     * comes from DungeonDrop.json. A set that legitimately drops from artifact domains in more than
     * one city is listed in each of those cities with a separate goods id.
     */
    public void install(Int2ObjectMap<List<ShopInfo>> shopData) {
        var options = GAME_OPTIONS.artifactShop;
        this.goods.clear();
        this.goodsCity.clear();
        this.domainSetsByDungeon.clear();
        shopData.values().forEach(list -> list.removeIf(sold -> sold.getGoodsId() >= GOODS_ID_BASE));
        if (!options.enabled) return;

        try {
            DungeonDropLoader.ensureLoaded();
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load dungeon drops for artifact shop.", e);
            return;
        }

        for (var route : REGIONAL_SHOPS.entrySet()) {
            if (!shopData.containsKey(route.getValue())) {
                Grasscutter.getLogger()
                        .error(
                                "Artifact shop requires city {} shop {}, but it is missing from current shop data.",
                                route.getKey(),
                                route.getValue());
                return;
            }
        }

        this.domainSetsByDungeon.putAll(buildDomainSetIndex());
        var citiesBySet = citiesBySet();
        var pieces = catalog(Set.of(), Set.of(3, 4, 5));
        if (pieces.isEmpty() || citiesBySet.isEmpty() || this.domainSetsByDungeon.isEmpty()) {
            Grasscutter.getLogger()
                    .error(
                            "Artifact shop current-version data is incomplete: pieces={}, regional sets={}, domain groups={}.",
                            pieces.size(),
                            citiesBySet.size(),
                            this.domainSetsByDungeon.size());
            return;
        }

        int goodsId = GOODS_ID_BASE;
        int listed = 0;
        var touchedShops = new HashSet<Integer>();
        for (ItemData piece : pieces) {
            var cities = citiesBySet.get(piece.getSetId());
            if (cities == null || cities.isEmpty()) continue;

            for (int cityId : cities) {
                Integer shopId = REGIONAL_SHOPS.get(cityId);
                if (shopId == null) continue;

                var items = shopData.get(shopId);
                items.add(makeGoods(goodsId, piece, options));
                this.goods.put(goodsId, piece);
                this.goodsCity.put(goodsId, cityId);
                touchedShops.add(shopId);
                goodsId++;
                listed++;
            }
        }

        Grasscutter.getLogger()
                .info(
                        "Listed {} regional artifact goods across {} city shop(s), with {} dungeon "
                                + "difficulty id(s) indexed for domain unlocks.",
                        listed,
                        touchedShops.size(),
                        this.domainSetsByDungeon.size());
    }

    /** The piece this goods id sells, or null when the id is not one of ours. */
    public ItemData getPiece(int goodsId) {
        return this.goods.get(goodsId);
    }

    /** Goods IDs this player is currently allowed to see in this exact city shop. */
    public Set<Integer> getAvailableGoodsIds(Player player, int shopType) {
        int cityId = cityIdForShop(shopType);
        if (cityId <= 0) return Set.of();

        var unlockedSets = unlockedSetIds(player, cityId);
        var allowedRanks = allowedRanks(player);
        var available = new HashSet<Integer>();

        for (var entry : this.goods.int2ObjectEntrySet()) {
            int goodsId = entry.getIntKey();
            if (this.goodsCity.get(goodsId) != cityId) continue;
            if (isAvailable(entry.getValue(), unlockedSets, allowedRanks)) {
                available.add(goodsId);
            }
        }
        return available;
    }

    /** Server-side purchase check; the client-side shop filter is never treated as authorization. */
    public boolean isAvailable(Player player, int shopType, int goodsId) {
        var piece = this.goods.get(goodsId);
        int cityId = cityIdForShop(shopType);
        if (piece == null || cityId <= 0 || this.goodsCity.get(goodsId) != cityId) return false;
        return isAvailable(piece, unlockedSetIds(player, cityId), allowedRanks(player));
    }

    private static boolean isAvailable(
            ItemData piece, Set<Integer> unlockedSets, Set<Integer> allowedRanks) {
        return piece != null
                && unlockedSets.contains(piece.getSetId())
                && allowedRanks.contains(piece.getRankLevel());
    }

    /**
     * Builds the regional catalog from the real artifact-domain data. Only Domains of Blessing are
     * considered, so boss/story dungeons that happen to award artifacts cannot populate a shop.
     */
    private static Map<Integer, Set<Integer>> citiesBySet() {
        var citiesBySet = new HashMap<Integer, Set<Integer>>();
        for (var dungeonEntry : GameData.getDungeonDropDataMap().int2ObjectEntrySet()) {
            var dungeon = GameData.getDungeonDataMap().get(dungeonEntry.getIntKey());
            if (dungeon == null || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY) {
                continue;
            }

            int cityId = dungeon.getCityId();
            if (!REGIONAL_SHOPS.containsKey(cityId)) continue;

            for (int setId : artifactSetIds(dungeonEntry.getValue())) {
                citiesBySet.computeIfAbsent(setId, k -> new TreeSet<>()).add(cityId);
            }
        }
        return citiesBySet;
    }

    /**
     * Builds dungeonId -> whole-domain set ids from current PointData.dungeonIds only.
     *
     * <p>Every artifact difficulty at the entrance must also have a DungeonDrop row. Missing point
     * or drop relationships are treated as invalid current-version data and are never substituted.
     */
    private static Int2ObjectMap<Set<Integer>> buildDomainSetIndex() {
        var exactSets = new Int2ObjectOpenHashMap<Set<Integer>>();
        for (var dungeonEntry : GameData.getDungeonDropDataMap().int2ObjectEntrySet()) {
            var dungeon = GameData.getDungeonDataMap().get(dungeonEntry.getIntKey());
            if (dungeon == null || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY) {
                continue;
            }

            var sets = artifactSetIds(dungeonEntry.getValue());
            if (!sets.isEmpty()) {
                exactSets.put(dungeonEntry.getIntKey(), sets);
            }
        }

        var index = new Int2ObjectOpenHashMap<Set<Integer>>();
        for (var pointEntry : GameData.getScenePointEntryMap().values()) {
            var point = pointEntry.getPointData();
            if (point == null || point.getDungeonIds() == null || point.getDungeonIds().length == 0) {
                continue;
            }

            var domainDungeonIds = new IntArrayList();
            var domainSets = new HashSet<Integer>();
            boolean complete = true;
            for (int dungeonId : point.getDungeonIds()) {
                var dungeon = GameData.getDungeonDataMap().get(dungeonId);
                if (dungeon == null
                        || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY) {
                    continue;
                }

                domainDungeonIds.add(dungeonId);
                var sets = exactSets.get(dungeonId);
                if (sets == null || sets.isEmpty()) {
                    Grasscutter.getLogger()
                            .error(
                                    "Artifact domain dungeon {} has no current DungeonDrop artifact sets.",
                                    dungeonId);
                    complete = false;
                    break;
                }
                domainSets.addAll(sets);
            }

            if (!complete || domainDungeonIds.isEmpty() || domainSets.isEmpty()) continue;
            Set<Integer> sharedSets = Set.copyOf(domainSets);
            for (int i = 0; i < domainDungeonIds.size(); i++) {
                index.put(domainDungeonIds.getInt(i), sharedSets);
            }
        }

        return index;
    }

    private static Set<Integer> artifactSetIds(List<DungeonDropEntry> drops) {
        if (drops == null) return Set.of();

        var sets = new HashSet<Integer>();
        for (var drop : drops) {
            if (drop == null || drop.getItems() == null) continue;
            for (int itemId : drop.getItems()) {
                var data = GameData.getItemDataMap().get(itemId);
                if (data != null
                        && data.getItemType() == ItemType.ITEM_RELIQUARY
                        && data.getSetId() > 0) {
                    sets.add(data.getSetId());
                }
            }
        }
        return sets;
    }

    /**
     * Completing any difficulty at an artifact-domain entrance unlocks every set belonging to that
     * entrance, but only in its own city's shop. Adventure Rank separately gates 3/4/5-star goods.
     */
    private Set<Integer> unlockedSetIds(Player player, int cityId) {
        if (cityId <= 0
                || player == null
                || player.getPlayerProgress() == null
                || player.getPlayerProgress().getCompletedDungeons() == null) {
            return Set.of();
        }

        var unlocked = new HashSet<Integer>();
        for (int dungeonId : player.getPlayerProgress().getCompletedDungeons()) {
            var dungeon = GameData.getDungeonDataMap().get(dungeonId);
            if (dungeon == null
                    || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY
                    || dungeon.getCityId() != cityId) {
                continue;
            }

            var domainSets = this.domainSetsByDungeon.get(dungeonId);
            if (domainSets != null) unlocked.addAll(domainSets);
        }
        return unlocked;
    }

    private static int cityIdForShop(int shopType) {
        return SHOP_CITIES.getOrDefault(shopType, 0);
    }

    /**
     * First-pass progression curve. AR 1-29 can buy 3-star pieces, AR 30-39 adds 4-star pieces,
     * and AR 40+ adds 5-star pieces. Keeping the lower rarities available avoids deleting the
     * earlier shop progression when the player ranks up.
     */
    private static Set<Integer> allowedRanks(Player player) {
        int level = player != null ? player.getLevel() : 0;
        if (level >= 40) return Set.of(3, 4, 5);
        if (level >= 30) return Set.of(3, 4);
        return Set.of(3);
    }

    /** Creates the artifact delivered by this shop good. V1 artifact goods are sold at +0. */
    public GameItem roll(ItemData piece) {
        var item = new GameItem(piece);

        // The main stat has to be settled first: a substat never repeats it.
        int mainPropId = rollMainProp(piece);
        if (mainPropId > 0) {
            item.setMainPropId(mainPropId);
        }

        // Item level 1 is the protocol/storage representation of an unenhanced (+0) artifact.
        item.setLevel(1);
        item.setTotalExp(0);
        item.getAppendPropIdList().clear();
        item.addAppendProps(piece.getAppendPropNum());
        return item;
    }

    /**
     * Returns one normal domain-style item definition for every requested set/rank/slot.
     *
     * <p>An empty set filter means every released set. An empty rank filter means ranks 3 through
     * 5. The normal affix depot follows the game's 301/401/501 convention and the best starting
     * substat variant has rank - 1 initial substats (2/3/4 for 3/4/5-star pieces).
     */
    public static List<ItemData> catalog(Set<Integer> setIds, Set<Integer> ranks) {
        boolean allSets = setIds == null || setIds.isEmpty();
        boolean allRanks = ranks == null || ranks.isEmpty();
        var pieces = new ArrayList<ItemData>();

        for (ItemData data : GameData.getItemDataMap().values()) {
            if (data.getItemType() != ItemType.ITEM_RELIQUARY) continue;
            int rank = data.getRankLevel();
            if (rank < 3 || rank > 5) continue;
            if (!allRanks && !ranks.contains(rank)) continue;
            if (!allSets && !setIds.contains(data.getSetId())) continue;
            if (!isCanonicalDomainPiece(data)) continue;

            // Beta and test sets carry no set bonus; every released set does.
            var set = GameData.getReliquarySetDataMap().get(data.getSetId());
            if (set == null || set.getEquipAffixId() <= 0) continue;
            pieces.add(data);
        }

        pieces.sort(
                Comparator.comparingInt(ItemData::getSetId)
                        .thenComparingInt(ItemData::getRankLevel)
                        .thenComparingInt(data -> SLOT_ORDER.indexOf(data.getEquipType())));
        return pieces;
    }

    /** Selects the regular domain-roll variant among the duplicate reliquary item definitions. */
    private static boolean isCanonicalDomainPiece(ItemData data) {
        int rank = data.getRankLevel();
        if (data.getAppendPropNum() != rank - 1) return false;
        if (data.getAppendPropDepotId() != rank * 100 + 1) return false;
        return data.getMainPropDepotId() == mainPropDepot(data.getEquipType());
    }

    private static ShopInfo makeGoods(int goodsId, ItemData piece, ArtifactShopOptions options) {
        var goods = new ShopInfo();
        goods.setGoodsId(goodsId);
        goods.setGoodsItem(new ItemParamData(piece.getId(), 1));
        goods.setScoin(0);
        goods.setHcoin(0);
        goods.setBuyLimit(options.buyLimit);
        goods.setMinLevel(1);
        goods.setMaxLevel(99);

        // V1 has one explicit price: the same 20 Original Resin as one ordinary domain claim.
        var costs = new ArrayList<ItemParamData>(1);
        costs.add(new ItemParamData(ORIGINAL_RESIN_ID, DEFAULT_RESIN_COST));
        goods.setCostItemList(costs);
        return goods;
    }

    private static int rollMainProp(ItemData piece) {
        var pool = MAIN_STATS.get(piece.getEquipType());
        var candidates = GameDepot.getRelicMainPropList(piece.getMainPropDepotId());
        if (pool == null || candidates == null) return 0;

        var randomList = new WeightedList<ReliquaryMainPropData>();
        for (ReliquaryMainPropData prop : candidates) {
            double weight = pool.getOrDefault(prop.getFightProp(), 0d);
            if (weight > 0) {
                randomList.add(weight, prop);
            }
        }
        return randomList.size() == 0 ? 0 : randomList.next().getId();
    }

    private static int mainPropDepot(EquipType slot) {
        return switch (slot) {
            case EQUIP_SHOES -> 1000;
            case EQUIP_NECKLACE -> 2000;
            case EQUIP_DRESS -> 3000;
            case EQUIP_BRACER -> 4000;
            case EQUIP_RING -> 5000;
            default -> 0;
        };
    }
}
