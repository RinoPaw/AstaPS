package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.lang.Language;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "talent",
        permission = "player.settalent",
        permissionTargeted = "player.settalent.others")
public final class TalentCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("set", new SetById(sender, targetPlayer));
        commandLine.addSubcommand("n", new SetSlot(sender, targetPlayer, Slot.NORMAL));
        commandLine.addSubcommand("e", new SetSlot(sender, targetPlayer, Slot.SKILL));
        commandLine.addSubcommand("q", new SetSlot(sender, targetPlayer, Slot.BURST));
        commandLine.addSubcommand("all", new SetAll(sender, targetPlayer));
        commandLine.addSubcommand("getid", new GetIds(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "talent")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            TalentCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract class AvatarCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private AvatarCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Avatar avatar() {
            return targetPlayer.getTeamManager().getCurrentAvatarEntity().getAvatar();
        }

        protected AvatarSkillDepotData skillDepot(Avatar avatar) {
            AvatarSkillDepotData depot = avatar.getSkillDepot();
            if (depot == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.talent.invalid_skill_id");
            }
            return depot;
        }
    }

    private final class SetById extends AvatarCommand {
        @Parameters(index = "0", paramLabel = "<talentId>")
        private int skillId;

        @Parameters(index = "1", paramLabel = "<level>")
        private int level;

        private SetById(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Avatar avatar = avatar();
            if (skillDepot(avatar) == null) {
                return;
            }
            setTalentLevel(sender, avatar, skillId, level);
        }
    }

    private final class SetSlot extends AvatarCommand {
        private final Slot slot;

        @Parameters(index = "0", paramLabel = "<level>")
        private int level;

        private SetSlot(Player sender, Player targetPlayer, Slot slot) {
            super(sender, targetPlayer);
            this.slot = slot;
        }

        @Override
        public void run() {
            Avatar avatar = avatar();
            AvatarSkillDepotData depot = skillDepot(avatar);
            if (depot == null) {
                return;
            }

            int skillId =
                    switch (slot) {
                        case NORMAL -> depot.getSkills().get(0);
                        case SKILL -> depot.getSkills().get(1);
                        case BURST -> depot.getEnergySkill();
                    };
            setTalentLevel(sender, avatar, skillId, level);
        }
    }

    private final class SetAll extends AvatarCommand {
        @Parameters(index = "0", paramLabel = "<level>")
        private int level;

        private SetAll(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            if (level < 1 || level > 15) {
                CommandOutput.sendTranslatedMessage(sender, "commands.talent.out_of_range");
                return;
            }

            Avatar avatar = avatar();
            AvatarSkillDepotData depot = skillDepot(avatar);
            if (depot == null) {
                return;
            }
            depot.getSkillsAndEnergySkill().forEach(id -> setTalentLevel(sender, avatar, id, level));
        }
    }

    @CommandLine.Command(name = "getid")
    private final class GetIds extends AvatarCommand {
        private GetIds(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Avatar avatar = avatar();
            AvatarSkillDepotData depot = skillDepot(avatar);
            if (depot == null) {
                return;
            }

            var map = GameData.getAvatarSkillDataMap();
            depot.getSkillsAndEnergySkill()
                    .forEach(
                            id -> {
                                var talent = map.get(id);
                                if (talent == null) {
                                    return;
                                }
                                Object name = Language.getTextMapKey(talent.getNameTextMapHash());
                                Object desc = Language.getTextMapKey(talent.getDescTextMapHash());
                                if (name == null) {
                                    name = id;
                                }
                                if (desc == null) {
                                    desc = "";
                                }
                                CommandOutput.sendTranslatedMessage(
                                        sender, "commands.talent.id_desc", id, name, desc);
                            });
        }
    }

    private void setTalentLevel(Player sender, Avatar avatar, int skillId, int newLevel) {
        if (avatar.setSkillLevel(skillId, newLevel)) {
            var talent = GameData.getAvatarSkillDataMap().get(skillId);
            Object name = talent != null ? Language.getTextMapKey(talent.getNameTextMapHash()) : null;
            if (name == null) {
                name = skillId;
            }
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.talent.set_id", skillId, name, newLevel);
        } else {
            CommandOutput.sendTranslatedMessage(sender, "commands.talent.out_of_range");
        }
    }

    private enum Slot {
        NORMAL,
        SKILL,
        BURST
    }
}
