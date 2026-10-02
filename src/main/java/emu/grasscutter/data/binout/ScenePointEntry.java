package emu.grasscutter.data.binout;

import emu.grasscutter.data.common.PointData;
import lombok.Getter;

public class ScenePointEntry {
    @Getter private final int sceneId;
    @Getter private final PointData pointData;

    public ScenePointEntry(int sceneId, PointData pointData) {
        this.sceneId = sceneId;
        this.pointData = pointData;
    }

    public String getName() {
        return this.sceneId + "_" + this.pointData.getId();
    }
}
