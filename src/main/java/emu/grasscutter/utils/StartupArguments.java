package emu.grasscutter.utils;

import static emu.grasscutter.config.Configuration.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import emu.grasscutter.BuildConfig;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.tools.Dumpers;
import java.util.Locale;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.TypeConversionException;

/** Parses process start-up arguments before the server is initialized. */
public final class StartupArguments {
    private StartupArguments() {}

    /**
     * Parses and applies the provided start-up arguments.
     *
     * @param args The application start-up arguments.
     * @return If the application should exit before server initialization.
     */
    public static boolean parse(String[] args) {
        var options = new Options();
        var commandLine = createCommandLine(options);

        try {
            commandLine.parseArgs(args);
        } catch (ParameterException exception) {
            commandLine.getErr().println(exception.getMessage());
            commandLine.usage(commandLine.getErr());
            return true;
        }

        return options.apply();
    }

    static CommandLine createCommandLine(Options options) {
        return new CommandLine(options)
                .setOptionsCaseInsensitive(true)
                .setCaseInsensitiveEnumValuesAllowed(true);
    }

    @Command(name = "astaps", sortOptions = false)
    static final class Options {
        @Option(names = {"-version", "-v"})
        boolean version;

        @Option(names = "-dumppacketids")
        boolean dumpPacketIds;

        @Option(names = "-debug", arity = "0..1", fallbackValue = "BASIC", paramLabel = "[all]")
        DebugMode debugMode;

        @Option(names = "-debugall")
        boolean debugAll;

        @Option(names = "-lang", paramLabel = "<language>")
        String language;

        @ArgGroup(exclusive = true, multiplicity = "0..1")
        RunModeOptions runMode;

        @Option(names = "-noconsole")
        boolean noConsole;

        @Option(names = "-test")
        boolean test;

        @Option(
                names = "-dump",
                paramLabel = "<content>,<language>",
                converter = DumpRequestConverter.class)
        DumpRequest dumpRequest;

        boolean apply() {
            if (language != null) {
                Grasscutter.setPreferredLanguage(language);
            }

            if (runMode != null) {
                Grasscutter.setRunModeOverride(
                        runMode.game
                                ? Grasscutter.ServerRunMode.GAME_ONLY
                                : Grasscutter.ServerRunMode.DISPATCH_ONLY);
            }

            if (noConsole) {
                Grasscutter.setNoConsole(true);
                SERVER.game.enableConsole = false;
            }

            if (test) {
                SERVER.game.enableConsole = false;
                SERVER.http.encryption.useEncryption = false;
            }

            if (debugMode != null || debugAll) {
                enableDebug(debugAll || debugMode == DebugMode.ALL);
            }

            boolean exitEarly = false;
            if (dumpPacketIds) {
                PacketOpcodesUtils.dumpPacketIds();
                exitEarly = true;
            }
            if (version) {
                printVersion();
                exitEarly = true;
            }
            if (dumpRequest != null) {
                dump(dumpRequest);
                exitEarly = true;
            }
            return exitEarly;
        }
    }

    static final class RunModeOptions {
        @Option(names = "-game")
        boolean game;

        @Option(names = "-dispatch")
        boolean dispatch;
    }

    enum DebugMode {
        BASIC,
        ALL
    }

    enum DumpContent {
        COMMANDS,
        AVATARS,
        ITEMS,
        SCENES,
        ENTITIES,
        QUESTS,
        AREAS
    }

    record DumpRequest(DumpContent content, String language) {}

    static final class DumpRequestConverter implements ITypeConverter<DumpRequest> {
        @Override
        public DumpRequest convert(String value) {
            String[] parts = value.split(",", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new TypeConversionException(
                        "Expected -dump=<content>,<language>; content is commands, avatars, items, scenes, entities, quests, or areas.");
            }

            final DumpContent content;
            try {
                content = DumpContent.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new TypeConversionException(
                        "Unknown dump content '"
                                + parts[0]
                                + "'; expected commands, avatars, items, scenes, entities, quests, or areas.");
            }

            return new DumpRequest(content, parts[1].trim());
        }
    }

    private static void printVersion() {
        System.out.println("Grasscutter version: " + BuildConfig.VERSION + "-" + BuildConfig.GIT_HASH);
    }

    private static void enableDebug(boolean all) {
        if (all) {
            GAME_INFO.isShowLoopPackets = DEBUG_MODE_INFO.isShowLoopPackets;
            GAME_INFO.isShowPacketPayload = DEBUG_MODE_INFO.isShowPacketPayload;
            GAME_INFO.logPackets = DEBUG_MODE_INFO.logPackets;
            DISPATCH_INFO.logRequests = DEBUG_MODE_INFO.logRequests;

            Level loggerLevel = DEBUG_MODE_INFO.servicesLoggersLevel;
            ((Logger) LoggerFactory.getLogger("io.javalin")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.quartz")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.reflections")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.eclipse.jetty")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.mongodb.driver")).setLevel(loggerLevel);
        }

        Grasscutter.getLogger().setLevel(DEBUG_MODE_INFO.serverLoggerLevel);
        Grasscutter.getLogger().debug("The logger is now running in debug mode.");
        GameConstants.DEBUG = true;
    }

    private static void dump(DumpRequest request) {
        try {
            switch (request.content()) {
                case COMMANDS -> Dumpers.dumpCommands(request.language());
                case AVATARS -> Dumpers.dumpAvatars(request.language());
                case ITEMS -> Dumpers.dumpItems(request.language());
                case SCENES -> Dumpers.dumpScenes();
                case ENTITIES -> Dumpers.dumpEntities(request.language());
                case QUESTS -> Dumpers.dumpQuests(request.language());
                case AREAS -> Dumpers.dumpAreas(request.language());
            }

            Grasscutter.getLogger().info("Finished dumping.");
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Unable to complete dump.", exception);
        }
    }
}
