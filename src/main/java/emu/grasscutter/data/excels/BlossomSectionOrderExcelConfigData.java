package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/** Ordered blossom sections, indexed by the source row id. */
@Getter
@ResourceType(name = "BlossomSectionOrderExcelConfigData.json")
public final class BlossomSectionOrderExcelConfigData extends GameResource {
    private int id;
    private int cityId;
    private int sectionId;
    private int order;
}
