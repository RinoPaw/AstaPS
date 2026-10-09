package emu.grasscutter.data.quest;

import emu.grasscutter.data.excels.quest.QuestData;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Runtime-safe projection of one native subquest row.
 *
 * <p>For condition/exec lists, null means "decoded, but contains semantics that are not independently
 * confirmed yet"; an empty list means the native row authoritatively has no entries.
 */
@Getter
@Builder
public class NativeQuestOverlay {
    private int subId;
    private Integer mainId;
    private Integer order;
    private Boolean rewind;
    private Boolean finishParent;
    private List<QuestData.QuestContentCondition> finishCond;
    private List<QuestData.QuestContentCondition> failCond;
    private List<QuestData.QuestExecParam> finishExec;
    private List<QuestData.QuestExecParam> failExec;
}
