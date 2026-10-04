package emu.grasscutter.server.http.handlers;

import emu.grasscutter.server.http.Router;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

/** Handles logging requests made to the server. */
public final class LogHandler implements Router {
    private static void log(Context ctx) {
        // TODO: Figure out how to dump request body and log to file.
        ctx.result("{\"code\":0}");
    }

    @Override
    public void applyRoutes(RoutesConfig routes) {
        // overseauspider.yuanshen.com
        routes.post("/log", LogHandler::log);
        // log-upload-os.mihoyo.com
        routes.post("/crash/dataUpload", LogHandler::log);
    }
}
