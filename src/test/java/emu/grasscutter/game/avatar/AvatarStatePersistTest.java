package emu.grasscutter.game.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class AvatarStatePersistTest {
    @Test
    void newlyCreatedAvatarKeepsFullHealthAfterItsFirstLevelChange() throws Exception {
        var avatar = newAvatar();
        assertEquals(500f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        avatar.setLevel(2);
        avatar.recalcStats();
        assertEquals(1000f, avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(1000f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void loadedNatlanHpDoesNotReplaceSpecialEnergy() throws Exception {
        var avatar = newAvatar();
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 42f);
        AvatarStatePersist.stash(avatar, 300f, 0f, 17f);
        avatar.recalcStats();
        assertEquals(300f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(17f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_NATLAN_HP));
        assertEquals(42f, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    private static Avatar newAvatar() throws Exception {
        var data = new Gson().fromJson(
                """
                {"id":10000002,"name":"Avatar_Test","hpBase":500,"hpGrowthCurve":[1,2],"fetters":[]}
                """,
                AvatarData.class);
        var depot = new AvatarSkillDepotData();
        setField(depot, "skills", List.of());
        setField(depot, "talents", List.of());
        setField(depot, "elementType", ElementType.None);
        setField(depot, "inherentProudSkillOpens", List.of());
        setField(depot, "questProudSkillGroupIds", new IntArrayList());
        setField(data, "skillDepot", depot);
        return new Avatar(data);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
