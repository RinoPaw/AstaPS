package emu.grasscutter.utils.objects;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.LoggingEvent;
import emu.grasscutter.Grasscutter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class JLineLogbackAppenderTest {
    @Test
    void logOutputWorksBeforeShellCreationAndWithoutAConsole() {
        assertNull(Grasscutter.getActiveConsoleReader());

        var context = new LoggerContext();
        var encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%msg%n");
        encoder.start();

        var output = new ByteArrayOutputStream();
        var appender = new JLineLogbackAppender();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.start();
        appender.setOutputStream(output);

        try {
            var event = new LoggingEvent(
                    getClass().getName(), context.getLogger(getClass()),
                    Level.INFO, "before-shell-log", null, null);
            appender.doAppend(event);
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("before-shell-log"));
            assertNull(Grasscutter.getActiveConsoleReader());
        } finally {
            appender.stop();
            encoder.stop();
            context.stop();
        }
    }
}
