package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarFetterLevelRewardReqOuterClass.AvatarFetterLevelRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

import it.unimi.dsi.fastutil.ints.Int2IntMap;

import java.util.*;

@Opcodes(PacketOpcodes.AvatarFetterLevelRewardReq)
public class HandlerAvatarFetterLevelRewardReq extends PacketHandler {
    static Map<Integer, Integer> selectRewards(
            Int2IntMap configured, int currentLevel, int requestedLevel, Set<Integer> claimed) {
        var rewards = new TreeMap<Integer, Integer>();
        configured.forEach(
                (level, rewardId) -> {
                    if (level > 0
                            && level <= currentLevel
                            && rewardId > 0
                            && (requestedLevel == 0 || requestedLevel == level)
                            && (claimed == null || !claimed.contains(level))) {
                        rewards.put(level, rewardId);
                    }
                });
        return rewards;
    }

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = AvatarFetterLevelRewardReq.parseFrom(payload);
        var player = session.getPlayer();
        long guid = req.getAvatarGuid();
        int level = req.getFetterLevel();
        var avatar = player.getAvatars().getAvatarByGuid(guid);
        if (avatar == null || avatar.getAvatarData() == null || level < 0) {
            session.send(new PacketAvatarFetterLevelRewardRsp(guid, level));
            return;
        }

        synchronized (avatar) {
            var configured = avatar.getAvatarData().getFetterRewards();
            if (configured.isEmpty()
                    || (level != 0
                            && (!configured.containsKey(level)
                                    || avatar.getFetterLevel() < level))) {
                session.send(new PacketAvatarFetterLevelRewardRsp(guid, level));
                return;
            }
            // 7.2 的按钮只发 GUID，等级 0 表示领取所有已达标奖励。
            var rewards =
                    selectRewards(
                            configured,
                            avatar.getFetterLevel(),
                            level,
                            avatar.getRewardedFetterLevels());
            var items = new ArrayList<GameItem>();
            for (int rewardId : rewards.values()) {
                var reward = GameData.getRewardDataMap().get(rewardId);
                if (reward == null) {
                    Grasscutter.getLogger()
                            .warn(
                                    "[Fetter] missing reward {} for avatar {}",
                                    rewardId,
                                    avatar.getAvatarId());
                    session.send(new PacketAvatarFetterLevelRewardRsp(guid, level));
                    return;
                }
                for (var param : reward.getAllRewardItems()) {
                    if (param.getId() <= 0 || param.getCount() <= 0) continue;
                    var data = GameData.getItemDataMap().get(param.getId());
                    if (data != null
                            && data.getStackLimit() == 1
                            && player.getInventory().getItemCountById(param.getId()) > 0) continue;
                    items.add(new GameItem(param.getId(), param.getCount()));
                }
            }
            var added =
                    items.isEmpty()
                            ? null
                            : player.getInventory().addItems(items, ActionReason.FetterLevelReward);
            if (added != null && !added.allAccepted()) {
                Grasscutter.getLogger()
                        .warn(
                                "[Fetter] uid={} avatar={} reward refused: {}",
                                player.getUid(),
                                avatar.getAvatarId(),
                                added.entries());
                session.send(new PacketAvatarFetterLevelRewardRsp(guid, level));
                return;
            }
            if (!rewards.isEmpty()) {
                rewards.keySet().forEach(avatar::addRewardedFetterLevel);
                avatar.save();
                if (items.stream().anyMatch(item -> item.getItemId() == avatar.getNameCardId())) {
                    player.sendPacket(new PacketUnlockNameCardNotify(avatar.getNameCardId()));
                }
            }
            session.send(new PacketAvatarFetterDataNotify(avatar));
            session.send(
                    new PacketAvatarFetterLevelRewardRsp(
                            guid, level, new ArrayList<>(rewards.values())));
        }
    }
}
