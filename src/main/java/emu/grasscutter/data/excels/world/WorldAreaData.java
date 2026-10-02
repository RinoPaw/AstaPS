package emu.grasscutter.data.excels.world;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;

/** Current 7.1 table field names are case-sensitive. */
@ResourceType(name = "WorldAreaConfigData.json")
public class WorldAreaData extends GameResource {
    @SerializedName("ID")
    private int ID;

    @Getter private ElementType elementType;

    @Getter
    @SerializedName("areaNameTextMapHash")
    private long textMapHash;

    @Getter
    @SerializedName("areaID1")
    private int parentArea;

    @Getter
    @SerializedName("areaID2")
    private int childArea;

    @Getter
    @SerializedName("sceneID")
    private int sceneId;

    @Override
    public int getId() {
        return (this.childArea << 16) + this.parentArea;
    }
}
