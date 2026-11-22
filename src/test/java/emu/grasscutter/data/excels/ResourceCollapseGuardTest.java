package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.ResourceLoader;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public final class ResourceCollapseGuardTest {
    @Test
    public void smallTablesWarnWithoutLosingExistingWarnings() throws Exception {
        Method method = ResourceLoader.class.getDeclaredMethod(
                "reportCollapse", Class.class, String.class, int.class, int.class);
        method.setAccessible(true);
        var logger = Grasscutter.getLogger();
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            assertWarn(method, events, 4, 1, 1);
            assertWarn(method, events, 2, 1, 1);
            for (int rows = 8; rows <= 15; rows++) {
                assertWarn(method, events, rows, 2, 1);
            }
            assertWarn(method, events, 25, 17, 0);
            assertWarn(method, events, 783, 782, 0);
            assertWarn(method, events, 2, 2, 0);
            assertWarn(method, events, 1, 1, 0);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    private static void assertWarn(
            Method method, ListAppender<ILoggingEvent> events,
            int rows, int kept, int expected) throws Exception {
        events.list.clear();
        method.invoke(null, BargainData.class, "probe.json", rows, kept);
        assertEquals(expected, events.list.size(), "rows=" + rows + " kept=" + kept);
    }
}