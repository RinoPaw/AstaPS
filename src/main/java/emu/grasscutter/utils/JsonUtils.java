package emu.grasscutter.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.world.*;
import emu.grasscutter.utils.JsonAdapters.*;
import emu.grasscutter.utils.objects.JObject;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class JsonUtils {
    static final Gson gson =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(long.class, new UnsignedLongAdapter())
                    .registerTypeAdapter(Long.class, new UnsignedLongAdapter())
                    .registerTypeAdapter(DynamicFloat.class, new DynamicFloatAdapter())
                    .registerTypeAdapter(IntList.class, new IntListAdapter())
                    .registerTypeAdapter(Position.class, new PositionAdapter())
                    .registerTypeAdapter(GridPosition.class, new GridPositionAdapter())
                    .registerTypeAdapter(byte[].class, new ByteArrayAdapter())
                    .registerTypeAdapter(JObject.class, new JObject.Adapter())
                    .registerTypeAdapterFactory(new EnumTypeAdapterFactory())
                    .disableHtmlEscaping()
                    .create();

    static final Gson lenientGson =
            gson.newBuilder()
                    .registerTypeAdapter(int.class, LenientNumberAdapter.INT)
                    .registerTypeAdapter(Integer.class, LenientNumberAdapter.INT)
                    .registerTypeAdapter(long.class, LenientNumberAdapter.LONG)
                    .registerTypeAdapter(Long.class, LenientNumberAdapter.LONG)
                    .registerTypeAdapter(float.class, LenientNumberAdapter.FLOAT)
                    .registerTypeAdapter(Float.class, LenientNumberAdapter.FLOAT)
                    .registerTypeAdapter(double.class, LenientNumberAdapter.DOUBLE)
                    .registerTypeAdapter(Double.class, LenientNumberAdapter.DOUBLE)
                    .create();

    public static <T> List<T> loadToListLenient(Path filename, Class<T> classType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            var listType = TypeToken.getParameterized(List.class, classType).getType();
            var json = JsonParser.parseReader(fileReader);
            return lenientGson.fromJson(json, listType);
        }
    }

    public static JsonElement toJson(Object object) {
        return gson.toJsonTree(object);
    }

    public static String encode(Object object) {
        return gson.toJson(object);
    }

    public static <T> T decode(JsonElement jsonElement, Class<T> classType)
            throws JsonSyntaxException {
        return gson.fromJson(jsonElement, classType);
    }

    public static <T> T loadToClass(Reader fileReader, Class<T> classType) throws IOException {
        return gson.fromJson(fileReader, classType);
    }

    @Deprecated(forRemoval = true)
    public static <T> T loadToClass(String filename, Class<T> classType) throws IOException {
        try (InputStreamReader fileReader =
                new InputStreamReader(
                        new FileInputStream(Utils.toFilePath(filename)), StandardCharsets.UTF_8)) {
            return loadToClass(fileReader, classType);
        }
    }

    public static <T> T loadToClass(Path filename, Class<T> classType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return loadToClass(fileReader, classType);
        }
    }

    public static <T> List<T> loadToList(Reader fileReader, Class<T> classType) throws IOException {
        return gson.fromJson(fileReader, TypeToken.getParameterized(List.class, classType).getType());
    }

    @Deprecated(forRemoval = true)
    public static <T> List<T> loadToList(String filename, Class<T> classType) throws IOException {
        try (InputStreamReader fileReader =
                new InputStreamReader(
                        new FileInputStream(Utils.toFilePath(filename)), StandardCharsets.UTF_8)) {
            return loadToList(fileReader, classType);
        }
    }

    public static <T> List<T> loadToList(Path filename, Class<T> classType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return loadToList(fileReader, classType);
        }
    }

    public static <T1, T2> Map<T1, T2> loadToMap(
            Reader fileReader, Class<T1> keyType, Class<T2> valueType) throws IOException {
        return gson.fromJson(
                fileReader, TypeToken.getParameterized(Map.class, keyType, valueType).getType());
    }

    @Deprecated(forRemoval = true)
    public static <T1, T2> Map<T1, T2> loadToMap(
            String filename, Class<T1> keyType, Class<T2> valueType) throws IOException {
        try (InputStreamReader fileReader =
                new InputStreamReader(
                        new FileInputStream(Utils.toFilePath(filename)), StandardCharsets.UTF_8)) {
            return loadToMap(fileReader, keyType, valueType);
        }
    }

    public static <T1, T2> Map<T1, T2> loadToMap(
            Path filename, Class<T1> keyType, Class<T2> valueType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return loadToMap(fileReader, keyType, valueType);
        }
    }

    public static <T1, T2> Map<T1, T2> loadToMap(Path filename, Class<T1> keyType, Type valueType)
            throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return gson.fromJson(
                    fileReader, TypeToken.getParameterized(Map.class, keyType, valueType).getType());
        }
    }

    public static <T> T decode(String jsonData, Class<T> classType) {
        try {
            return gson.fromJson(jsonData, classType);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static <T> T decode(String jsonData, Type type) {
        try {
            return gson.fromJson(jsonData, type);
        } catch (Exception ignored) {
            return null;
        }
    }
}
