package emu.grasscutter.game.shop;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;

/**
 * Player-aware shop extension point.
 *
 * <p>A provider may install synthetic goods into ordinary shops, hide them per player, override
 * their price, validate a purchase, and create custom item instances. Shop packets and purchase
 * handling stay generic and do not need to know which feature owns a dynamic good.
 */
public interface DynamicShopProvider {
    void install(Int2ObjectMap<List<ShopInfo>> shopData);

    boolean ownsGoods(int goodsId);

    boolean isAvailable(Player player, int shopType, int goodsId);

    /** Returns null to keep the static ShopInfo cost. */
    default List<ItemParamData> getCostItems(Player player, int shopType, int goodsId) {
        return null;
    }

    default Retcode validatePurchase(Player player, int shopType, ShopInfo goods, int buyCount) {
        return Retcode.RET_SUCC;
    }

    /** Returns null to use the ordinary stacked-item delivery path. */
    default List<GameItem> createItems(Player player, ShopInfo goods, int buyCount) {
        return null;
    }

    /** Returns 0 when this provider does not define the city's identity for this shop. */
    default int cityIdForShop(int shopType) {
        return 0;
    }
}
