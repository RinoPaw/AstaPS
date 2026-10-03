package emu.grasscutter.data.binout;

import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SceneNpcBornData {
    int sceneId;

    @SerializedName(value = "bornPosList", alternate = {"GJLIHAJHKEO"})
    List<SceneNpcBornEntry> bornPosList;

    /** Spatial Index For NPC */
    transient RTree<SceneNpcBornEntry, Geometry> index;

    /** npc groups */
    transient Map<Integer, SceneGroup> groups = new ConcurrentHashMap<>();
}
