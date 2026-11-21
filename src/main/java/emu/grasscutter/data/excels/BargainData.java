package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "BargainExcelConfigData.json")
public final class BargainData extends GameResource {
    @Getter private int id;
    @SerializedName(value = "questId", alternate = "quest_id")
    private int questId;

    @SerializedName(value = "dialogId", alternate = "dialog_id")
    private List<Integer> dialogId;

    /**
     * This is a list of 2 integers. The first integer is the minimum value of the bargain. The second
     * integer is the maximum value of the bargain.
     */
    @SerializedName(value = "expectedValue", alternate = "expected_value")
    private List<Integer> expectedValue;

    private int space;

    @SerializedName(value = "successTalkId", alternate = "success_talk_id")
    private List<Integer> successTalkId;
    @SerializedName(value = "failTalkId", alternate = "fail_talk_id")
    private int failTalkId;
    @SerializedName(value = "moodNpcId", alternate = "mood_npc_id")
    private int moodNpcId;

    /**
     * This is a list of 2 integers. The first integer is the minimum value of the mood. The second
     * integer is the maximum value of the mood.
     */
    @SerializedName(value = "randomMood", alternate = "random_mood")
    private List<Integer> randomMood;

    @SerializedName(value = "moodAlertLimit", alternate = "mood_alert_limit")
    private int moodAlertLimit;
    @SerializedName(value = "moodLowLimit", alternate = "mood_low_limit")
    private int moodLowLimit;
    @SerializedName(value = "singleFailMoodDeduction", alternate = "single_fail_mood_deduction")
    private int singleFailMoodDeduction;

    @SerializedName(value = "moodLowLimitTextTextMapHash", alternate = "mood_low_limit_textTextMapHash")
    private long moodLowLimitTextTextMapHash;
    @SerializedName(value = "titleTextTextMapHash", alternate = "title_textTextMapHash")
    private long titleTextTextMapHash;
    @SerializedName(value = "affordTextTextMapHash", alternate = "afford_textTextMapHash")
    private long affordTextTextMapHash;
    @SerializedName(value = "storageTextTextMapHash", alternate = "storage_textTextMapHash")
    private long storageTextTextMapHash;
    @SerializedName(value = "moodHintTextTextMapHash", alternate = "mood_hint_textTextMapHash")
    private long moodHintTextTextMapHash;
    @SerializedName(value = "moodDescTextTextMapHash", alternate = "mood_desc_textTextMapHash")
    private long moodDescTextTextMapHash;

    @SerializedName(value = "singleFailTalkId", alternate = "single_fail_talk_id")
    private List<Integer> singleFailTalkId;

    @SerializedName(value = "deleteItem", alternate = "delete_item")
    private boolean deleteItem;
    @SerializedName(value = "itemId", alternate = "item_id")
    private int itemId;
}
