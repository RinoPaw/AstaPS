package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityNPC;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketGroupSuiteNotify;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Places or inspects NPCs for 7.1 scene/quest debugging. */
@Command(
        label = "npc",
        usage = {"<npcId>", "near [radius]", "group <groupId> <suiteId>", "clear"},
        permission = "server.npc",
        permissionTargeted = "server.npc.others")
public final class NpcCommand implements CommandHandler {
    private static final double DISTANCE = 2.5;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            this.sendUsageMessage(sender);
            return;
        }

        var scene = targetPlayer.getScene();
        if (scene == null) return;

        if (args.get(0).equalsIgnoreCase("near")) {
            this.listNearby(sender, targetPlayer, args);
            return;
        }

        if (args.get(0).equalsIgnoreCase("group")) {
            if (args.size() < 3) {
                this.sendUsageMessage(sender);
                return;
            }
            try {
                int groupId = Integer.parseInt(args.get(1));
                int suiteId = Integer.parseInt(args.get(2));
                targetPlayer.sendPacket(new PacketGroupSuiteNotify(groupId, suiteId));
                CommandHandler.sendMessage(
                        sender,
                        "Sent scene group " + groupId + " suite " + suiteId + " to the client.");
            } catch (NumberFormatException e) {
                this.sendUsageMessage(sender);
            }
            return;
        }

        if (args.get(0).equalsIgnoreCase("clear")) {
            var placed = new ArrayList<EntityNPC>();
            for (var entity : scene.getEntities().values()) {
                if (entity instanceof EntityNPC npc && npc.isStandalone()) placed.add(npc);
            }
            placed.forEach(scene::removeEntity);
            CommandHandler.sendMessage(sender, "Cleared " + placed.size() + " manually placed NPC(s).");
            return;
        }

        int npcId;
        try {
            npcId = Integer.parseInt(args.get(0));
        } catch (NumberFormatException e) {
            this.sendUsageMessage(sender);
            return;
        }
        if (!GameData.getNpcDataMap().containsKey(npcId)) {
            CommandHandler.sendMessage(sender, "NPC " + npcId + " was not found.");
            return;
        }

        double yaw = Math.toRadians(targetPlayer.getRotation().getY());
        var pos =
                targetPlayer
                        .getPosition()
                        .clone()
                        .addX((float) (Math.sin(yaw) * DISTANCE))
                        .addZ((float) (Math.cos(yaw) * DISTANCE));
        var rot = new Position(0, (targetPlayer.getRotation().getY() + 180) % 360, 0);

        var npc = new EntityNPC(scene, npcId, pos, rot);
        for (var block : scene.getLoadedBlocks()) {
            if (block.contains(pos)) {
                npc.setBlockId(block.id);
                break;
            }
        }
        scene.addEntity(npc);
        Grasscutter.getLogger()
                .info(
                        "[npc] placed npc {} as entity {} in scene {} block {} at {}",
                        npcId,
                        npc.getId(),
                        scene.getId(),
                        npc.getBlockId(),
                        pos);
        CommandHandler.sendMessage(sender, "Placed NPC " + npcId + ".");
    }

    private void listNearby(Player sender, Player targetPlayer, List<String> args) {
        float radius = 15f;
        if (args.size() > 1) {
            try {
                radius = Float.parseFloat(args.get(1));
            } catch (NumberFormatException e) {
                this.sendUsageMessage(sender);
                return;
            }
        }

        var data = GameData.getSceneNpcBornData().get(targetPlayer.getSceneId());
        var here = targetPlayer.getPosition();
        var lines = new ArrayList<String>();
        if (data != null && data.getBornPosList() != null) {
            final float r = radius;
            data.getBornPosList().stream()
                    .filter(e -> e.getPos() != null && e.getPos().computeDistance(here) <= r)
                    .sorted(Comparator.comparingDouble(e -> e.getPos().computeDistance(here)))
                    .limit(20)
                    .forEach(
                            e ->
                                    lines.add(
                                            "%d  %.1fm  group %d"
                                                    .formatted(
                                                            e.getId(),
                                                            e.getPos().computeDistance(here),
                                                            e.getGroupId())));
        }

        if (lines.isEmpty()) {
            CommandHandler.sendMessage(sender, "No NPCs found within " + radius + "m.");
            return;
        }
        CommandHandler.sendMessage(sender, String.join("\n", lines));
    }
}
