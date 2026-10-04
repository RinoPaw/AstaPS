package emu.grasscutter.server.http;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Handler;

/** Defines routes for the HTTP server. */
public interface Router {

    /**
     * Registers this router during Javalin configuration.
     *
     * @param routes Javalin's startup-time route registry.
     */
    void applyRoutes(RoutesConfig routes);

    /** Applies this handler to all endpoint types used by the game client. */
    default RoutesConfig allRoutes(RoutesConfig routes, String path, Handler handler) {
        routes.get(path, handler);
        routes.post(path, handler);
        routes.put(path, handler);
        routes.patch(path, handler);
        routes.delete(path, handler);

        return routes;
    }
}
