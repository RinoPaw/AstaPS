package emu.grasscutter.server.http.api;

import static emu.grasscutter.config.Configuration.GAME;
import static emu.grasscutter.server.http.api.ApiHandler.ERROR_RET_CODE;
import static emu.grasscutter.server.http.api.ApiHandler.SUCCESS_RET_CODE;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.threading.ServerHealthSnapshot;
import emu.grasscutter.server.threading.ServerRuntimeSnapshot;
import emu.grasscutter.server.threading.ThreadPoolManager;
import emu.grasscutter.server.threading.ThreadPoolSnapshot;
import emu.grasscutter.utils.JsonUtils;
import io.javalin.http.Context;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the same figures the periodic status log prints.
 *
 * <p>Built on {@link ServerRuntimeSnapshot} rather than a monitoring class of its own, so the log
 * and this endpoint cannot drift apart and report different numbers for the same server.
 */
public final class ServerStatusHandler {
    private ServerStatusHandler() {}

    /** Player count from this process. A dispatch-only node has no game server and reports 0. */
    private static int onlinePlayers() {
        var gameServer = Grasscutter.getGameServer();
        return gameServer == null ? 0 : gameServer.getPlayers().size();
    }

    public static void serverStatus(Context ctx) {
        ctx.contentType("application/json; charset=UTF-8");
        try {
            var runtime = ServerRuntimeSnapshot.collect();
            var pools =
                    ThreadPoolManager.getInstance().getAll().stream()
                            .map(ThreadPoolManager.getInstance()::snapshot)
                            .sorted(Comparator.comparing(ThreadPoolSnapshot::name))
                            .toList();
            var health = ServerHealthSnapshot.from(runtime, pools);

            var game = new LinkedHashMap<String, Object>();
            game.put("players", onlinePlayers());
            game.put("maxPlayers", GAME.maxOnlinePlayers);
            game.put("gameVersion", GameConstants.VERSION);
            game.put("uptime", runtime.uptimeText());
            game.put("startedAt", runtime.startedAtText());

            var jvm = new LinkedHashMap<String, Object>();
            jvm.put("processCpuLoad", runtime.processCpuLoad());
            jvm.put("usedMemoryBytes", runtime.usedJvmMemory());
            jvm.put("maxMemoryBytes", runtime.maxJvmMemory());
            jvm.put("memoryUsage", health.jvmMemoryUsage());
            jvm.put("gcCount", runtime.gcCount());
            jvm.put("gcTimeMillis", runtime.gcTimeMillis());

            var system = new LinkedHashMap<String, Object>();
            system.put("arch", System.getProperty("os.arch"));
            system.put("name", System.getProperty("os.name"));
            system.put("systemCpuLoad", runtime.systemCpuLoad());
            system.put("totalMemoryBytes", runtime.totalSystemMemory());
            system.put("freeMemoryBytes", runtime.freeSystemMemory());
            system.put("memoryUsage", health.systemMemoryUsage());

            var response = new LinkedHashMap<String, Object>();
            response.put("retcode", SUCCESS_RET_CODE);
            response.put("health", health.health().name());
            response.put("bottleneck", health.bottleneck());
            response.put("diagnosis", health.diagnosisText());
            response.put("suggestion", health.suggestion());
            response.put("game", game);
            response.put("jvm", jvm);
            response.put("system", system);
            response.put("threadPools", poolsJson(pools));

            ctx.result(JsonUtils.encode(response));
        } catch (Throwable t) {
            // Never let a monitoring endpoint take a thread down with it.
            Grasscutter.getLogger().warn("Failed to build the status response.", t);
            ctx.result("{\"retcode\":" + ERROR_RET_CODE + ",\"message\":\"internal error\"}");
        }
    }

    private static List<Map<String, Object>> poolsJson(List<ThreadPoolSnapshot> pools) {
        return pools.stream()
                .map(
                        pool -> {
                            var entry = new LinkedHashMap<String, Object>();
                            entry.put("name", pool.name());
                            entry.put("type", pool.type().name());
                            entry.put("health", pool.health().name());
                            entry.put("state", pool.lifecycleState());
                            entry.put("activeThreads", pool.activeCount());
                            entry.put("poolSize", pool.currentPoolSize());
                            entry.put("maxThreads", pool.maximumPoolSize());
                            entry.put("queueSize", pool.queueSize());
                            // -1 means unbounded.
                            entry.put("queueCapacity", pool.queueCapacity());
                            entry.put("submitted", pool.submittedTaskCount());
                            entry.put("completed", pool.completedTaskCount());
                            entry.put("failed", pool.failedTaskCount());
                            entry.put("rejected", pool.rejectedTaskCount());
                            entry.put("averageMillis", pool.averageExecutionMillis());
                            entry.put("maxMillis", pool.maxExecutionMillis());
                            entry.put("diagnosis", pool.diagnosisText());
                            return (Map<String, Object>) entry;
                        })
                .toList();
    }

    public static void listRoutes(Context ctx) {
        ctx.contentType("text/plain; charset=UTF-8");
        ctx.result(
                """
                /api/help
                /api/status
                /status/server
                """);
    }
}
