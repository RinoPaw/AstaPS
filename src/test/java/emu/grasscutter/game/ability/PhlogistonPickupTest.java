package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.game.props.ItemUseTarget;

import org.junit.jupiter.api.Test;

final class PhlogistonPickupTest {
    @Test
    void nativePickupIsAnImmediateEffectWhileTheConsumableStaysInTheBag() {
        for (String materialType : new String[] {"MATERIAL_ADSORBATE", "MATERIAL_CONSUME"}) {
            var data =
                    new Gson()
                            .fromJson(
                                    """
{"id":2030,"itemType":"ITEM_MATERIAL","materialType":"%s",
 "useTarget":"ITEM_USE_TARGET_CUR_TEAM",
 "itemUse":[{"useOp":"ITEM_USE_ADD_PHLOGISTON","useParam":["20"]}]}
"""
                                            .formatted(materialType),
                                    ItemData.class);
            data.onLoad();
            assertEquals(materialType.equals("MATERIAL_ADSORBATE"), data.isUseOnGain());
            assertEquals(1, data.getItemUseActions().size());
            assertEquals(
                    ItemUseOp.ITEM_USE_ADD_PHLOGISTON,
                    data.getItemUseActions().get(0).getItemUseOp());
        }
        assertEquals(ItemUseOp.ITEM_USE_ADD_PHLOGISTON, ItemUseOp.getTypeByValue(60));
    }

    @Test
    void pickupRestoresConfiguredAmountAndClampsTheTeamPool() {
        var player =
                new Player() {
                    @Override
                    public boolean isInNatlan() {
                        return true;
                    }
                };
        player.setPhlogistonValue(70);
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_CUR_TEAM);
        var action = new ItemUseAddPhlogiston(new String[] {"20"});
        assertTrue(action.useItem(params));
        assertEquals(90f, player.getPhlogistonValue());
        params.count = 2;
        assertTrue(action.useItem(params));
        assertEquals(100f, player.getPhlogistonValue());
        assertFalse(action.useItem(params));
        player.setPhlogistonValue(Float.NaN);
        assertEquals(100f, player.getPhlogistonValue());
    }

    @Test
    void outsideNatlanAndMalformedAmountsCannotConsumeAnItem() {
        var params = new UseItemParams(new Player(), ItemUseTarget.ITEM_USE_TARGET_CUR_TEAM);
        params.player.setPhlogistonValue(10);
        assertFalse(new ItemUseAddPhlogiston(new String[] {"20"}).useItem(params));
        assertFalse(new ItemUseAddPhlogiston(null).useItem(params));
        assertEquals(10f, params.player.getPhlogistonValue());
    }
}
