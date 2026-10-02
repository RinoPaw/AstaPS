package emu.grasscutter.server.packet.recv;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassReward;
import emu.grasscutter.game.battlepass.BattlePassSelectChestHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass;
import emu.grasscutter.net.proto.TakeBattlePassRewardReqOuterClass.TakeBattlePassRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketTakeBattlePassRewardRsp;
import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes.TakeBattlePassRewardReq)
public class HandlerTakeBattlePassRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session.getPlayer();
        if (player == null || player.getBattlePassManager() == null) return;

        BattlePassManager manager = player.getBattlePassManager();
        TakeBattlePassRewardReq req = TakeBattlePassRewardReq.parseFrom(payload);
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> options =
                new ArrayList<>(req.getTakeOptionListList());

        if (options.isEmpty()) {
            player.sendPacket(new PacketTakeBattlePassRewardRsp(options, List.of()));
            return;
        }

        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> normal =
                new ArrayList<>();
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> selectable =
                new ArrayList<>();
        for (var option : options) {
            if (option == null || !option.hasTag() || option.getTag().getRewardId() <= 0) continue;
            if (BattlePassSelectChestHelper.isSelectableReward(option.getTag().getRewardId())) {
                if (option.getOptionIdx() > 0) selectable.add(option);
            } else {
                normal.add(option);
            }
        }

        if (!normal.isEmpty()) {
            manager.takeReward(normal);
            if (selectable.isEmpty()) return;
        }

        List<GameItem> granted = new ArrayList<>();
        List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> claimed =
                new ArrayList<>();
        for (var option : selectable) {
            int rewardId = option.getTag().getRewardId();
            int level = option.getTag().getLevel();
            boolean paid =
                    option.getTag().getUnlockStatus()
                            == BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus
                                    .BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID;

            if (level <= 0
                    || level > manager.getLevel()
                    || manager.getTakenRewards().containsKey(rewardId)) {
                continue;
            }

            var rewardData =
                    GameData.getBattlePassRewardDataMap()
                            .get(GameConstants.BATTLE_PASS_CURRENT_INDEX * 100 + level);
            if (rewardData == null) {
                continue;
            }

            boolean allowed =
                    rewardData.getFreeRewardIdList() != null
                                    && rewardData.getFreeRewardIdList().contains(rewardId)
                            || manager.isPaid()
                                    && rewardData.getPaidRewardIdList() != null
                                    && rewardData.getPaidRewardIdList().contains(rewardId);
            if (!allowed) {
                continue;
            }

            List<GameItem> items =
                    BattlePassSelectChestHelper.grant(
                            player, rewardId, option.getOptionIdx(), paid);
            if (items.isEmpty()) continue;

            manager.getTakenRewards().put(rewardId, new BattlePassReward(level, rewardId, paid));
            granted.addAll(items);
            claimed.add(option);
        }

        if (!claimed.isEmpty()) {
            manager.save();
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketTakeBattlePassRewardRsp(claimed, granted));
        } else if (normal.isEmpty()) {
            player.sendPacket(new PacketTakeBattlePassRewardRsp(options, granted));
        }
    }
}
