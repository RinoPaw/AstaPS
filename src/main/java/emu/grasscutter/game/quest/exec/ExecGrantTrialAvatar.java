package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_GRANT_TRIAL_AVATAR)
public class ExecGrantTrialAvatar extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        try {
            quest
                    .getOwner()
                    .getTeamManager()
                    .addTrialAvatar(Integer.parseInt(paramStr[0]), quest.getMainQuestId());
            if (quest.getSubQuestId() == 35301) {
                var team = quest.getOwner().getTeamManager();
                Grasscutter.getLogger().info(
                        "[MondstadtTrial] uid={} quest=35301 trialId={} usingTrialTeam={} trialIds={} teamSize={}",
                        quest.getOwner().getUid(), paramStr[0], team.isUsingTrialTeam(),
                        team.getTrialAvatars().keySet(), team.getActiveTeam().size());
            } else {
                Grasscutter.getLogger()
                        .debug("Added trial avatar to team for quest {}", quest.getSubQuestId());
            }
            return true;
        } catch (RuntimeException exception) {
            Grasscutter.getLogger().error(
                    "[MondstadtTrial] quest={} trial grant failed",
                    quest.getSubQuestId(), exception);
            return false;
        }
    }
}
