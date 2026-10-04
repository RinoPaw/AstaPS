package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.*;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.stream.IntStream;

@Command(
        label = "unlockall",
        usage = {""},
        permission = "player.unlockall",
        permissionTargeted = "player.unlockall.others")
public final class UnlockAllCommand implements CommandHandler {

    private static final List<Integer> SCENE_AREAS = IntStream.range(1, 1000).boxed().toList();

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {

        for (var state : GameData.getOpenStateList()) {
            if (PlayerProgressManager.BLACKLIST_OPEN_STATES.contains(state.getId())) continue;
            if (targetPlayer.getProgressManager().getOpenState(state.getId()) == 0) {
                targetPlayer.getOpenStates().put(state.getId(), 1);
            }
        }
        // the whole map at once - a change notify pops a window per state, ~690 of them
        targetPlayer.sendPacket(new PacketOpenStateUpdateNotify(targetPlayer));

        GameData.getScenePointsPerScene().forEach((sceneId, scenePoints) -> {
            var points = new ArrayList<Integer>();
            for (var pointId : scenePoints) {
                var entry = GameData.getScenePointEntryById(sceneId, pointId);
                if (entry == null) continue;
                var data = entry.getPointData();
                if (data.isForbidSimpleUnlock()) continue;
                if (data.getType() != null && data.getType().equals("SceneBuildingPoint") && !data.isUnlocked()) continue;
                points.add(pointId);
            }
            targetPlayer.getUnlockedScenePoints(sceneId).addAll(points);
            targetPlayer.getUnlockedSceneAreas(sceneId).addAll(SCENE_AREAS);
        });

        int curScene = targetPlayer.getSceneId();
        targetPlayer.sendPacket(new PacketScenePointUnlockNotify(
                curScene, targetPlayer.getUnlockedScenePoints(curScene)));
        targetPlayer.sendPacket(new PacketSceneAreaUnlockNotify(
                curScene, targetPlayer.getUnlockedSceneAreas(curScene)));

        // straight onto the lists: addFlycloak/addTraceEffect pop a window each, and
        // addTraceEffect saves the whole player every time
        GameData.getAvatarFlycloakDataMap()
                .keySet()
                .forEach(targetPlayer.getFlyCloakList()::add);
        GameData.getAvatarTraceEffectDataMap()
                .keySet()
                .forEach(targetPlayer.getTraceEffectList()::add);

        var fetterEntries = GameData.getFetterDataEntries();
        for (var avatar : targetPlayer.getAvatars().getAvatars().values()) {
            var dataFetters = fetterEntries.get(avatar.getAvatarId());
            if (dataFetters == null) continue;
            List<Integer> current = avatar.getFetterList();
            if (current == null) {
                avatar.setFetterList(new ArrayList<>(dataFetters));
            } else {
                for (int fetterId : dataFetters) {
                    if (!current.contains(fetterId)) current.add(fetterId);
                }
            }
            avatar.save();
        }
        // carries the owned flycloak, costume and trace effect lists, so one refresh covers them
        targetPlayer.sendPacket(new PacketAvatarDataNotify(targetPlayer));

        // Scene tags are intentionally left unchanged.
        //
        // Many tags represent mutually exclusive quest/activity world states. Enabling all of
        // them simultaneously can make the client load incompatible terrain variants and produce
        // missing ground/collision. Use /tag reset or /tag add <id> explicitly instead.

        // [removed 2026-10-01 on request] 原来这里会把 jar 内 main_quest_ids.txt 的 4372 条
        // 主线/传说任务 id 全部写进 forcedFinishedQuests，并在客户端标记为“已完成”，
        // 导致传说任务从列表里消失。用户要求踢掉，故不再执行。
        // 代价：靠任务门禁解锁的新区域可能打不开（原逻辑靠伪造 ParentQuest 完成通知绕过）。
        // var quests = emu.grasscutter.game.quest.ForcedQuests.allMainQuests();
        // emu.grasscutter.game.quest.ForcedQuests.apply(targetPlayer, quests);

        targetPlayer.save();

        CommandHandler.sendMessage(
                sender, translate(sender, "commands.unlockall.success", targetPlayer.getNickname()));
        CommandHandler.sendMessage(
                sender,
                "Scene tags left unchanged.");
    }
}
