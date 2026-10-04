package emu.grasscutter.server.http.documentation;

import emu.grasscutter.server.http.Router;
import io.javalin.config.RoutesConfig;

public final class DocumentationServerHandler implements Router {

    @Override
    public void applyRoutes(RoutesConfig routes) {
        final var root = new RootRequestHandler();
        final var gachaMapping = new GachaMappingRequestHandler();

        routes.get("/documentation/handbook", ctx -> ctx.redirect("https://grasscutter.io/handbook"));
        routes.get("/documentation/gachamapping", gachaMapping::handle);
        routes.get("/documentation", root::handle);
    }
}
