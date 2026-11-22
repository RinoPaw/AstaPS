package emu.grasscutter.data.excels.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;

@ResourceType(name = "AvatarCostumeExcelConfigData.json")
public class AvatarCostumeData extends GameResource {
    @SerializedName(value = "skinId", alternate = "costumeId")
    private int skinId;

    private int itemId;
    private int characterId;
    private int quality;

    /**
     * True only for outfits the game itself considers already owned. In the shipped 7.2 table the
     * column is present on exactly 4 of 162 rows (200302/202101/204101/204501) and those four are
     * also the only {@code hide=true} rows: the free Jean/Amber/Mona/Rosaria alternate outfits.
     * The other 26 alternate outfits leave the key absent and are shop- or reward-obtainable.
     */
    private boolean isDefaultUnlock;

    @Override
    public int getId() {
        return this.skinId;
    }

    public int getItemId() {
        return this.itemId;
    }

    public int getCharacterId() {
        return characterId;
    }

    public int getQuality() {
        return quality;
    }

    /** Whether the game treats this outfit as owned without any acquisition step. */
    public boolean isDefaultUnlock() {
        return isDefaultUnlock;
    }

    @Override
    public void onLoad() {
        GameData.getAvatarCostumeDataItemIdMap().put(this.getItemId(), this);
    }
}
