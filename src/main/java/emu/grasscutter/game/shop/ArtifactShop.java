package emu.grasscutter.game.shop;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ShopSettings;
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
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.objects.WeightedList;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;

/** Regional, player-aware artifact goods backed by ordinary shop packets. */
public class ArtifactShop implements DynamicShopProvider {
    private static final int GOODS_ID_BASE = 200_000_000;
    private static final int GOODS_ID_LIMIT = 201_000_000;
    public static final int ORIGINAL_RESIN_ID = 106;
    private static final int FALLBACK_RESIN_COST = 20;

    private static final List<EquipType> SLOT_ORDER =
            List.of(
                    EquipType.EQUIP_BRACER,
                    EquipType.EQUIP_NECKLACE,
                    EquipType.EQUIP_SHOES,
                    EquipType.EQUIP_RING,
                    EquipType.EQUIP_DRESS);

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

    @Getter private final Int2ObjectMap<ItemData> goods = new Int2ObjectOpenHashMap<>();
    private final Int2IntMap goodsCity = new Int2IntOpenHashMap();
    private final Int2ObjectMap<Set<Integer>> domainSetsByDungeon = new Int2ObjectOpenHashMap<>();

    private static ShopSettings.Artifact options() {
        return GAME.shop.artifact;
    }

    @Override
    public void install(Int2ObjectMap<List<ShopInfo>> shopData) {
        var options = options();
        goods.clear();
        goodsCity.clear();
        domainSetsByDungeon.clear();
        shopData.values()
                .forEach(
                        list ->
                                list.removeIf(
                                        sold ->
                                                sold.getGoodsId() >= GOODS_ID_BASE
                                                        && sold.getGoodsId() < GOODS_ID_LIMIT));
        if (!options.enabled) return;

        try {
            DungeonDropLoader.ensureLoaded();
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load dungeon drops for artifact shop.", e);
            return;
        }

        for (var route : options.regionalShops.entrySet()) {
            Integer cityId = route.getKey();
            Integer shopId = route.getValue();
            if (cityId == null || shopId == null || cityId <= 0 || shopId <= 0) continue;
            if (!shopData.containsKey(shopId.intValue())) {
                Grasscutter.getLogger()
                        .error(
                                "Artifact shop requires city {} shop {}, but it is missing from current shop data.",
                                cityId,
                                shopId);
                return;
            }
        }

        domainSetsByDungeon.putAll(buildDomainSetIndex());
        var citiesBySet = citiesBySet();
        var pieces = catalog(Set.of(), Set.of(2, 3, 4, 5));
        if (pieces.isEmpty() || citiesBySet.isEmpty() || domainSetsByDungeon.isEmpty()) {
            Grasscutter.getLogger()
                    .error(
                            "Artifact shop data incomplete: pieces={}, regional sets={}, domain groups={}.",
                            pieces.size(),
                            citiesBySet.size(),
                            domainSetsByDungeon.size());
            return;
        }

        int goodsId = GOODS_ID_BASE;
        int listed = 0;
        var touchedShops = new HashSet<Integer>();
        for (var piece : pieces) {
            var cities = citiesBySet.get(piece.getSetId());
            if (cities == null) continue;
            for (int cityId : cities) {
                Integer shopId = options.regionalShops.get(cityId);
                if (shopId == null) continue;
                if (goodsId >= GOODS_ID_LIMIT) {
                    Grasscutter.getLogger().error("Artifact dynamic goods id range exhausted.");
                    return;
                }
                shopData.get(shopId.intValue()).add(makeGoods(goodsId, piece, options.buyLimit));
                goods.put(goodsId, piece);
                goodsCity.put(goodsId, cityId);
                touchedShops.add(shopId);
                goodsId++;
                listed++;
            }
        }

        Grasscutter.getLogger()
                .info(
                        "Listed {} regional artifact goods across {} city shop(s), with {} dungeon difficulty id(s) indexed.",
                        listed,
                        touchedShops.size(),
                        domainSetsByDungeon.size());
    }

    @Override
    public boolean ownsGoods(int goodsId) {
        return goods.containsKey(goodsId);
    }

    public ItemData getPiece(int goodsId) {
        return goods.get(goodsId);
    }

    public Set<Integer> getAvailableGoodsIds(Player player, int shopType) {
        int cityId = cityIdForShop(shopType);
        if (cityId <= 0) return Set.of();
        var available = new HashSet<Integer>();
        for (var entry : goods.int2ObjectEntrySet()) {
            int goodsId = entry.getIntKey();
            if (goodsCity.get(goodsId) == cityId && getResinCost(player, goodsId) > 0) {
                available.add(goodsId);
            }
        }
        return available;
    }

    @Override
    public boolean isAvailable(Player player, int shopType, int goodsId) {
        int cityId = cityIdForShop(shopType);
        return goods.get(goodsId) != null
                && cityId > 0
                && goodsCity.get(goodsId) == cityId
                && getResinCost(player, goodsId) > 0;
    }

    @Override
    public List<ItemParamData> getCostItems(Player player, int shopType, int goodsId) {
        int resinCost = getResinCost(player, goodsId);
        return resinCost > 0
                ? List.of(new ItemParamData(ORIGINAL_RESIN_ID, resinCost))
                : List.of();
    }

    @Override
    public Retcode validatePurchase(Player player, int shopType, ShopInfo goods, int buyCount) {
        var relics = player.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY);
        return buyCount <= relics.getMaxCapacity() - relics.getSize()
                ? Retcode.RET_SUCC
                : Retcode.RET_PACK_EXCEED_MAX_WEIGHT;
    }

    @Override
    public List<GameItem> createItems(Player player, ShopInfo goodsInfo, int buyCount) {
        var piece = goods.get(goodsInfo.getGoodsId());
        if (piece == null) return null;
        var rolled = new ArrayList<GameItem>(buyCount);
        for (int i = 0; i < buyCount; i++) rolled.add(roll(player, piece));
        return rolled;
    }

    @Override
    public int cityIdForShop(int shopType) {
        for (var route : options().regionalShops.entrySet()) {
            Integer cityId = route.getKey();
            Integer shopId = route.getValue();
            if (cityId != null && shopId != null && shopId == shopType) return cityId;
        }
        return 0;
    }

    public int getResinCost(Player player, int goodsId) {
        var piece = goods.get(goodsId);
        if (piece == null) return 0;
        int clearAr = highestClearedAr(player, piece, goodsCity.get(goodsId));
        return options().resinCost(clearAr, piece.getRankLevel());
    }

    private int highestClearedAr(Player player, ItemData piece, int cityId) {
        if (player == null
                || piece == null
                || cityId <= 0
                || player.getPlayerProgress() == null
                || player.getPlayerProgress().getCompletedDungeons() == null) {
            return 0;
        }

        int highest = 0;
        for (int dungeonId : player.getPlayerProgress().getCompletedDungeons()) {
            var dungeon = GameData.getDungeonDataMap().get(dungeonId);
            if (dungeon == null
                    || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY
                    || dungeon.getCityId() != cityId) {
                continue;
            }
            var sets = domainSetsByDungeon.get(dungeonId);
            if (sets != null && sets.contains(piece.getSetId())) {
                highest = Math.max(highest, dungeon.getLimitLevel());
            }
        }
        return highest;
    }

    private static Map<Integer, Set<Integer>> citiesBySet() {
        var out = new HashMap<Integer, Set<Integer>>();
        var routes = options().regionalShops;
        for (var entry : GameData.getDungeonDropDataMap().int2ObjectEntrySet()) {
            var dungeon = GameData.getDungeonDataMap().get(entry.getIntKey());
            if (dungeon == null
                    || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY
                    || !routes.containsKey(dungeon.getCityId())) {
                continue;
            }
            for (int setId : artifactSetIds(entry.getValue())) {
                out.computeIfAbsent(setId, k -> new TreeSet<>()).add(dungeon.getCityId());
            }
        }
        return out;
    }

    private static Int2ObjectMap<Set<Integer>> buildDomainSetIndex() {
        var exactSets = new Int2ObjectOpenHashMap<Set<Integer>>();
        for (var entry : GameData.getDungeonDropDataMap().int2ObjectEntrySet()) {
            var dungeon = GameData.getDungeonDataMap().get(entry.getIntKey());
            if (dungeon != null && dungeon.getSubType() == DungeonSubType.DUNGEON_SUB_RELIQUARY) {
                var sets = artifactSetIds(entry.getValue());
                if (!sets.isEmpty()) exactSets.put(entry.getIntKey(), sets);
            }
        }

        var index = new Int2ObjectOpenHashMap<Set<Integer>>();
        for (var pointEntry : GameData.getScenePointEntryMap().values()) {
            var point = pointEntry.getPointData();
            if (point == null || point.getDungeonIds() == null || point.getDungeonIds().length == 0) {
                continue;
            }

            var ids = new IntArrayList();
            var union = new HashSet<Integer>();
            boolean complete = true;
            for (int dungeonId : point.getDungeonIds()) {
                var dungeon = GameData.getDungeonDataMap().get(dungeonId);
                if (dungeon == null
                        || dungeon.getSubType() != DungeonSubType.DUNGEON_SUB_RELIQUARY) {
                    continue;
                }
                ids.add(dungeonId);
                var sets = exactSets.get(dungeonId);
                if (sets == null || sets.isEmpty()) {
                    complete = false;
                    break;
                }
                union.addAll(sets);
            }
            if (!complete || ids.isEmpty() || union.isEmpty()) continue;
            Set<Integer> shared = Set.copyOf(union);
            for (int i = 0; i < ids.size(); i++) index.put(ids.getInt(i), shared);
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

    public GameItem roll(Player player, ItemData piece) {
        var item = roll(piece);
        int worldLevel = player != null ? player.getWorldLevel() : 0;
        applyInitialEnhancement(item, piece, rollInitialEnhancementLevel(worldLevel));
        return item;
    }

    public GameItem roll(ItemData piece) {
        var item = new GameItem(piece);
        int mainPropId = rollMainProp(piece);
        if (mainPropId > 0) item.setMainPropId(mainPropId);
        item.setLevel(1);
        item.setExp(0);
        item.setTotalExp(0);
        item.getAppendPropIdList().clear();
        item.addAppendProps(piece.getAppendPropNum());
        return item;
    }

    static int[] initialEnhancementRange(int worldLevel) {
        var range = options().initialEnhancementRange(worldLevel);
        return new int[] {range.min, range.max};
    }

    private static int rollInitialEnhancementLevel(int worldLevel) {
        int[] range = initialEnhancementRange(worldLevel);
        if (range[0] == range[1]) return range[0];
        return ThreadLocalRandom.current().nextInt(range[0], range[1] + 1);
    }

    private static void applyInitialEnhancement(
            GameItem item, ItemData piece, int enhancementLevel) {
        int maxInternalLevel = Math.max(1, piece.getMaxLevel());
        int targetInternalLevel =
                Math.min(maxInternalLevel, Math.max(1, enhancementLevel + 1));
        int level = 1;
        int totalExp = 0;
        int upgrades = 0;

        while (level < targetInternalLevel) {
            int reqExp = GameData.getRelicExpRequired(piece.getRankLevel(), level);
            if (reqExp <= 0) break;
            totalExp += reqExp;
            level++;
            if (piece.canAddRelicProp(level)) upgrades++;
        }

        item.addAppendProps(upgrades);
        item.setLevel(level);
        item.setExp(0);
        item.setTotalExp(totalExp);
    }

    public static List<ItemData> catalog(Set<Integer> setIds, Set<Integer> ranks) {
        boolean allSets = setIds == null || setIds.isEmpty();
        boolean allRanks = ranks == null || ranks.isEmpty();
        var pieces = new ArrayList<ItemData>();

        for (var data : GameData.getItemDataMap().values()) {
            if (data.getItemType() != ItemType.ITEM_RELIQUARY) continue;
            int rank = data.getRankLevel();
            if (rank < 2 || rank > 5) continue;
            if (!allRanks && !ranks.contains(rank)) continue;
            if (!allSets && !setIds.contains(data.getSetId())) continue;
            if (!isCanonicalDomainPiece(data)) continue;
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

    private static boolean isCanonicalDomainPiece(ItemData data) {
        int rank = data.getRankLevel();
        return data.getAppendPropNum() == rank - 1
                && data.getAppendPropDepotId() == rank * 100 + 1
                && data.getMainPropDepotId() == mainPropDepot(data.getEquipType());
    }

    private static ShopInfo makeGoods(int goodsId, ItemData piece, int buyLimit) {
        var goods = new ShopInfo();
        goods.setGoodsId(goodsId);
        goods.setGoodsItem(new ItemParamData(piece.getId(), 1));
        goods.setScoin(0);
        goods.setHcoin(0);
        goods.setBuyLimit(buyLimit);
        goods.setMinLevel(1);
        goods.setMaxLevel(99);
        goods.setCostItemList(
                new ArrayList<>(List.of(new ItemParamData(ORIGINAL_RESIN_ID, FALLBACK_RESIN_COST))));
        return goods;
    }

    private static int rollMainProp(ItemData piece) {
        var pool = MAIN_STATS.get(piece.getEquipType());
        var candidates = GameDepot.getRelicMainPropList(piece.getMainPropDepotId());
        if (pool == null || candidates == null) return 0;
        var random = new WeightedList<ReliquaryMainPropData>();
        for (var prop : candidates) {
            double weight = pool.getOrDefault(prop.getFightProp(), 0d);
            if (weight > 0) random.add(weight, prop);
        }
        return random.size() == 0 ? 0 : random.next().getId();
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
