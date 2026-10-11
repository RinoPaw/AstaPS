package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Narrow post-claim CmdId diagnostic for the 7.1 Fontaine weapon domain retry.
 * Never logs protobuf payloads, tokens, or other accounts' traffic.
 */
public final class DomainRetryOpcodeProbe {
    private static final long WINDOW_MS = 90_000;
    private static final int MAX_DISTINCT_OPCODES = 48;
    private static final ConcurrentHashMap<Integer, Probe> PROBES = new ConcurrentHashMap<>();

    private DomainRetryOpcodeProbe() {}

    public static void arm(Player player, DungeonManager manager) {
        if (player == null || manager == null || manager.getDungeonData() == null) return;
        int dungeonId = manager.getDungeonData().getId();
        if (dungeonId < 4470 || dungeonId > 4473) return;
        long now = System.currentTimeMillis();
        PROBES.put(player.getUid(),
                new Probe(dungeonId, manager.getScene().getId(), now + WINDOW_MS, now));
        Grasscutter.getLogger().info(
                "[DomainRetryProbe] armed uid={} dungeon={} scene={} window={}s",
                player.getUid(), dungeonId, manager.getScene().getId(), WINDOW_MS / 1000);
    }

    public static void observe(
            Player player, int opcode, int payloadBytes, boolean registered, String mappedName) {
        if (player == null || opcode <= 0 || opcode == PacketOpcodes.PingReq
                || PacketOpcodesUtils.LOOP_PACKETS.contains(opcode)) return;
        Probe probe = PROBES.get(player.getUid());
        if (probe == null) return;
        long now = System.currentTimeMillis();
        if (now > probe.expiresAt) {
            PROBES.remove(player.getUid(), probe);
            return;
        }
        if (probe.sceneId != player.getSceneId()) return;
        if (probe.observed.size() >= MAX_DISTINCT_OPCODES || !probe.observed.add(opcode)) return;
        Grasscutter.getLogger().info(
                "[DomainRetryProbe] uid={} dungeon={} opcode={} name={} registered={} len={} afterClaimMs={}",
                player.getUid(), probe.dungeonId, opcode,
                mappedName == null ? "unknown" : mappedName,
                registered, payloadBytes, now - probe.armedAt);
    }

    private static final class Probe {
        final int dungeonId;
        final int sceneId;
        final long expiresAt;
        final long armedAt;
        final Set<Integer> observed = ConcurrentHashMap.newKeySet();

        Probe(int dungeonId, int sceneId, long expiresAt, long armedAt) {
            this.dungeonId = dungeonId;
            this.sceneId = sceneId;
            this.expiresAt = expiresAt;
            this.armedAt = armedAt;
        }
    }
}
