package emu.grasscutter.game.shop;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ShopGoodsData;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import lombok.Getter;

public class ShopSystem extends BaseGameSystem {
    private static final int REFRESH_HOUR = 4; // In GMT+8 server
    private static final String TIME_ZONE = "Asia/Shanghai"; // GMT+8 Timezone

    private final Int2ObjectMap<List<ShopInfo>> shopData;
    private final Int2ObjectMap<List<ItemParamData>> shopChestData;

    @Getter private final ArtifactShop artifactShop = new ArtifactShop();
    private final List<DynamicShopProvider> dynamicShopProviders = List.of(artifactShop);

    public ShopSystem(GameServer server) {
        super(server);
        this.shopData = new Int2ObjectOpenHashMap<>();
        this.shopChestData = new Int2ObjectOpenHashMap<>();
        this.load();
    }

    public static int getShopNextRefreshTime(ShopInfo shopInfo) {
        return switch (shopInfo.getShopRefreshType()) {
            case SHOP_REFRESH_DAILY -> Utils.getNextTimestampOfThisHour(
                    REFRESH_HOUR, TIME_ZONE, shopInfo.getShopRefreshParam());
            case SHOP_REFRESH_WEEKLY -> Utils.getNextTimestampOfThisHourInNextWeek(
                    REFRESH_HOUR, TIME_ZONE, shopInfo.getShopRefreshParam());
            case SHOP_REFRESH_MONTHLY -> Utils.getNextTimestampOfThisHourInNextMonth(
                    REFRESH_HOUR, TIME_ZONE, shopInfo.getShopRefreshParam());
            default -> 0;
        };
    }

    public Int2ObjectMap<List<ShopInfo>> getShopData() {
        return shopData;
    }

    public List<ItemParamData> getShopChestData(int chestId) {
        return this.shopChestData.get(chestId);
    }

    public DynamicShopProvider getDynamicShopProvider(int goodsId) {
        for (var provider : dynamicShopProviders) {
            if (provider.ownsGoods(goodsId)) return provider;
        }
        return null;
    }

    public int getCityIdForShop(int shopType) {
        for (var provider : dynamicShopProviders) {
            int cityId = provider.cityIdForShop(shopType);
            if (cityId > 0) return cityId;
        }
        // Preserve the historical fallback for ordinary shops that do not expose a city mapping.
        return 1;
    }

    private void loadShop() {
        getShopData().clear();
        try {
            List<ShopTable> banners = DataLoader.loadList("Shop.json", ShopTable.class);
            if (banners.size() > 0) {
                for (ShopTable shopTable : banners) {
                    shopTable.getItems().forEach(ShopInfo::removeVirtualCosts);
                    getShopData().put(shopTable.getShopId(), shopTable.getItems());
                }
                Grasscutter.getLogger().debug("Shop data successfully loaded.");
            } else {
                Grasscutter.getLogger().error("Unable to load shop data. Shop data size is 0.");
            }

            if (GAME.enableShopItems) {
                // Shop.json is the curated source and every one of its shops also exists in the
                // excel data, so appending there would list those items twice. Fill only the
                // shops it does not define.
                GameData.getShopGoodsDataEntries()
                        .forEach(
                                (k, v) -> {
                                    int shopId = k.intValue();
                                    if (getShopData().containsKey(shopId)) return;

                                    var items = new ArrayList<ShopInfo>(v.size());
                                    for (ShopGoodsData sgd : v) {
                                        items.add(new ShopInfo(sgd));
                                    }
                                    getShopData().put(shopId, items);
                                });
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load shop data.", e);
        }
    }

    private void loadShopChest() {
        shopChestData.clear();
        try {
            Map<Integer, String> chestMap =
                    DataLoader.loadMap("ShopChest.v2.json", Integer.class, String.class);
            chestMap.forEach(
                    (chestId, itemStr) -> {
                        if (itemStr.isEmpty()) return;
                        var entries = itemStr.split(",");
                        var list = new ArrayList<ItemParamData>(entries.length);
                        for (var entry : entries) {
                            var idAndCount = entry.split(":");
                            int id = Integer.parseInt(idAndCount[0]);
                            int count = Integer.parseInt(idAndCount[1]);
                            list.add(new ItemParamData(id, count));
                        }
                        this.shopChestData.put((int) chestId, list);
                    });
            Grasscutter.getLogger().debug("Loaded " + chestMap.size() + " ShopChest entries.");
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load ShopChest data.", e);
        }
    }

    public synchronized void load() {
        loadShop();
        loadShopChest();
        loadDynamicShops();
    }

    /** Rebuilds every player-aware provider after static shop/resource data changes. */
    public synchronized void loadDynamicShops() {
        for (var provider : dynamicShopProviders) {
            provider.install(getShopData());
        }
    }

    /** Compatibility entry point used by the current resource reload path. */
    public synchronized void loadArtifactShop() {
        loadDynamicShops();
    }

    public GameServer getServer() {
        return server;
    }
}
