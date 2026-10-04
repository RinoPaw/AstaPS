package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins how 7.1 burst rows spell their special-energy bar and cost fields. */
public final class SpecialEnergySkillTest {
    @Test
    @DisplayName("Mavuika's 7.1 burst row gives a 200 bar with a 100 cost")
    public void currentSpecialEnergyFieldsLoad() {
        var skill =
                JsonUtils.decode(
                        "{\"id\":11065,\"costElemType\":\"Fire\",\"specialEnergyCostStart\":100,"
                                + "\"specialEnergyCostType\":\"SPECIAL_ENERGY_MAVUIKA\",\"specialEnergyCostMax\":200}",
                        AvatarSkillData.class);
        assertEquals(200f, skill.getSpecialEnergyMin());
        assertEquals(100f, skill.getSpecialEnergyMax());
    }
}
