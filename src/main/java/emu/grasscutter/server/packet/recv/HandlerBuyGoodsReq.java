package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBuyGoodsRsp;
import emu.grasscutter.utils.Utils;
import java.util.*;

@Opcodes(PacketOpcodes.BuyGoodsReq)
public class HandlerBuyGoodsReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        BuyGoodsReqOuterClass.BuyGoodsReq buyGoodsReq =
                BuyGoodsReqOuterClass.BuyGoodsReq.parseFrom(payload);
        var shopSystem = session.getServer().getShopSystem();
        List<ShopInfo> configShop = shopSystem.getShopData().get(buyGoodsReq.getShopType());
        if (configShop == null) {
            session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
            return;
        }

        // Don't trust your users' input
        var player = session.getPlayer();

        // A non-positive count buys nothing and cannot be honest. Worse, it inverts the whole
        // purchase: payItems checks `held < cost * count`, which is never true for a negative
        // count, and payVirtualItem then subtracts that negative - handing out mora and
        // primogems instead of taking them.
        int buyCount = buyGoodsReq.getBuyCount();
        if (buyCount <= 0) {
            session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
            return;
        }

        List<Integer> targetShopGoodsId = List.of(buyGoodsReq.getGoods().getGoodsId());
        for (int goodsId : targetShopGoodsId) {
            Optional<ShopInfo> sg2 =
                    configShop.stream().filter(x -> x.getGoodsId() == goodsId).findFirst();
            if (sg2.isEmpty()) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
                continue;
            }
            ShopInfo sg = sg2.get();
            int itemId = sg.getGoodsItem().getId();
            AvatarCostumeData costumeData = GameData.getAvatarCostumeDataItemIdMap().get(itemId);

            // Already unlocked costume: refuse repurchase (client may still send BuyGoods).
            if (costumeData != null
                    && player.getCostumeList() != null
                    && player.getCostumeList().contains(costumeData.getId())) {
                int soldOut = Math.max(1, sg.getBuyLimit());
                ShopLimit ownedLimit = player.getGoodsLimit(sg.getGoodsId());
                if (ownedLimit == null) {
                    player.addShopLimit(sg.getGoodsId(), soldOut, 0);
                } else {
                    ownedLimit.setHasBoughtInPeriod(
                            Math.max(ownedLimit.getHasBoughtInPeriod(), soldOut));
                    ownedLimit.setHasBought(Math.max(ownedLimit.getHasBought(), soldOut));
                    ownedLimit.setNextRefreshTime(0);
                }
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT));
                continue;
            }

            int currentTs = Utils.getCurrentSeconds();
            boolean refreshes = sg.getShopRefreshType() != ShopInfo.ShopRefreshType.NONE;
            ShopLimit shopLimit = player.getGoodsLimit(sg.getGoodsId());
            int bought = 0;
            if (shopLimit != null) {
                // Period expired: reset monthly/weekly/daily counter before checking limit.
                if (refreshes
                        && shopLimit.getNextRefreshTime() > 0
                        && currentTs >= shopLimit.getNextRefreshTime()) {
                    shopLimit.setHasBoughtInPeriod(0);
                    shopLimit.setNextRefreshTime(ShopSystem.getShopNextRefreshTime(sg));
                }
                bought = shopLimit.getHasBoughtInPeriod();
                player.save();
            }

            if (((long) bought + buyCount > sg.getBuyLimit()) && sg.getBuyLimit() != 0) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT));
                continue;
            }

            var dynamicProvider = shopSystem.getDynamicShopProvider(sg.getGoodsId());
            List<ItemParamData> dynamicCostOverride = null;
            if (dynamicProvider != null) {
                // Synthetic shop entries are presentation only. Re-run the provider's player-aware
                // authorization and validation before charging anything.
                if (!dynamicProvider.isAvailable(
                        player, buyGoodsReq.getShopType(), sg.getGoodsId())) {
                    session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_CONTENT_NOT_MATCH));
                    continue;
                }

                Retcode validation =
                        dynamicProvider.validatePurchase(
                                player, buyGoodsReq.getShopType(), sg, buyCount);
                if (validation != Retcode.RET_SUCC) {
                    session.send(new PacketBuyGoodsRsp(validation));
                    continue;
                }
                dynamicCostOverride =
                        dynamicProvider.getCostItems(
                                player, buyGoodsReq.getShopType(), sg.getGoodsId());
            }

            // Materialize the exact delivery before charging. Equipment purchases with a
            // quantity greater than one must create separate instances.
            List<GameItem> items;
            int itemCount;
            try {
                List<GameItem> dynamicItems = dynamicProvider == null
                        ? null
                        : dynamicProvider.createItems(player, sg, buyCount);
                if (dynamicItems != null) {
                    items = List.copyOf(dynamicItems);
                    itemCount = 0;
                } else {
                    itemCount = Math.multiplyExact(buyCount, sg.getGoodsItem().getCount());
                    items = InventoryGrantBuilder.create(
                            GameData.getItemDataMap().get(itemId), itemCount, 1);
                }
                if (items.isEmpty()) {
                    throw new IllegalArgumentException("Empty shop delivery");
                }
            } catch (RuntimeException badDelivery) {
                Grasscutter.getLogger().warn(
                        "Shop purchase rejected: invalid delivery goods={} uid={}",
                        sg.getGoodsId(), player.getUid(), badDelivery);
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
                continue;
            }

            List<ItemParamData> costs = new ArrayList<>(
                    dynamicCostOverride != null
                            ? dynamicCostOverride
                            : sg.getCostItemList() != null
                                    ? sg.getCostItemList()
                                    : Collections.emptyList());
            costs.add(new ItemParamData(202, sg.getScoin()));
            costs.add(new ItemParamData(201, sg.getHcoin()));
            costs.add(new ItemParamData(203, sg.getMcoin()));
            int nextRefresh = refreshes ? ShopSystem.getShopNextRefreshTime(sg) : 0;
            var inventory = player.getInventory();
            boolean delivered;
            try {
                if (costumeData != null && dynamicProvider == null) {
                    // Costumes unlock an account flag rather than occupying an inventory
                    // slot. Do not charge through a checked bag grant that rejects useOnGain.
                    synchronized (inventory) {
                        delivered = buyCount == 1 && itemCount == 1
                                && player.getCostumeList() != null
                                && !player.getCostumeList().contains(costumeData.getId())
                                && withinCurrentLimit(player.getGoodsLimit(sg.getGoodsId()), sg, buyCount)
                                && inventory.payItems(costs, buyCount);
                        if (delivered) {
                            player.addShopLimit(sg.getGoodsId(), buyCount, nextRefresh);
                            player.addCostume(costumeData.getId());
                        }
                    }
                } else {
                    var outcome = inventory.addItems(
                            items,
                            ActionReason.Shop,
                            InventoryAddPolicy.ALL_OR_NOTHING,
                            () -> withinCurrentLimit(
                                            player.getGoodsLimit(sg.getGoodsId()), sg, buyCount)
                                    && inventory.payItems(costs, buyCount),
                            () -> player.addShopLimit(sg.getGoodsId(), buyCount, nextRefresh));
                    delivered = outcome.allAccepted();
                    if (!delivered) {
                        Grasscutter.getLogger().warn(
                                "Shop purchase refused goods={} uid={} result={}",
                                sg.getGoodsId(), player.getUid(), outcome.entries());
                    }
                }
            } catch (RuntimeException failure) {
                Grasscutter.getLogger().error(
                        "Shop delivery interrupted goods={} uid={}",
                        sg.getGoodsId(), player.getUid(), failure);
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SVR_ERROR));
                continue;
            }
            if (!delivered) {
                session.send(new PacketBuyGoodsRsp(Retcode.RET_SHOP_CONTENT_NOT_MATCH));
                continue;
            }

            // Return full server-side goods (buy_limit / next_refresh), not the client stub.
            var limit = player.getGoodsLimit(sg.getGoodsId());
            session.send(
                    new PacketBuyGoodsRsp(
                            buyGoodsReq.getShopType(),
                            limit.getHasBoughtInPeriod(),
                            sg,
                            refreshes ? limit.getNextRefreshTime() : 0,
                            dynamicCostOverride));
        }

        player.save();
    }
    private static boolean withinCurrentLimit(ShopLimit current, ShopInfo goods, int count) {
        int bought = current == null ? 0 : current.getHasBoughtInPeriod();
        return count > 0
                && (goods.getBuyLimit() == 0
                        || (long) bought + count <= goods.getBuyLimit());
    }

}
