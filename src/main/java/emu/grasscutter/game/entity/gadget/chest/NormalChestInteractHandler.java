package emu.grasscutter.game.entity.gadget.chest;

import emu.grasscutter.game.entity.gadget.GadgetChest;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.reward.RewardScaler;
import emu.grasscutter.game.world.ChestReward;
import emu.grasscutter.server.event.player.PlayerOpenChestEvent;
import java.util.Random;

public class NormalChestInteractHandler implements ChestInteractHandler {
    private final ChestReward chestReward;

    public NormalChestInteractHandler(ChestReward rewardData) {
        this.chestReward = rewardData;
    }

    @Override
    public boolean isTwoStep() {
        return false;
    }

    @Override
    public boolean onInteract(GadgetChest chest, Player player) {
        // Invoke open chest event.
        var event = new PlayerOpenChestEvent(player, chest, this.chestReward);
        event.call();
        if (event.isCanceled()) return true;

        player.addExpDirectly(
                RewardScaler.scaleCount(
                        RewardScaler.ADVENTURE_EXP_ITEM_ID, chestReward.getAdvExp(), 1.0));
        player.getInventory().addItem(201, chestReward.getResin(), ActionReason.OpenChest);

        var baseMora = chestReward.getMora() * (1 + (player.getWorldLevel() - 1) * 0.5);
        int mora = RewardScaler.scaleCount(RewardScaler.MORA_ITEM_ID, (int) baseMora, 1.0);
        if (mora > 0) {
            player.getInventory().addItem(202, mora, ActionReason.OpenChest);
        }

        for (int i = 0; i < chestReward.getContent().size(); i++) {
            var item = chestReward.getContent().get(i);
            drop(chest, item.getItemId(), item.getCount());
        }

        var random = new Random(System.currentTimeMillis());
        for (int i = 0; i < chestReward.getRandomCount(); i++) {
            var index = random.nextInt(chestReward.getRandomContent().size());
            var item = chestReward.getRandomContent().get(index);
            drop(chest, item.getItemId(), item.getCount());
        }

        return true;
    }

    private static void drop(GadgetChest chest, int itemId, int baseCount) {
        int count = RewardScaler.scaleCount(itemId, baseCount, 1.0);
        if (count <= 0) return;
        chest.getGadget().getScene().addItemEntity(itemId, count, chest.getGadget());
    }
}
