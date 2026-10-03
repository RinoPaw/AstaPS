package emu.grasscutter.game.talk;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import emu.grasscutter.data.excels.TalkConfigData;
import emu.grasscutter.game.entity.EntityNPC;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TalkNpcIdentityTest {
    private static EntityNPC npc(int npcId, int configId) {
        // Only identity fields are needed. Avoid constructing a world/server for a scene placement.
        return new GsonBuilder()
                .setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes field) {
                        return !Set.of("npcId", "configId", "id").contains(field.getName());
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> type) {
                        return false;
                    }
                })
                .create()
                .fromJson("{\"npcId\":%d,\"configId\":%d}".formatted(npcId, configId), EntityNPC.class);
    }

    private static TalkConfigData paimonTalk() {
        var talk = new TalkConfigData();
        talk.setId(35216);
        talk.setNpcId(List.of(1005));
        return talk;
    }

    @Test
    void paimonTalkAcceptsHerNpcIdEvenWhenThePlacementConfigIdDiffers() {
        assertTrue(TalkManager.isTalkNpc(paimonTalk(), npc(1005, 472)));
        assertTrue(TalkManager.isTalkNpc(paimonTalk(), npc(1005, 0)));
    }

    @Test
    void anotherNpcCannotPassByHavingPaimonsIdAsItsPlacementConfigId() {
        assertFalse(TalkManager.isTalkNpc(paimonTalk(), npc(1201, 1005)));
    }

    @Test
    void clientLocalQuestActorsStillReportTalkCompletionWithoutAServerEntity() {
        assertTrue(TalkManager.isTalkNpc(paimonTalk(), null));
    }
}
