package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.props.ItemUseOp;

public final class ItemUseAddPhlogiston extends ItemUseInt {
    public ItemUseAddPhlogiston(String[] useParam) {
        super(useParam == null ? new String[0] : useParam);
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_ADD_PHLOGISTON;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        if (params.player == null
                || !params.player.isInNatlan()
                || this.i <= 0
                || params.count <= 0) {
            return false;
        }
        float before = params.player.getPhlogistonValue();
        return params.player.addPhlogistonValue((float) this.i * params.count) > before;
    }
}
