package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopRspOuterClass;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;
import emu.grasscutter.net.proto.ShopOuterClass.Shop;
import emu.grasscutter.utils.Utils;
import java.util.*;

public class PacketGetShopRsp extends BasePacket {
    public PacketGetShopRsp(Player player, int shopType) {
        super(PacketOpcodes.GetShopRsp);

        Shop.Builder shop =
                Shop.newBuilder()
                        .setShopType(shopType)
                        .setCityId(cityIdForShop(shopType))
                        .setCityReputationLevel(10); // mock

        ShopSystem manager = Grasscutter.getGameServer().getShopSystem();
        if (manager.getShopData().get(shopType) != null) {
            List<ShopInfo> list = manager.getShopData().get(shopType);
            List<ShopGoods> goodsList = new ArrayList<>();
            int shopNextRefreshTime = 0;
            int currentTs = Utils.getCurrentSeconds();
            var artifactShop = manager.getArtifactShop();
            Set<Integer> availableArtifactGoods =
                    artifactShop.getAvailableGoodsIds(player, shopType);

            for (ShopInfo info : list) {
                // Artifact goods are already routed to the right city shop; each player only sees
                // sets from artifact domains cleared in that city and rarities their AR allows.
                var artifactPiece = artifactShop.getPiece(info.getGoodsId());
                if (artifactPiece != null && !availableArtifactGoods.contains(info.getGoodsId())) {
                    continue;
                }

                boolean refreshes =
                        info.getShopRefreshType() != ShopInfo.ShopRefreshType.NONE;
                int nextRefreshTime = ShopSystem.getShopNextRefreshTime(info);
                // Only invent a countdown for goods that actually refresh.
                // One-time skins (SHOP_REFRESH_NONE) must stay at 0 or the costume UI hangs.
                if (refreshes && nextRefreshTime <= 0 && info.getBuyLimit() > 0) {
                    nextRefreshTime =
                            Utils.getNextTimestampOfThisHourInNextMonth(4, "Asia/Shanghai", 1);
                }
                if (!refreshes) {
                    nextRefreshTime = 0;
                }

                ShopLimit currentShopLimit = player.getGoodsLimit(info.getGoodsId());
                int boughtNum = 0;

                if (currentShopLimit != null) {
                    if (refreshes
                            && currentShopLimit.getNextRefreshTime() > 0
                            && currentShopLimit.getNextRefreshTime() <= currentTs) {
                        currentShopLimit.setHasBoughtInPeriod(0);
                        currentShopLimit.setNextRefreshTime(nextRefreshTime);
                    } else if (refreshes
                            && currentShopLimit.getNextRefreshTime() <= 0
                            && nextRefreshTime > 0) {
                        currentShopLimit.setNextRefreshTime(nextRefreshTime);
                    } else if (!refreshes && currentShopLimit.getNextRefreshTime() != 0) {
                        // Clear stale countdown left by older shop logic.
                        currentShopLimit.setNextRefreshTime(0);
                    }
                    boughtNum = currentShopLimit.getHasBoughtInPeriod();
                    if (refreshes && currentShopLimit.getNextRefreshTime() > currentTs) {
                        nextRefreshTime = currentShopLimit.getNextRefreshTime();
                    } else if (!refreshes) {
                        nextRefreshTime = 0;
                    }
                } else {
                    player.addShopLimit(info.getGoodsId(), 0, nextRefreshTime);
                }

                // Already-owned costumes: mark sold out so the client shows them as owned and they cannot be bought again.
                boughtNum = Math.max(boughtNum, ownedCostumeBoughtNum(player, info));
                if (info.getBuyLimit() > 0 && boughtNum >= info.getBuyLimit()) {
                    syncSoldOutLimit(player, info.getGoodsId(), boughtNum, nextRefreshTime);
                }

                ShopGoods goods =
                        ShopGoodsBuilder.fromShopInfo(info, boughtNum, nextRefreshTime).build();
                goodsList.add(goods);

                if (refreshes
                        && nextRefreshTime > 0
                        && (shopNextRefreshTime == 0 || nextRefreshTime < shopNextRefreshTime)) {
                    shopNextRefreshTime = nextRefreshTime;
                }
            }

            shop.addAllGoodsList(goodsList);
            if (shopNextRefreshTime > 0) {
                shop.setNextRefreshTime(shopNextRefreshTime);
            }
        }

        player.save();
        this.setData(GetShopRspOuterClass.GetShopRsp.newBuilder().setShop(shop).build());
    }

    /** Preserve the old city-1 fallback for unrelated shops while reporting regional shops exactly. */
    private static int cityIdForShop(int shopType) {
        return switch (shopType) {
            case 1004 -> 1; // Mondstadt
            case 1008 -> 2; // Liyue
            case 1056 -> 3; // Inazuma
            case 1074 -> 4; // Sumeru
            case 1093 -> 5; // Fontaine
            case 1117 -> 6; // Natlan
            case 1134 -> 7; // Nod-Krai
            default -> 1;
        };
    }

    /** Returns buyLimit when the player already owns this costume item; otherwise 0. */
    private static int ownedCostumeBoughtNum(Player player, ShopInfo info) {
        if (info.getGoodsItem() == null || info.getBuyLimit() <= 0) {
            return 0;
        }
        AvatarCostumeData costume =
                GameData.getAvatarCostumeDataItemIdMap().get(info.getGoodsItem().getId());
        if (costume == null) {
            return 0;
        }
        if (player.getCostumeList() != null && player.getCostumeList().contains(costume.getId())) {
            return info.getBuyLimit();
        }
        return 0;
    }

    private static void syncSoldOutLimit(
            Player player, int goodsId, int boughtNum, int nextRefreshTime) {
        ShopLimit limit = player.getGoodsLimit(goodsId);
        if (limit == null) {
            player.addShopLimit(goodsId, boughtNum, nextRefreshTime);
            return;
        }
        if (limit.getHasBoughtInPeriod() < boughtNum) {
            limit.setHasBoughtInPeriod(boughtNum);
        }
        if (limit.getHasBought() < boughtNum) {
            limit.setHasBought(boughtNum);
        }
        limit.setNextRefreshTime(nextRefreshTime);
    }
}
