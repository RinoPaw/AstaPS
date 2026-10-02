package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BattlePassSetRewardPlanReq._BattlePassSetRewardPlanReq;
import emu.grasscutter.net.proto.BattlePassSetRewardPlanRsp._BattlePassSetRewardPlanRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;

@Opcodes(PacketOpcodes._BattlePassSetRewardPlanReq)
public class HandlerBattlePassSetRewardPlanReq extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] header, byte[] payload) throws Exception {
        Player player = gameSession.getPlayer();
        int plan = 1;
        boolean noRemind = false;
        try {
            var req = _BattlePassSetRewardPlanReq.parseFrom(payload);
            if (req.getBattlePassPlan() > 0) plan = req.getBattlePassPlan();
            noRemind = req.getIsNoRemind();
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("SetRewardPlan parse failed", throwable);
        }
        if (player != null && player.getBattlePassManager() != null) {
            player.getBattlePassManager().setSelectedRewardPlan(plan);
            player.getBattlePassManager().save();
            Grasscutter.getLogger()
                    .info("SetRewardPlan uid={} plan={} noRemind={}", player.getUid(), plan, noRemind);
        }
        var rsp = _BattlePassSetRewardPlanRsp.newBuilder().setBattlePassPlan(plan);
        for (int tier = 1; tier <= 5; ++tier) rsp.addAffectedTierIdList(tier);
        var packet = new BasePacket(PacketOpcodes._BattlePassSetRewardPlanRsp);
        packet.setData(rsp.build());
        gameSession.send(packet);
        if (player != null) {
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
        }
    }
}
