package emu.grasscutter.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class UnsignedLongJsonTest {
    @Test
    public void readsSignedAndUnsignedBoundsExactly() {
        assertEquals(Long.MIN_VALUE, JsonUtils.gson.fromJson("-9223372036854775808", Long.class));
        assertEquals(Long.MAX_VALUE, JsonUtils.gson.fromJson("9223372036854775807", Long.class));
        assertEquals(Long.MIN_VALUE, JsonUtils.gson.fromJson("9223372036854775808", Long.class));
        assertEquals(-1L, JsonUtils.gson.fromJson("18446744073709551615", Long.class));
    }

    @Test
    public void acceptsIntegralDecimalAndExponentForms() {
        assertEquals(1L, JsonUtils.gson.fromJson("1.0", Long.class));
        assertEquals(1000L, JsonUtils.gson.fromJson("1e3", Long.class));
    }
}
