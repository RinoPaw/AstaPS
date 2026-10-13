package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

final class ChestRewardConfigTest {
    private static Map<Integer, Integer> items(List<ItemParamData> reward) {
        return reward.stream().collect(Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    private static String rules() throws Exception {
        try (var resource = RewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(resource);
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void standardFiveKindsHaveOnlyDirectRewards() throws Exception {
        var chest = RewardConfig.parse(rules()).chest();
        assertEquals(Map.of(102, 30, 201, 2),
                items(chest.resolve(ChestRewardConfig.Kind.COMMON)));
        assertEquals(Map.of(102, 30, 201, 5),
                items(chest.resolve(ChestRewardConfig.Kind.EXQUISITE)));
        assertEquals(Map.of(102, 30, 201, 10),
                items(chest.resolve(ChestRewardConfig.Kind.PRECIOUS)));
        assertEquals(Map.of(102, 30, 201, 10),
                items(chest.resolve(ChestRewardConfig.Kind.LUXURIOUS)));
        assertEquals(Map.of(102, 30, 201, 5),
                items(chest.resolve(ChestRewardConfig.Kind.REMARKABLE)));
    }

    @Test
    void noDropIdTagOrSourceFieldIsAccepted() throws Exception {
        var input = rules();
        for (String extra : List.of("source = \"native\"", "dropId = 52010000",
                "drop_tag = \"combat\"", "dropCount = 2", "kind = \"common\"")) {
            String invalid = input.replace("[chest.\"kind=common\"]",
                    "[chest.\"kind=common\"]\n" + extra);
            assertThrows(IllegalArgumentException.class, () -> RewardConfig.parse(invalid), extra);
        }
    }

    @Test
    void cannotOmitOrInventKinds() throws Exception {
        String input = rules();
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(input.replace("kind=remarkable", "kind=rare")));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(input.replace("kind=remarkable", "kind=common")));
    }

    @Test
    void validatesAmountsAndIndividualItems() throws Exception {
        String template = rules();
        var parsed = Toml.parse(template);
        assertFalse(parsed.hasErrors());
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(template.replace("adventureExp = 30", "adventureExp = -1")));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(template.replace("primogems = 2", "primogems = 2.5")));
        var withItem = template.replace("[chest.\"kind=common\"]",
                "[chest.\"kind=common\"]\nitems = [{ id = 104001, count = 2 }]");
        assertEquals(Map.of(102, 30, 201, 2, 104001, 2),
                items(RewardConfig.parse(withItem).chest().resolve(ChestRewardConfig.Kind.COMMON)));
    }
}
