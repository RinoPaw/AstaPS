package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
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
    @SerializedName("limit_level")
    private int limitLevel;
    private List<Integer> refreshTypeVec;
    private List<Integer> newGroupVec;
    private List<Integer> nextCampIdVec;
}
