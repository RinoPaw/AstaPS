package emu.grasscutter.game.systems;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.utils.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 圣遗物功能共用的资源参数。 */
final class ReliquaryConfig {
    private static volatile ReliquaryConfig instance;
    final Map<String, List<String>> constants = new HashMap<>();
    final Map<Integer, Purchase> purchases = new HashMap<>();
    final List<Bundle> transfer = new ArrayList<>();
    final Set<Integer> options = new HashSet<>();
    final Map<Integer, Set<Integer>> mainProps = new HashMap<>();
    final Set<Integer> appendProps = new HashSet<>();
    final Map<Integer, List<ReliquaryState.LockScheme>> defaults = new HashMap<>();
    int maxSchemes;

    static synchronized ReliquaryConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    static JsonArray rows(String name) {
        Path path = FileUtils.getResourcePath("ExcelBinOutput/" + name + ".json");
        if (!Files.isRegularFile(path)) return new JsonArray();
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonArray();
        } catch (IOException e) {
            throw new IllegalStateException("无法读取圣遗物配置", e);
        }
    }

    private static ReliquaryConfig load() {
        var c = new ReliquaryConfig();
        for (var element : rows("ConstValueExcelConfigData")) {
            var row = element.getAsJsonObject();
            if (!row.has("name")) continue;
            c.constants.put(row.get("name").getAsString(), strings(row.getAsJsonArray("value")));
        }
        var gson = new Gson();
        for (var row : rows("ReliquaryOfferPurchaseExcelConfigData")) {
            var p = gson.fromJson(row, Purchase.class);
            c.purchases.put(p.id, p);
        }
        var groups =
                c.constants.getOrDefault(
                        "CONST_VALUE_RELIQUARY_TRANSFER_MATERIALS_V2",
                        c.constants.getOrDefault(
                                "CONST_VALUE_RELIQUARY_TRANSFER_MATERIALS", List.of()));
        for (var value : groups) {
            if (value.isBlank()) continue;
            var items = new TreeMap<Integer, Integer>();
            long exp = 0;
            for (var entry : value.split(",")) {
                var pair = entry.split(":");
                int id = Integer.parseInt(pair[0]);
                int count = pair.length == 2 ? Integer.parseInt(pair[1]) : 1;
                int unit = materialExp(id);
                if (unit <= 0 || count <= 0) throw new IllegalStateException("圣遗物经验素材配置无效");
                items.merge(id, count, Math::addExact);
                exp = Math.addExact(exp, (long) unit * count);
            }
            c.transfer.add(new Bundle(exp, items));
        }
        c.transfer.sort(Comparator.comparingLong(Bundle::exp).reversed());
        for (var row : rows("ReliquaryLockGeneralOptionExcelConfigData"))
            c.options.add(row.getAsJsonObject().get("id").getAsInt());
        c.maxSchemes = rows("ReliquarySetSchemeExcelConfigData").size();
        for (var entry : rows("ReliquarySetSchemeMainPropExcelConfigData")) {
            var row = entry.getAsJsonObject();
            int slot = EquipType.valueOf(row.get("equipType").getAsString()).getValue();
            var values = new HashSet<>(numbers(row.getAsJsonArray("HANABIILGLG")));
            values.addAll(numbers(row.getAsJsonArray("LEOOOEDHHAE")));
            c.mainProps.put(slot, values);
        }
        for (var entry : rows("ReliquarySetSchemeAppendPropExcelConfigData"))
            c.appendProps.addAll(numbers(entry.getAsJsonObject().getAsJsonArray("KHPJAEPHFLJ")));
        Path defaults = FileUtils.getDataPath("ReliquaryLockSchemes.json");
        try (var input =
                Files.isRegularFile(defaults)
                        ? Files.newInputStream(defaults)
                        : ReliquaryConfig.class.getResourceAsStream("/ReliquaryLockSchemes.json")) {
            if (input != null) {
                Map<Integer, List<ReliquaryState.LockScheme>> map =
                        gson.fromJson(
                                new java.io.InputStreamReader(
                                        input, java.nio.charset.StandardCharsets.UTF_8),
                                new TypeToken<
                                        Map<
                                                Integer,
                                                List<ReliquaryState.LockScheme>>>() {}.getType());
                if (map != null) c.defaults.putAll(map);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取套装锁定方案", e);
        }
        return c;
    }

    String param(String name, int index) {
        var values = constants.get(name);
        if (values == null || index >= values.size() || values.get(index).isBlank())
            throw new IllegalArgumentException("缺少圣遗物配置参数");
        return values.get(index);
    }

    int value(String name, int index) {
        return Integer.parseInt(param(name, index));
    }

    List<Integer> values(String name, int index) {
        return Arrays.stream(param(name, index).split(";")).map(Integer::parseInt).toList();
    }

    Map<Integer, Integer> pairs(String name, int index) {
        var result = new TreeMap<Integer, Integer>();
        for (var part : param(name, index).split(";")) {
            var p = part.split(",");
            result.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        return result;
    }

    Map<Integer, Double> offeringMaterials() {
        var result = new HashMap<Integer, Double>();
        for (var part : param("CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_3", 1).split(";")) {
            var p = part.split(",");
            double progress = Double.parseDouble(p[1]);
            if (!Double.isFinite(progress) || progress <= 0)
                throw new IllegalStateException("萃取进度配置无效");
            result.put(Integer.parseInt(p[0]), progress);
        }
        return result;
    }

    static int materialExp(int id) {
        var item = GameData.getItemDataMap().get(id);
        if (item == null || item.getItemUse() == null) return 0;
        for (var use : item.getItemUse())
            if (use.getUseOp().getValue() == 37
                    && use.getUseParam() != null
                    && use.getUseParam().length > 0) return Integer.parseInt(use.getUseParam()[0]);
        return 0;
    }

    private static List<Integer> numbers(JsonArray array) {
        var out = new ArrayList<Integer>();
        if (array != null) for (var v : array) out.add(v.getAsInt());
        return out;
    }

    private static List<String> strings(JsonArray array) {
        var out = new ArrayList<String>();
        if (array != null) for (var v : array) out.add(v.getAsString());
        return out;
    }

    record Bundle(long exp, Map<Integer, Integer> items) {}

    static final class Purchase {
        int id, offerSetId;
        List<Integer> flowerIds = List.of(),
                featherIds = List.of(),
                timepieceIds = List.of(),
                gobletIds = List.of(),
                circletIds = List.of(),
                weights = List.of();

        List<Integer> items(int slot) {
            return switch (slot) {
                case 1 -> flowerIds;
                case 2 -> featherIds;
                case 3 -> timepieceIds;
                case 4 -> gobletIds;
                case 5 -> circletIds;
                default -> List.of();
            };
        }
    }
}
