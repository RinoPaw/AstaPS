package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/**
 * Controls WeatherExcel gadget activation for quest-driven weather.
 *
 * <p>The first quest parameter is a WeatherExcel area ID (not a scene ID).
 * The second is the activation flag. SceneAreaWeatherNotify carries the
 * matching WeatherExcel gadget ID to the 7.1 client.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_SET_WEATHER_GADGET)
public final class ExecSetWeatherGadget extends QuestExecHandler {
    static int nextArea(int activeArea, int questArea, boolean enabled) {
        return enabled ? questArea : (activeArea == questArea ? 0 : activeArea);
    }

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... args) {
        if (args.length < 2) {
            Grasscutter.getLogger().warn(
                    "[quest-weather] main={} sub={} expected area and 0/1 activation",
                    quest.getMainQuestId(), quest.getSubQuestId());
            return false;
        }

        final int areaId;
        final int activation;
        try {
            areaId = Integer.parseInt(args[0]);
            activation = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            Grasscutter.getLogger().warn(
                    "[quest-weather] malformed weather gadget params for sub={}: {}",
                    quest.getSubQuestId(), java.util.Arrays.toString(args));
            return false;
        }
        if (activation != 0 && activation != 1) return false;

        var data = GameData.getWeatherDataMap().get(areaId);
        if (data == null || data.getGadgetID() <= 0) {
            Grasscutter.getLogger().warn(
                    "[quest-weather] unknown weather area/gadget {} in sub={}",
                    areaId, quest.getSubQuestId());
            return false;
        }

        var player = quest.getOwner();
        // Weather-area IDs and scene IDs differ for some quests (e.g. 2150
        // belongs to scene 4), so validate the weather data's owning scene.
        if (player.getSceneId() != data.getSceneID()) {
            Grasscutter.getLogger().debug(
                    "[quest-weather] deferring area {} for scene {} while player is in scene {}",
                    areaId, data.getSceneID(), player.getSceneId());
            return false;
        }

        int current = player.getWeatherId();
        int next = nextArea(current, areaId, activation == 1);
        if (next == current) return true;

        if (next == 0) {
            player.setWeather(0, ClimateType.CLIMATE_SUNNY);
        } else {
            player.setWeather(next);
        }
        Grasscutter.getLogger().debug(
                "[quest-weather] main={} sub={} weatherArea={} gadget={} activated={} current={}",
                quest.getMainQuestId(), quest.getSubQuestId(),
                areaId, data.getGadgetID(), activation == 1, next);
        return true;
    }
}
