package emu.grasscutter.server.http.objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import emu.grasscutter.utils.JsonUtils;

/** Encodes ma-passport responses without changing the global JSON mapper. */
public final class MaPassportJson {
    private static final Gson PROTOCOL_GSON =
            new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private MaPassportJson() {}

    public static String encode(Object response) {
        var json = JsonUtils.toJson(response);
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Ma-passport response must be a JSON object");
        }

        var object = json.getAsJsonObject();
        if (!object.has("data")) {
            object.add("data", JsonNull.INSTANCE);
        }

        // JsonUtils intentionally omits ordinary null fields. Serialize the already-built tree with
        // null support so the protocol-required top-level data:null survives the final write.
        return PROTOCOL_GSON.toJson(object);
    }
}
