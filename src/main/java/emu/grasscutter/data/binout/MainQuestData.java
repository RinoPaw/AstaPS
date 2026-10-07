package emu.grasscutter.data.binout;

import com.google.gson.annotations.SerializedName;
import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.LogicType;
import emu.grasscutter.game.quest.enums.QuestType;
import java.util.*;
import lombok.Data;

public class MainQuestData {
    public MainQuestData() {}

    /**
     * Builds the runtime parent-quest skeleton from fields proven by the native 7.1 Quest decoder.
     *
     * <p>Talks, rewards and successor links stay unset until their native semantics are confirmed.
     */
    public MainQuestData(
            int id,
            SubQuestData[] subQuests,
            int[] suggestTrackMainQuestList,
            int[] rewardIdList,
            List<TalkData> talks) {
        this.id = id;
        this.subQuests = subQuests != null ? subQuests : new SubQuestData[0];
        this.suggestTrackMainQuestList = suggestTrackMainQuestList;
        this.rewardIdList = rewardIdList;
        this.talks = talks != null ? new ArrayList<>(talks) : new ArrayList<>();
    }

    public void mergeNativeRuntimeMetadata(
            int[] suggestTrackMainQuestList, int[] rewardIdList, List<TalkData> talks) {
        if (suggestTrackMainQuestList != null) {
            this.suggestTrackMainQuestList = suggestTrackMainQuestList;
        }
        if (rewardIdList != null) {
            this.rewardIdList = rewardIdList;
        }
        if (talks != null && !talks.isEmpty()) {
            this.talks = new ArrayList<>(talks);
        }
    }

    private int id;
    private int ICLLDPJFIMA;
    private int series;
    private QuestType type;

    private long titleTextMapHash;
    private int[] suggestTrackMainQuestList;
    private int[] rewardIdList;

    private SubQuestData[] subQuests;
    private List<TalkData> talks;
    /**
     * Nothing reads this: it exists so Gson has somewhere to put the field. It is typed as strings
     * because the ids outgrew {@code long} in the 4.5-era resources, and a value past
     * {@link Long#MAX_VALUE} makes Gson throw while parsing QuestData.json, which fails the whole
     * quest load rather than just this field.
     */
    private List<String> preloadLuaList;

    public int getId() {
        return id;
    }

    public int getSeries() {
        return series;
    }

    public QuestType getType() {
        return type;
    }

    public long getTitleTextMapHash() {
        return titleTextMapHash;
    }

    public int[] getSuggestTrackMainQuestList() {
        return suggestTrackMainQuestList;
    }

    public int[] getRewardIdList() {
        return rewardIdList;
    }

    public SubQuestData[] getSubQuests() {
        return subQuests;
    }

    public List<TalkData> getTalks() {
        return talks;
    }

    public void onLoad() {
        if (this.talks == null) this.talks = new ArrayList<>();
        if (this.subQuests == null) this.subQuests = new SubQuestData[0];

        this.talks = this.talks.stream().filter(Objects::nonNull).toList();
        this.talks.forEach(talkData -> GameData.getQuestTalkMap().put(talkData.getId(), this.getId()));

        // Normalize only rows that already exist in QuestExcel. Bin-only rows stay as raw evidence
        // until their source/materialization role is proven; promoting them now could turn a missing
        // acceptCond into an unconditional runtime quest.
        Arrays.stream(this.subQuests)
                .filter(Objects::nonNull)
                .filter(quest -> quest.getSubId() != 0)
                .forEach(
                        quest -> {
                            var canonical = GameData.getQuestDataMap().get(quest.getSubId());
                            if (canonical != null) {
                                canonical.mergeFromBinOutput(quest, this.id);
                            } else {
                                QuestData.auditBinOnlySubQuest(quest, this.id);
                            }
                        });
    }

    @Data
    public static class SubQuestData {
        private int subId;
        private Integer mainId;
        private Integer order;
        private Long descTextMapHash;

        @SerializedName("isMpBlock")
        private Boolean mpBlock;

        @SerializedName("isRewind")
        private Boolean rewind;

        private Boolean finishParent;

        private LogicType acceptCondComb;
        private LogicType finishCondComb;
        private LogicType failCondComb;

        private List<QuestData.QuestAcceptCondition> acceptCond;
        private List<QuestData.QuestContentCondition> finishCond;
        private List<QuestData.QuestContentCondition> failCond;
        private List<QuestData.QuestExecParam> beginExec;
        private List<QuestData.QuestExecParam> finishExec;
        private List<QuestData.QuestExecParam> failExec;
        private QuestData.Guide guide;

        private String showType;
        private String banType;
        private String showGuide;

        private List<Integer> trialAvatarList;
        private List<ItemParamData> gainItems;
    }

    @Data
    @Entity
    public static class TalkData {
        private int id;
        private String heroTalk;

        public TalkData() {}

        public TalkData(int id, String heroTalk) {
            this.id = id;
            this.heroTalk = heroTalk;
        }
    }
}
