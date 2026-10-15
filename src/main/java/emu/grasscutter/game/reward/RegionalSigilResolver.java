package emu.grasscutter.game.reward;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CityData;
import java.util.Locale;

/** Resolves a regional sigil using source-backed geographic identity. */
public final class RegionalSigilResolver {
    private RegionalSigilResolver() {}

    public static int resolve(RewardContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Regional sigil requires reward context");
        }
        if (context.sourceRegion() != null && !context.sourceRegion().isBlank()) {
            return fromSourceRegion(context.sourceRegion());
        }
        if (context.sceneId() <= 0 || context.areaId() <= 0) {
            throw new IllegalArgumentException(
                    "Cannot resolve regional sigil: no source region or area ID");
        }
        var worldArea = GameData.getWorldAreaDataMap().get(context.areaId());
        if (worldArea == null || worldArea.getSceneId() != context.sceneId()) {
            throw new IllegalArgumentException(
                    "Cannot resolve regional sigil: scene " + context.sceneId()
                            + " area " + context.areaId() + " has no matching world-area row");
        }

        int cityId = 0;
        for (CityData city : GameData.getCityDataMap().values()) {
            var areas = city.getAreaIdVec();
            if (areas == null || !areas.contains(context.areaId())) continue;
            if (cityId != 0) {
                throw new IllegalArgumentException(
                        "Ambiguous regional sigil: area " + context.areaId()
                                + " belongs to multiple cities");
            }
            cityId = city.getCityId();
        }
        return fromCityId(cityId);
    }

    static int fromCityId(int cityId) {
        // Special cities (99+) are not reliable nation identifiers.
        return switch (cityId) {
            case 1 -> 305;     // Mondstadt
            case 2 -> 307;     // Liyue
            case 3 -> 304;     // Inazuma
            case 4 -> 303;     // Sumeru
            case 5 -> 302;     // Fontaine
            case 6 -> 301;     // Natlan
            case 7 -> 308;     // Nod-Krai
            case 8, 9 -> 306;  // Snezhnaya
            default -> throw new IllegalArgumentException(
                    "No verified regional sigil mapping for city " + cityId);
        };
    }

    static int fromSourceRegion(String region) {
        return switch (region.toLowerCase(Locale.ROOT)) {
            case "mondstadt" -> 305;
            case "liyue" -> 307;
            case "inazuma" -> 304;
            case "sumeru" -> 303;
            case "fontaine" -> 302;
            case "natlan" -> 301;
            case "nodkrai", "nod-krai", "nod_krai" -> 308;
            case "snezhnaya" -> 306;
            default -> throw new IllegalArgumentException(
                    "Unknown regional sigil source: " + region);
        };
    }
}
