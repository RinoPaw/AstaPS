package emu.grasscutter.plugin.api;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.auth.AuthenticationSystem;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.PermissionHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.HttpServer;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Hooks into the {@link GameServer} class, adding convenient ways to do certain things. */
public final class ServerHelper {
    private static ServerHelper instance;
    private final GameServer gameServer;
    private final HttpServer httpServer;

    public ServerHelper(GameServer gameServer, HttpServer httpServer) {
        this.gameServer = gameServer;
        this.httpServer = httpServer;
        instance = this;
    }

    public static ServerHelper getInstance() {
        return instance;
    }

    public Grasscutter.ServerRunMode getRunMode() {
        return Grasscutter.getRunMode();
    }

    public GameServer getGameServer() {
        return this.gameServer;
    }

    public HttpServer getHttpServer() {
        return this.httpServer;
    }

    public List<Player> getOnlinePlayers() {
        return new ArrayList<>(this.gameServer.getPlayers().values());
    }

    public Stream<Player> getOnlinePlayersStream() {
        return this.gameServer.getPlayers().values().stream();
    }

    /** Register a picocli-backed command. */
    public void registerCommand(PicocliCommandHandler handler) {
        Class<?> clazz = handler.getClass();
        if (!clazz.isAnnotationPresent(Command.class)) {
            throw new IllegalArgumentException("Command handler must be annotated with @Command.");
        }
        Command commandData = clazz.getAnnotation(Command.class);
        CommandMap.getInstance().registerCommand(commandData.label(), handler);
    }

    public void addRouter(Router router) {
        this.addRouter(router.getClass());
    }

    public void addRouter(Class<? extends Router> router) {
        this.httpServer.addRouter(router);
    }

    public void setAuthSystem(AuthenticationSystem authSystem) {
        Grasscutter.setAuthenticationSystem(authSystem);
    }

    public void setPermissionHandler(PermissionHandler permHandler) {
        Grasscutter.setPermissionHandler(permHandler);
    }

    public ServerTaskScheduler getScheduler() {
        if (this.getGameServer() == null) return null;
        return this.gameServer.getScheduler();
    }
}
