package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.custom.TrialAvatarCustomData;
import emu.grasscutter.data.excels.trial.TrialAvatarData;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrialAvatarParamFallbackTest {
    @Test
    void unrelatedCustomTrialAvatarDoesNotShadowNativeAmber() {
        final int customId = 987654;
        var natives = GameData.getTrialAvatarDataMap();
        var customs = GameData.getTrialAvatarCustomData();
        var previousNative = natives.get(1);
        var previousCustom = customs.get(customId);
        var previousOverride = customs.get(1);
        try {
            var amber = new TrialAvatarData();
            amber.setTrialAvatarId(1);
            amber.setTrialAvatarParamList(List.of(10000021, 5));
            natives.put(1, amber);

            var unrelated = new TrialAvatarCustomData();
            unrelated.setTrialAvatarId(customId);
            unrelated.setTrialAvatarParamList(List.of("10000006;19"));
            customs.put(customId, unrelated);
            customs.remove(1);

            assertEquals(List.of(10000021, 5), new TeamManager().getTrialAvatarParam(1));

            var override = new TrialAvatarCustomData();
            override.setTrialAvatarId(1);
            override.setTrialAvatarParamList(List.of("10000006;19"));
            customs.put(1, override);
            assertEquals(List.of(10000006, 19), new TeamManager().getTrialAvatarParam(1));
        } finally {
            if (previousNative == null) natives.remove(1);
            else natives.put(1, previousNative);
            if (previousCustom == null) customs.remove(customId);
            else customs.put(customId, previousCustom);
            if (previousOverride == null) customs.remove(1);
            else customs.put(1, previousOverride);
        }
    }
}
