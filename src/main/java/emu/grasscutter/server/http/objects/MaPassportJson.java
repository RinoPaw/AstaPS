package emu.grasscutter.server.http.objects;

import com.google.gson.JsonNull;
import emu.grasscutter.utils.JsonUtils;

/** Encodes ma-passport responses without changing the global JSON mapper. */
public final class MaPassportJson {
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

        return JsonUtils.encode(object);
    }
}
