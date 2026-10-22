package emu.grasscutter.game.managers.blossom;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BlossomGeoIndexTest {
    @Test
    void nativeLevelOnePolygonAttributesLandToCityAndRejectsOffshorePoints() {
        var root = JsonParser.parseString(
                "{\"level1Areas\":[{\"level1Area\":"
                + "{\"id1\":1,\"id2\":0,\"polygonData\":"
                + "{\"minArea\":{\"x\":0,\"z\":0},"
                + "\"maxArea\":{\"x\":10,\"z\":10},"
                + "\"roadPoints\":[{\"x\":0,\"z\":0},{\"x\":10,\"z\":0},"
                + "{\"x\":10,\"z\":10},{\"x\":0,\"z\":10}]}}}]}").getAsJsonObject();
        var index = BlossomGeoIndex.parse(root, Map.of(1, 1));
        assertEquals(1, index.cityId(4, 6));
        assertEquals(0, index.cityId(-1, 6));
        assertEquals(0, index.cityId(12, 6));
    }

    @Test
    void overlappingNativeCityClaimsHaveNoInventedOwnership() {
        var root = JsonParser.parseString(
                "{\"level1Areas\":["
                + "{\"level1Area\":{\"id1\":1,\"polygonData\":"
                + "{\"minArea\":{\"x\":0,\"z\":0},\"maxArea\":{\"x\":10,\"z\":10},"
                + "\"roadPoints\":[{\"x\":0,\"z\":0},{\"x\":10,\"z\":0},"
                + "{\"x\":10,\"z\":10},{\"x\":0,\"z\":10}]}}},"
                + "{\"level1Area\":{\"id1\":5,\"polygonData\":"
                + "{\"minArea\":{\"x\":0,\"z\":0},\"maxArea\":{\"x\":10,\"z\":10},"
                + "\"roadPoints\":[{\"x\":0,\"z\":0},{\"x\":10,\"z\":0},"
                + "{\"x\":10,\"z\":10},{\"x\":0,\"z\":10}]}}}]}").getAsJsonObject();
        var index = BlossomGeoIndex.parse(root, Map.of(1, 1, 5, 2));
        assertEquals(0, index.cityId(5, 5));
    }
}
