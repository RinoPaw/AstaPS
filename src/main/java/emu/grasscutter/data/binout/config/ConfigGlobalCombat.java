package emu.grasscutter.data.binout.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

@Data
public class ConfigGlobalCombat {
    @SerializedName("EHNGNAOKIPB")
    private DefaultAbilities defaultAbilities;
    // TODO: Add more indices

    public boolean isDefaultAbilitiesMissing() {
        return this.defaultAbilities == null;
    }

    /** Never null: every entity's ability setup (the world entity first, during login) reads it. */
    public DefaultAbilities getDefaultAbilities() {
        if (this.defaultAbilities == null) this.defaultAbilities = new DefaultAbilities();
        return this.defaultAbilities;
    }

    @Data
    public static class DefaultAbilities {
        @SerializedName("CIPPBKBJJFH")
        private String monterEliteAbilityName;

        @SerializedName("LDHLMPMOPKL")
        private List<String> nonHumanoidMoveAbilities;

        @SerializedName("BJIFBDHAJMN")
        private List<String> levelDefaultAbilities;

        @SerializedName("NEEHFCBBAKF")
        private List<String> levelElementAbilities;

        @SerializedName("NFEJCNKAEEK")
        private List<String> levelItemAbilities;

        @SerializedName("GBDLFMJEKIC")
        private List<String> levelSBuffAbilities;

        @SerializedName("NHHFOOAIHIA")
        private List<String> defaultMPLevelAbilities;

        @SerializedName("PPLPBDDJMGM")
        private List<String> defaultAvatarAbilities;

        @SerializedName("GPNPNBPEDOB")
        private List<String> defaultTeamAbilities;

        public List<String> getNonHumanoidMoveAbilities() {
            return orEmpty(this.nonHumanoidMoveAbilities);
        }

        public List<String> getLevelDefaultAbilities() {
            return orEmpty(this.levelDefaultAbilities);
        }

        public List<String> getLevelElementAbilities() {
            return orEmpty(this.levelElementAbilities);
        }

        public List<String> getLevelItemAbilities() {
            return orEmpty(this.levelItemAbilities);
        }

        public List<String> getLevelSBuffAbilities() {
            return orEmpty(this.levelSBuffAbilities);
        }

        public List<String> getDefaultMPLevelAbilities() {
            return orEmpty(this.defaultMPLevelAbilities);
        }

        public List<String> getDefaultAvatarAbilities() {
            return orEmpty(this.defaultAvatarAbilities);
        }

        public List<String> getDefaultTeamAbilities() {
            return orEmpty(this.defaultTeamAbilities);
        }

        private static List<String> orEmpty(List<String> list) {
            return list != null ? list : List.of();
        }
    }
}
