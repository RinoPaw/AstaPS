package emu.grasscutter.data.quest;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

/**
 * JSON contract produced from the fail-closed Genshin-Reverse 7.1 MainQuest decoder.
 *
 * <p>This model deliberately contains only fields that the native decoder exposes. It has no
 * acceptCond or beginExec field, so the server cannot accidentally recreate legacy synthetic
 * prerequisites while reading this source.
 */
@Data
public class NativeQuestData {
    private int schemaVersion;
    private String gameVersion;
    private Source source;
    private Coverage coverage;
    private List<MainQuest> mainQuests;
    private List<FailedMainQuest> failedMainQuests;

    @Data
    public static class Source {
        private String repository;
        private String decoder;
        private String decoderCommit;
        private String policy;
    }

    @Data
    public static class Coverage {
        private int total;
        private int fullConsumed;
        private int failed;
        private long payloadBytes;
    }

    @Data
    public static class FailedMainQuest {
        private int mainId;
        private int size;
        private String error;
        private List<String> failureFamilies;
    }

    @Data
    public static class MainQuest {
        private int mainId;
        private Integer resId;
        private int payloadSize;
        private String payloadSha256;
        private int[] suggestTrackMainQuestList;
        private int[] rewardIdList;
        private List<Talk> talks;
        private List<SubQuest> quests;
    }

    @Data
    public static class Talk {
        private int id;
        private int questId;
    }

    @Data
    public static class SubQuest {
        private Integer mainId;
        private int subId;
        private Integer order;
        @SerializedName("isRewind")
        private Boolean rewind;
        private Boolean finishParent;
        private List<Content> failCond;
        private List<Content> finishCond;
        private List<Exec> failExec;
        private List<Exec> finishExec;
    }

    @Data
    public static class Content {
        private int typeId;
        private String type;
        private int[] param;
        private String paramStr;
        private Integer value;
    }

    @Data
    public static class Exec {
        private int typeId;
        private String type;
        private String[] param;
    }
}
