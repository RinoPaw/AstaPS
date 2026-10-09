package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.binout.OpenConfigEntry.AbilityVarSetter;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "debug",
        permission = "grasscutter.command.debug",
        targetRequirement = Command.TargetRequirement.NONE)
public final class DebugCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("abilities", new Abilities(sender));
        commandLine.addSubcommand("entity", new CurrentEntity(sender));
        commandLine.addSubcommand("setvar", new SetVar(sender));
        commandLine.addSubcommand("dynamicmap", new DynamicMap(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "debug")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (sender != null) DebugCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "abilities")
    private static final class Abilities implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<entityId>")
        private int entityId;

        @Option(names = "--config", description = "Interpret the id as a config ID")
        private boolean configId;

        private Abilities(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (sender == null) return;
            var scene = sender.getScene();
            var entity =
                    configId
                            ? scene.getFirstEntityByConfigId(entityId)
                            : scene.getEntityById(entityId);
            if (entity == null) {
                sender.dropMessage("Entity not found.");
                return;
            }

            try {
                var abilities = entity.getInstancedAbilities();
                for (int i = 0; i < abilities.size(); i++) {
                    try {
                        var ability = abilities.get(i);
                        Grasscutter.getLogger()
                                .info(
                                        "Ability #{}: {}; Modifiers: {}",
                                        i,
                                        ability,
                                        ability.getModifiers().keySet());
                    } catch (Exception exception) {
                        Grasscutter.getLogger().warn("Failed to print ability #{}.", i, exception);
                    }
                }
                if (abilities.isEmpty()) {
                    Grasscutter.getLogger().info("No abilities found on {}.", entity);
                }
            } catch (Exception exception) {
                Grasscutter.getLogger().warn("Failed to get abilities.", exception);
            }
            sender.dropMessage("Check console for abilities.");
        }
    }

    @CommandLine.Command(name = "entity")
    private static final class CurrentEntity implements Runnable {
        private final Player sender;

        private CurrentEntity(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (sender == null) return;
            var avatarEntity = sender.getTeamManager().getCurrentAvatarEntity();
            if (avatarEntity == null) {
                sender.dropMessage("No current avatar entity.");
                return;
            }
            sender.dropMessage(
                    "Current avatar entityId="
                            + avatarEntity.getId()
                            + " avatarId="
                            + avatarEntity.getAvatar().getAvatarId()
                            + " configId="
                            + avatarEntity.getConfigId());
        }
    }

    @CommandLine.Command(name = "setvar")
    private static final class SetVar implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<entityId>")
        private int entityId;

        @Parameters(index = "1", paramLabel = "<varName>")
        private String varName;

        @Parameters(index = "2", paramLabel = "<floatValue>")
        private float value;

        @Option(names = "--ability", defaultValue = "1", paramLabel = "<index>")
        private int abilityIndex;

        private SetVar(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (sender == null) return;
            var entry =
                    AbilityScalarValueEntry.newBuilder()
                            .setKey(AbilityString.newBuilder().setStr(varName).build())
                            .setFloatValue(value)
                            .build();
            var overrideMap = AbilityMetaReInitOverrideMap.newBuilder().addOverrideMap(entry).build();
            var head =
                    AbilityInvokeEntryHead.newBuilder()
                            .setInstancedAbilityId(abilityIndex)
                            .build();
            var reinit =
                    AbilityInvokeEntry.newBuilder()
                            .setEntityId(entityId)
                            .setArgumentType(
                                    AbilityInvokeArgument
                                            .AbilityInvokeArgument_ABILITY_META_REINIT_OVERRIDEMAP)
                            .setForwardType(ForwardType.ForwardType_FORWARD_TO_ALL)
                            .setHead(head)
                            .setAbilityData(overrideMap.toByteString())
                            .build();
            sender.sendPacket(new PacketAbilityInvocationsNotify(reinit));

            var override =
                    AbilityInvokeEntry.newBuilder()
                            .setEntityId(entityId)
                            .setArgumentType(
                                    AbilityInvokeArgument
                                            .AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                            .setForwardType(ForwardType.ForwardType_FORWARD_TO_ALL)
                            .setHead(head)
                            .setAbilityData(entry.toByteString())
                            .build();
            sender.sendPacket(new PacketAbilityInvocationsNotify(override));
            sender.dropMessage(
                    "Sent REINIT_OVERRIDEMAP+OVERRIDE_PARAM: entity="
                            + entityId
                            + " ability="
                            + abilityIndex
                            + " "
                            + varName
                            + "="
                            + value);
        }
    }

    @CommandLine.Command(name = "dynamicmap")
    private static final class DynamicMap implements Runnable {
        private final Player sender;

        private DynamicMap(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (sender == null) return;
            var entity = sender.getTeamManager().getCurrentAvatarEntity();
            if (entity == null) {
                sender.dropMessage("No current avatar entity.");
                return;
            }
            var avatar = entity.getAvatar();
            var skillLevelMap = avatar.getSkillLevelMap();
            var depotData = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
            int total = 0;

            Grasscutter.getLogger()
                    .info(
                            "=== dynamicValueMap for avatar {} (depot {}) ===",
                            avatar.getAvatarId(),
                            avatar.getSkillDepotId());
            for (int proudSkillId : avatar.getProudSkillList()) {
                total += dumpProudSkillVars(proudSkillId, "passive");
            }
            if (depotData != null) {
                for (int skillId : depotData.getSkillsAndEnergySkill().toArray()) {
                    var skillCfg = GameData.getAvatarSkillDataMap().get(skillId);
                    if (skillCfg == null || skillCfg.getProudSkillGroupId() == 0) continue;
                    int level = skillLevelMap.getOrDefault(skillId, 1);
                    total += dumpProudSkillVars(
                            skillCfg.getProudSkillGroupId() * 100 + level,
                            "skill " + skillId + " lv" + level);
                }
            }
            Grasscutter.getLogger().info("=== total entries: {} ===", total);
            sender.dropMessage("Check console for dynamicValueMap (" + total + " entries).");
        }
    }

    private static int dumpProudSkillVars(int proudSkillId, String label) {
        ProudSkillData skillData = GameData.getProudSkillDataMap().get(proudSkillId);
        if (skillData == null || skillData.getOpenConfig() == null) return 0;
        OpenConfigEntry entry = GameData.getOpenConfigEntries().get(skillData.getOpenConfig());
        if (entry == null || entry.getAbilityVarSetters() == null) return 0;

        float[] params = skillData.getParamList();
        int count = 0;
        for (AbilityVarSetter setter : entry.getAbilityVarSetters()) {
            int index = setter.getParamIndex();
            float value = params != null && index < params.length ? params[index] : Float.NaN;
            Grasscutter.getLogger()
                    .info(
                            "  [{}] openConfig={} ability={} var={} paramIdx={} value={}",
                            label,
                            skillData.getOpenConfig(),
                            setter.getAbilityName(),
                            setter.getVarName(),
                            index,
                            value);
            count++;
        }
        return count;
    }
}
