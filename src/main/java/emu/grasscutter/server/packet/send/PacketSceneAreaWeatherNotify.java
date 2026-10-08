package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneAreaWeatherNotifyOuterClass.SceneAreaWeatherNotify;

public class PacketSceneAreaWeatherNotify extends BasePacket {

    public PacketSceneAreaWeatherNotify(Player player) {
        super(PacketOpcodes.SceneAreaWeatherNotify);

        // WeatherExcel's gadgetID identifies the weather volume controlled by
        // quest actions (for 35901: area 3 -> gadget 70020003). The client's
        // 7.1 SceneAreaWeatherNotify carries that gadget ID separately from the
        // weather area and climate values.
        var weatherData = emu.grasscutter.data.GameData
                .getWeatherDataMap().get(player.getWeatherId());
        var builder = SceneAreaWeatherNotify.newBuilder()
                .setWeatherAreaId(player.getWeatherId())
                .setClimateType(player.getClimate().getValue());
        if (weatherData != null && weatherData.getGadgetID() > 0) {
            builder.setWeatherGadgetId(weatherData.getGadgetID());
        }
        SceneAreaWeatherNotify proto = builder.build();

        this.setData(proto);
    }
}
