package emu.grasscutter.utils;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

public class UnsignedLongJsonTest {
    @Test
    public void readsSignedAndUnsignedBoundsExactly() {
        assertEquals(Long.MIN_VALUE, JsonUtils.gson.fromJson("-9223372036854775808", Long.class));
        assertEquals(Long.MAX_VALUE, JsonUtils.gson.fromJson("9223372036854775807", Long.class));
        assertEquals(Long.MIN_VALUE, JsonUtils.gson.fromJson("9223372036854775808", Long.class));
        assertEquals(-1L, JsonUtils.gson.fromJson("18446744073709551615", Long.class));
        assertEquals(-2L, JsonUtils.gson.fromJson("\"18446744073709551614\"", Long.class));
    }

    @Test
    public void acceptsIntegralDecimalAndExponentForms() {
        assertEquals(1L, JsonUtils.gson.fromJson("1.0", Long.class));
        assertEquals(1000L, JsonUtils.gson.fromJson("1e3", Long.class));
        assertEquals(-1L, JsonUtils.gson.fromJson("1.8446744073709551615e19", Long.class));
    }

    @Test
    public void rejectsFractionsOverflowAndMalformedValues() {
        for (String json : new String[] {"1.5", "18446744073709551616", "-9223372036854775809", "\"formula\"", "1e1000000000", "1e-1000000000"}) {
            assertThrows(JsonSyntaxException.class, () -> JsonUtils.gson.fromJson(json, Long.class));
        }
    }

    @Test
    public void keepsFormulaFallbackInLenientGson() {
        assertEquals(0L, JsonUtils.lenientGson.fromJson("\"SGV_damage\"", Long.class));
        assertEquals(0L, JsonUtils.lenientGson.fromJson("[\"ADD\",1]", Long.class));
        assertEquals(1L, JsonUtils.lenientGson.fromJson("true", Long.class));
    }
}
