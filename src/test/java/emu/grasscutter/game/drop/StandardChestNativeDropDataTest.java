package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StandardChestNativeDropDataTest {
    @Test
    void fourStandardGradesHaveDistinctNativeRoots() throws Exception {
        try (var input = Files.newBufferedReader(Path.of("data/ChestDrop.json"), StandardCharsets.UTF_8)) {
            // Runtime DataLoader prefers this editable data table over bundled defaults.
            var records = JsonParser.parseReader(input).getAsJsonArray();

            // These are 1-level source mappings, not guessed chest rewards.
            Map<String, Integer> expected = Map.of(
                    "战斗低级蒙德", 51010000,
                    "战斗中级蒙德", 51010500,
                    "战斗高级蒙德", 51010700,
                    "战斗超级蒙德", 51010900,
                    "解谜低级稻妻", 52030000,
                    "解谜中级稻妻", 52032000,
                    "解谜高级稻妻", 52033000,
                    "解谜超级稻妻", 52034000);

            for (var entry : expected.entrySet()) {
                int match = 0;
                for (var element : records) {
                    var data = element.getAsJsonObject();
                    if (entry.getKey().equals(data.get("index").getAsString())
                            && data.get("minLevel").getAsInt() == 1) {
                        assertEquals(entry.getValue().intValue(),
                                data.get("dropId").getAsInt(), entry.getKey());
                        assertEquals(1, data.get("dropCount").getAsInt());
                        match++;
                    }
                }
                assertEquals(1, match, entry.getKey());
            }
        }
    }
}
