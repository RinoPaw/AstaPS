package emu.grasscutter.server.http;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.utils.FileUtils;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.ContentType;
import io.javalin.json.JavalinGson;
import java.io.File;
import java.util.*;

public final class HttpServer {
    private final List<Router> routers = new ArrayList<>();
    private Javalin javalin;

    public HttpServer() {
        if (Grasscutter.getRunMode() == Grasscutter.ServerRunMode.GAME_ONLY) {
            return;
        }
    }

    private void configure(JavalinConfig config) {
        configureConnector(config);

        if (HTTP_POLICIES.cors.enabled) {
            var allowedOrigins = HTTP_POLICIES.cors.allowedOrigins;
            config.bundledPlugins.enableCors(
                    cors ->
                            cors.addRule(
                                    rule -> {
                                        if (allowedOrigins.length == 0
                                                || Arrays.asList(allowedOrigins).contains("*")) {
                                            rule.anyHost();
                                            return;
                                        }

                                        rule.allowHost(
                                                allowedOrigins[0],
                                                Arrays.copyOfRange(
                                                        allowedOrigins, 1, allowedOrigins.length));
                                    }));
        }

        if (DISPATCH_INFO.logRequests == ServerDebugMode.ALL) {
            config.bundledPlugins.enableDevLogging();
        }

        config.jsonMapper(new JavalinGson());

        RoutesConfig routes = config.routes;
        this.routers.forEach(router -> router.applyRoutes(routes));
        routes.exception(
                Exception.class,
                (exception, ctx) -> {
                    ctx.status(500)
                            .result(
                                    "Internal server error. %s"
                                            .formatted(exception.getMessage()));
                    Grasscutter.getLogger()
                            .debug("Exception thrown: " + exception.getMessage(), exception);
                });
    }

    private static void configureConnector(JavalinConfig config) {
        String bindAddress = HTTP_INFO.bindAddress;
        config.jetty.host = bindAddress == null || bindAddress.isBlank() ? null : bindAddress;
        config.jetty.port = HTTP_INFO.bindPort;

        if (!HTTP_ENCRYPTION.useEncryption) {
            return;
        }

        var keystoreFile = new File(HTTP_ENCRYPTION.keystore);
        if (!keystoreFile.isFile()) {
            HTTP_ENCRYPTION.useEncryption = false;
            HTTP_ENCRYPTION.useInRouting = false;
            Grasscutter.getLogger()
                    .warn(translate("messages.dispatch.keystore.no_keystore_error"));
            return;
        }

        var sslPlugin =
                new SslPlugin(
                        ssl -> {
                            ssl.host = config.jetty.host;
                            ssl.insecure = false;
                            ssl.secure = true;
                            ssl.securePort = HTTP_INFO.bindPort;
                            // Preserve the old server's HTTP/1.1 transport behaviour.
                            ssl.http2 = false;
                            ssl.keystoreFromPath(
                                    keystoreFile.getPath(), HTTP_ENCRYPTION.keystorePassword);
                        });
        config.registerPlugin(sslPlugin);
    }

    public Javalin getHandle() {
        return this.javalin;
    }

    @SuppressWarnings("UnusedReturnValue")
    public HttpServer addRouter(Router router) {
        if (this.javalin != null) {
            throw new IllegalStateException(
                    "HTTP routes must be registered before the server starts.");
        }

        this.routers.add(Objects.requireNonNull(router));
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public HttpServer addRouter(Class<? extends Router> router, Object... args) {
        if (this.javalin != null) {
            throw new IllegalStateException(
                    "HTTP routes must be registered before the server starts.");
        }

        var types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }

        try {
            var constructor = router.getDeclaredConstructor(types);
            return this.addRouter(constructor.newInstance(args));
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn(translate("messages.dispatch.router_error"), exception);
        }
        return this;
    }

    public void start() {
        if (this.javalin != null) {
            throw new IllegalStateException("HTTP server has already been started.");
        }

        this.javalin = Javalin.create(this::configure);
        this.javalin.start();

        Grasscutter.getLogger()
                .info(
                        translate(
                                "messages.dispatch.address_bind",
                                HTTP_INFO.accessAddress,
                                this.javalin.port()));
    }

    public static class DefaultRequestRouter implements Router {
        @Override
        public void applyRoutes(RoutesConfig routes) {
            routes.get(
                    "/",
                    ctx -> {
                        File file = new File(HTTP_STATIC_FILES.indexFile);
                        if (!file.exists()) {
                            ctx.contentType(ContentType.TEXT_HTML);
                            ctx.result(
                                    """
                                    <!DOCTYPE html>
                                    <html>
                                        <head>
                                            <meta charset="utf8">
                                        </head>
                                        <body>%s</body>
                                    </html>
                                    """
                                            .formatted(translate("messages.status.welcome")));
                        } else {
                            var filePath = file.getPath();
                            ContentType fromExtension =
                                    ContentType.contentTypeByExtension(
                                            filePath.substring(filePath.lastIndexOf(".") + 1));
                            ctx.contentType(
                                    fromExtension != null ? fromExtension : ContentType.TEXT_HTML);
                            ctx.result(FileUtils.read(filePath));
                        }
                    });
        }
    }

    public static class UnhandledRequestRouter implements Router {
        @Override
        public void applyRoutes(RoutesConfig routes) {
            routes.error(
                    404,
                    ctx -> {
                        ctx.status(200);
                        ctx.contentType(ContentType.APPLICATION_JSON);
                        ctx.result("{\"retcode\":0,\"message\":\"OK\",\"data\":{}}");
                    });
        }
    }
}
