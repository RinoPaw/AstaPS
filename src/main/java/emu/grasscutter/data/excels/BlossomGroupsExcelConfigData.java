package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import java.util.List;
import lombok.Getter;

/** Directly imported Global 7.1 BlossomGroupsExcelConfigData rows. */
@ResourceType(name = "BlossomGroupsExcelConfigData.json")
@Getter
public class BlossomGroupsExcelConfigData extends GameResource {
    private int id;
    private int cityId;
    private int sectionId;
    private List<Integer> refreshTypeVec;
    private List<Integer> newGroupVec;
    private List<Integer> nextCampIdVec;

    @Override
    public int getId() {
        return id;
    }
}
