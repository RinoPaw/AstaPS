package emu.grasscutter;

import static emu.grasscutter.config.Configuration.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.tools.Dumpers;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Parses startup options before triggering server initialization. */
@Command(
        name = "astaps",
        description = "AstaPS game server",
        mixinStandardHelpOptions = true,
        versionProvider = ServerLauncher.Version.class)
public final class ServerLauncher implements Callable<Integer> {
    @ArgGroup(exclusive = true, multiplicity = "0..1")
    ServerMode mode;

    static final class ServerMode {
        @Option(names = "--game", description = "Start only the game server.")
        boolean game;

        @Option(names = "--dispatch", description = "Start only the dispatch server.")
        boolean dispatch;
    }

    @Option(names = "--no-console", description = "Disable the local JLine shell.")
    boolean noConsole;

    @Option(names = "--test", description = "Disable the console and HTTP encryption for testing.")
    boolean test;

    @Option(names = "--lang", paramLabel = "<locale>", description = "Preferred language.")
    String language;

    enum Debug {
        STANDARD,
        ALL
    }

    @Option(
            names = "--debug",
            arity = "0..1",
            fallbackValue = "STANDARD",
            description = "Enable debug mode (optionally --debug=all).")
    Debug debug;

    @Option(
            names = "--dump",
            paramLabel = "<kind,language>",
            description = "Export commands, avatars, items, scenes, entities, quests or areas.")
    String dump;

    @Option(names = "--dump-packet-ids", description = "Export packet ID mappings and exit.")
    boolean dumpPacketIds;

    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"AstaPS " + BuildConfig.VERSION + " (" + BuildConfig.GIT_HASH + ")"};
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ServerLauncher())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        if (exitCode != 0) System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (dumpPacketIds && dump != null) {
            throw new CommandLine.ParameterException(
                    new CommandLine(this), "--dump and --dump-packet-ids are mutually exclusive");
        }
        if (dumpPacketIds) {
            PacketOpcodesUtils.dumpPacketIds();
            return 0;
        }

        String[] dumpArguments = null;
        if (dump != null) {
            dumpArguments = dump.split(",", -1);
            if (dumpArguments.length != 2
                    || dumpArguments[0].isBlank()
                    || dumpArguments[1].isBlank()
                    || !Set.of("commands", "avatars", "items", "scenes",
                                    "entities", "quests", "areas")
                            .contains(dumpArguments[0].toLowerCase(Locale.ROOT))) {
                throw new CommandLine.ParameterException(
                        new CommandLine(this),
                        "--dump requires <kind,language>; kind: commands, avatars, items, scenes, entities, quests, areas");
            }
        }

        configure();
        if (dumpArguments != null) {
            export(dumpArguments[0], dumpArguments[1]);
            return 0;
        }

        Grasscutter.startServer();
        return 0;
    }

    void configure() {
        if (mode != null) {
            Grasscutter.setRunModeOverride(
                    mode.game ? Grasscutter.ServerRunMode.GAME_ONLY
                              : Grasscutter.ServerRunMode.DISPATCH_ONLY);
        }
        Grasscutter.setNoConsole(noConsole || test);
        if (language != null) {
            Grasscutter.setPreferredLanguage(language);
            Grasscutter.loadLanguage();
        }
        if (test) SERVER.http.encryption.useEncryption = false;
        if (debug != null) enableDebug();
    }

    private void enableDebug() {
        if (debug == Debug.ALL) {
            GAME_INFO.isShowLoopPackets = DEBUG_MODE_INFO.isShowLoopPackets;
            GAME_INFO.isShowPacketPayload = DEBUG_MODE_INFO.isShowPacketPayload;
            GAME_INFO.logPackets = DEBUG_MODE_INFO.logPackets;
            DISPATCH_INFO.logRequests = DEBUG_MODE_INFO.logRequests;

            Level level = DEBUG_MODE_INFO.servicesLoggersLevel;
            for (String name : new String[] {
                    "io.javalin", "org.quartz", "org.reflections",
                    "org.eclipse.jetty", "org.mongodb.driver"
            }) {
                ((Logger) LoggerFactory.getLogger(name)).setLevel(level);
            }
        }
        Grasscutter.getLogger().setLevel(DEBUG_MODE_INFO.serverLoggerLevel);
        Grasscutter.getLogger().debug("The logger is now running in debug mode.");
        GameConstants.DEBUG = true;
    }

    private static void export(String kind, String language) {
        switch (kind.toLowerCase(Locale.ROOT)) {
            case "commands" -> Dumpers.dumpCommands(language);
            case "avatars" -> Dumpers.dumpAvatars(language);
            case "items" -> Dumpers.dumpItems(language);
            case "scenes" -> Dumpers.dumpScenes();
            case "entities" -> Dumpers.dumpEntities(language);
            case "quests" -> Dumpers.dumpQuests(language);
            case "areas" -> Dumpers.dumpAreas(language);
            default -> throw new IllegalArgumentException("Unknown dump kind: " + kind);
        }
        Grasscutter.getLogger().info("Finished dumping.");
    }
}