package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSceneAreaUnlockNotify;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "waypoints",
        aliases = {"wp", "unlockwp"},
        permission = "player.waypoints",
        permissionTargeted = "player.waypoints.others")
public final class WaypointsCommand implements CommandHandler {

    static boolean isWaypointType(String type) {
        return "SceneTransPoint".equals(type)
                || "TransPointNormal".equals(type)
                || "TransPoint".equals(type);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Unlock(sender, targetPlayer));
        commandLine.addSubcommand("list", new ListAreas(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "waypoints")
    private final class Unlock implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[areaId...]")
        private List<Integer> areaIds;

        private Unlock(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int sceneId = targetPlayer.getSceneId();
            var waypoints = collectWaypoints(sceneId);
            if (waypoints.isEmpty()) {
                CommandOutput.sendMessage(sender, "No waypoints are known for scene " + sceneId + ".");
                return;
            }

            Set<Integer> wanted = areaIds == null || areaIds.isEmpty() ? null : new HashSet<>(areaIds);
            var points = new ArrayList<Integer>();
            var areas = new HashSet<Integer>();
            for (var entry : waypoints.entrySet()) {
                if (wanted != null && !wanted.contains(entry.getValue())) {
                    continue;
                }
                points.add(entry.getKey());
                if (entry.getValue() != 0) {
                    areas.add(entry.getValue());
                }
            }

            if (points.isEmpty()) {
                CommandOutput.sendMessage(sender, "No waypoints matched those areas. Try /waypoints list");
                return;
            }

            var alreadyUnlocked = targetPlayer.getUnlockedScenePoints(sceneId);
            int fresh = (int) points.stream().filter(point -> !alreadyUnlocked.contains(point)).count();
            alreadyUnlocked.addAll(points);

            for (int area = 1; area < 1000; area++) {
                areas.add(area);
            }
            targetPlayer.getUnlockedSceneAreas(sceneId).addAll(areas);
            targetPlayer.save();

            targetPlayer.sendPacket(
                    new PacketSceneAreaUnlockNotify(
                            sceneId, targetPlayer.getUnlockedSceneAreas(sceneId)));
            targetPlayer.sendPacket(new PacketScenePointUnlockNotify(sceneId, alreadyUnlocked));

            CommandOutput.sendMessage(
                    sender,
                    "Unlocked "
                            + points.size()
                            + " waypoint(s) in scene "
                            + sceneId
                            + " ("
                            + fresh
                            + " new), and opened every area of the scene."
                            + " If a region is still walled off, try /tag unlockall - the barrier at a"
                            + " region boundary is usually a scene tag rather than an area lock.");
        }
    }

    @CommandLine.Command(name = "list")
    private final class ListAreas implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private ListAreas(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int sceneId = targetPlayer.getSceneId();
            var waypoints = collectWaypoints(sceneId);
            if (waypoints.isEmpty()) {
                CommandOutput.sendMessage(sender, "No waypoints are known for scene " + sceneId + ".");
                return;
            }

            var perArea = new TreeMap<Integer, int[]>();
            var unlocked = targetPlayer.getUnlockedScenePoints(sceneId);
            for (var entry : waypoints.entrySet()) {
                var counts = perArea.computeIfAbsent(entry.getValue(), ignored -> new int[2]);
                counts[0]++;
                if (unlocked.contains(entry.getKey())) {
                    counts[1]++;
                }
            }

            var message = new StringBuilder("Waypoints in scene " + sceneId + " by area (unlocked/total):");
            perArea.forEach(
                    (area, counts) ->
                            message.append("\n  area ")
                                    .append(area)
                                    .append(": ")
                                    .append(counts[1])
                                    .append("/")
                                    .append(counts[0]));
            CommandOutput.sendMessage(sender, message.toString());
        }
    }

    private static TreeMap<Integer, Integer> collectWaypoints(int sceneId) {
        var waypoints = new TreeMap<Integer, Integer>();
        var pointIds = GameData.getScenePointsPerScene().get(sceneId);
        if (pointIds == null) {
            return waypoints;
        }

        for (var pointId : pointIds) {
            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            if (entry == null || entry.getPointData() == null) {
                continue;
            }
            var data = entry.getPointData();
            if (data.isForbidSimpleUnlock() || !"TransPointNormal".equals(data.getType())) {
                continue;
            }
            waypoints.put(pointId, data.getAreaId());
        }
        return waypoints;
    }
}
