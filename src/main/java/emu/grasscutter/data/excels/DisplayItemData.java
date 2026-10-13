package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/** UI preview item: param identifies its reliquary set, not a payable inventory item. */
@ResourceType(name = "DisplayItemExcelConfigData.json")
@Getter
public class DisplayItemData extends GameResource {
    private int id;
    private int param;

    @Override
    public void onLoad() {}
}
