package emu.grasscutter.data.quest;

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
    private String version;
    private List<MainQuest> quests;

    @Data
    public static class MainQuest {
        private int mainId;
        private Integer resId;
        private List<SubQuest> quests;
    }

    @Data
    public static class SubQuest {
        private Integer mainId;
        private int subId;
        private Integer order;
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
