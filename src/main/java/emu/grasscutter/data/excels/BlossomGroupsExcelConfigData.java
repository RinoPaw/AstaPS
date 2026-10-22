package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import java.util.List;
import lombok.Getter;

/** Imported 7.1 camp/section references. Native runtime semantics require separate verification. */
@Getter
@ResourceType(name = "BlossomGroupsExcelConfigData.json")
public final class BlossomGroupsExcelConfigData extends GameResource {
    private int id;
    private int cityId;
    private int sectionId;
    private int limit_level;
    private List<Integer> refreshTypeVec;
    private List<Integer> newGroupVec;
    private List<Integer> nextCampIdVec;
}
