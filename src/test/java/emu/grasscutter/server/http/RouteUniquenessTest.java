package emu.grasscutter.server.http;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when two routers register the same method and path.
 *
 * <p>Javalin throws on the second registration, and that throw escapes addRouter and aborts the
 * whole router setup - so one duplicate route does not disable itself, it takes every route
 * registered after it down with it. The server still starts, and the only sign is one warning
 * buried in the log.
 *
 * <p>This reads the sources rather than starting Javalin, so it needs no server and no database.
 */
public final class RouteUniquenessTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/emu/grasscutter");
    private static final List<String> ALL_ROUTE_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /** Matches Javalin 7 RoutesConfig calls such as routes.get("/path", ...). */
    private static final Pattern ROUTE =
            Pattern.compile(
                    "\\broutes\\s*\\.\\s*(get|post|put|patch|delete)\\s*\\(\\s*\"([^\"]+)\"");

    /** Matches AstaPS's helper, which registers the same path for every game-client HTTP verb. */
    private static final Pattern ALL_ROUTES =
            Pattern.compile("\\ballRoutes\\s*\\(\\s*routes\\s*,\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("route scan finds registrations and none are duplicated")
    public void routesAreUnique() throws IOException {
        Map<String, List<String>> registrations = collectRoutes();
        int found = registrations.values().stream().mapToInt(List::size).sum();
        assertTrue(found > 20, "only found " + found + " routes; the pattern has probably rotted");

        var duplicates = new ArrayList<String>();
        registrations.forEach(
                (route, files) -> {
                    if (files.size() > 1) duplicates.add(route + " <- " + files);
                });

        assertTrue(
                duplicates.isEmpty(),
                "These routes are registered more than once, which aborts router setup:\n  "
                        + String.join("\n  ", duplicates));
    }

    private static Map<String, List<String>> collectRoutes() throws IOException {
        Map<String, List<String>> registrations = new LinkedHashMap<>();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                String fileName = file.getFileName().toString();

                var routeMatcher = ROUTE.matcher(source);
                while (routeMatcher.find()) {
                    addRoute(
                            registrations,
                            routeMatcher.group(1).toUpperCase(Locale.ROOT),
                            routeMatcher.group(2),
                            fileName);
                }

                var allRoutesMatcher = ALL_ROUTES.matcher(source);
                while (allRoutesMatcher.find()) {
                    String path = allRoutesMatcher.group(1);
                    for (String method : ALL_ROUTE_METHODS) {
                        addRoute(registrations, method, path, fileName);
                    }
                }
            }
        }

        return registrations;
    }

    private static void addRoute(
            Map<String, List<String>> registrations, String method, String path, String fileName) {
        registrations
                .computeIfAbsent(method + " " + path, ignored -> new ArrayList<>())
                .add(fileName);
    }
}
