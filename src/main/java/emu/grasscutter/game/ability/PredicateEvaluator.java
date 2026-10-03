package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PredicateEvaluator {

    private PredicateEvaluator() {}

    public static boolean all(List<Map<String, Object>> predicates, Ability ability,
                               GameEntity owner, GameEntity target, AbilityModifierAction action) {
        if (predicates == null || predicates.isEmpty()) return true;
        for (var pred : predicates) {
            if (pred == null) continue;
            if (!evaluate(pred, ability, owner, target, action)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> pred, Ability ability,
                                    GameEntity owner, GameEntity target, AbilityModifierAction action) {
        Object typeObj = pred.get("$type");
        if (!(typeObj instanceof String rawType)) return true;
        String type = normalizeType(rawType);
        GameEntity resolved = resolveTarget(pred, ability, owner, target);
        if ("BJJDEAIEIGP".equals(type)) {
            GameEntity caster = ability != null ? ability.getCasterEntity() : null;
            return hasHexenzirkelTag(caster != null ? caster : (owner != null ? owner : resolved));
        }
        if ("ByUnlockTalentParam".equals(type)) {
            // Prefer the predicate's target (CasterOriginOwner / Target / …); fall back to caster.
            GameEntity talentTarget = resolveTalentParamTarget(pred, ability, owner, resolved);
            return byUnlockTalentParam(pred, talentTarget);
        }
        return switch (type) {
            case "ByHasModifier"       -> byHasModifier(pred, ability, resolved);
            case "ByTargetGlobalValue" -> byTargetGlobalValue(pred, ability, resolved);
            case "ByTargetHPRatio"     -> byTargetHPRatio(pred, ability, resolved);
            case "ByElementType"       -> byElementType(pred, resolved);
            case "ByEntityTypes"       -> byEntityTypes(pred, resolved);
            case "ByEntityIsAlive"     -> resolved != null && resolved.isAlive();
            case "ByAvatarWeaponType"  -> byAvatarWeaponType(pred, resolved);
            case "ByEnergyRatio"       -> byEnergyRatio(pred, ability, resolved);
            case "ByEnergy"            -> byEnergy(pred, ability, resolved);
            case "ByCurTeamHasElementType" -> byCurTeamHasElementType(pred, ability, resolved);
            case "ByStamina"           -> byStamina(pred, ability, resolved);
            case "ByNot"               -> byNot(pred, ability, owner, target, action);
            case "ByAny"               -> byAny(pred, ability, owner, target, action);
            default -> true;
        };
    }

    private static String normalizeType(String type) {
        return switch (type) {
            case "LJBLAFGJKGI" -> "ByHasModifier";
            case "EOFDCELPGFO" -> "ByTargetGlobalValue";
            case "HGKCHJOOMCH" -> "ByTargetHPRatio";
            case "LCCNMKNDACG" -> "ByUnlockTalentParam";
            case "ILNLCKCOGFD" -> "ByElementType";
            case "DEOFBICNFHF" -> "ByEntityTypes";
            case "FONKGIILJIO" -> "ByEntityIsAlive";
            case "EIBIHNJLLFH" -> "ByAvatarWeaponType";
            case "KJBEKOGFBKG" -> "ByEnergyRatio";
            case "PAPHNBCKAGI" -> "ByEnergy";
            case "NLNHFDLONMM" -> "ByCurTeamHasElementType";
            case "OPLIAABFJGD" -> "ByStamina";
            case "GKGBIPDLMMG" -> "ByNot";
            case "GPEMEIHPCCF" -> "ByAny";
            default -> type;
        };
    }

    private static boolean byNot(Map<String, Object> pred, Ability ability, GameEntity owner,
                                 GameEntity target, AbilityModifierAction action) {
        List<Map<String, Object>> nested = supportedNestedPredicates(pred.get("predicates"));
        if (nested == null) return true;
        return !all(nested, ability, owner, target, action);
    }

    private static boolean byAny(Map<String, Object> pred, Ability ability, GameEntity owner,
                                 GameEntity target, AbilityModifierAction action) {
        List<Map<String, Object>> nested = supportedNestedPredicates(pred.get("predicates"));
        if (nested == null) return true;
        for (var child : nested) {
            if (evaluate(child, ability, owner, target, action)) return true;
        }
        return false;
    }

    /**
     * Returns nested predicates only when every child is one we can evaluate. A partially supported
     * logical expression is left permissive, matching the evaluator's historical unknown-predicate
     * behavior instead of turning an unknown child into a false gate.
     */
    private static List<Map<String, Object>> supportedNestedPredicates(Object value) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) return null;
        var result = new ArrayList<Map<String, Object>>(raw.size());
        for (Object item : raw) {
            Map<String, Object> child = predicateMap(item);
            if (child == null || !isSupportedPredicate(child)) return null;
            result.add(child);
        }
        return result;
    }

    private static boolean isSupportedPredicate(Map<String, Object> pred) {
        Object typeObj = pred.get("$type");
        if (!(typeObj instanceof String rawType)) return false;
        String type = normalizeType(rawType);
        return switch (type) {
            case "BJJDEAIEIGP", "ByUnlockTalentParam", "ByHasModifier",
                    "ByTargetGlobalValue", "ByTargetHPRatio", "ByElementType", "ByEntityTypes",
                    "ByEntityIsAlive", "ByAvatarWeaponType", "ByEnergyRatio", "ByEnergy",
                    "ByCurTeamHasElementType", "ByStamina" -> true;
            case "ByNot", "ByAny" -> supportedNestedPredicates(pred.get("predicates")) != null;
            default -> false;
        };
    }

    private static Map<String, Object> predicateMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return null;
        var result = new java.util.HashMap<String, Object>();
        for (var entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }

    private static GameEntity resolveTalentParamTarget(Map<String, Object> pred, Ability ability,
                                                        GameEntity owner, GameEntity resolved) {
        Object t = pred.get("target");
        if (t instanceof String s) {
            if ("CasterOriginOwner".equals(s) || "OriginOwner".equals(s) || "Caster".equals(s)) {
                GameEntity caster = ability != null ? ability.getCasterEntity() : null;
                if (caster instanceof EntityAvatar) return caster;
                if (ability != null && ability.getPlayerOwner() != null) {
                    return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
                }
            }
            if ("Team".equals(s) && ability != null && ability.getPlayerOwner() != null) {
                return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
            }
        }
        if (resolved instanceof EntityAvatar) return resolved;
        if (owner instanceof EntityAvatar) return owner;
        GameEntity caster = ability != null ? ability.getCasterEntity() : null;
        if (caster instanceof EntityAvatar) return caster;
        if (ability != null && ability.getPlayerOwner() != null) {
            return ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        return resolved;
    }

    private static GameEntity resolveTarget(Map<String, Object> pred, Ability ability,
                                              GameEntity owner, GameEntity defaultTarget) {
        Object t = pred.get("target");
        if (!(t instanceof String s)) return defaultTarget;
        return switch (s) {
            case "Self", "Target", "Applier" -> defaultTarget;
            case "Owner" -> owner != null ? owner : defaultTarget;
            case "Caster", "CasterOriginOwner" -> {
                GameEntity caster = ability != null ? ability.getCasterEntity() : null;
                yield caster != null ? caster : defaultTarget;
            }
            case "OriginOwner", "CurLocalAvatar" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity()
                    : defaultTarget;
            case "Team" ->
                ability != null && ability.getPlayerOwner() != null
                    ? ability.getPlayerOwner().getTeamManager().getEntity()
                    : defaultTarget;
            default -> defaultTarget;
        };
    }

    private static boolean hasHexenzirkelTag(GameEntity target) {
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null || avatar.getAvatarData() == null) return false;
        var tags = avatar.getAvatarData().getTags();
        return tags != null && tags.contains("AVATAR_TAG_HEXENZIRKEL");
    }

    private static boolean byUnlockTalentParam(Map<String, Object> pred, GameEntity target) {
        Object tp = pred.get("talentParam");
        if (!(tp instanceof String talentParam) || talentParam.isEmpty()) return false;
        if (!(target instanceof EntityAvatar ea)) return false;
        Avatar avatar = ea.getAvatar();
        if (avatar == null) return false;

        // UnlockTalentParam stores a named param (e.g.
        // Player_Ice_StarSuperconducted_Aura_Permanent_Skill_1) under openConfig
        // (e.g. Player_Ice_PermanentSkill_1). Comparing talentParam to openConfig always fails.
        if (avatar.getProudSkillList() != null) {
            for (int proudSkillId : avatar.getProudSkillList()) {
                if (proudSkillUnlocksTalentParam(proudSkillId, talentParam)) {
                    return true;
                }
            }
        }
        if (avatar.getTalentIdList() != null) {
            for (int talentId : avatar.getTalentIdList()) {
                var td = GameData.getAvatarTalentDataMap().get(talentId);
                if (td == null || td.getOpenConfig() == null) continue;
                if (openConfigUnlocksTalentParam(td.getOpenConfig(), talentParam)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean proudSkillUnlocksTalentParam(int proudSkillId, String talentParam) {
        ProudSkillData ps = GameData.getProudSkillDataMap().get(proudSkillId);
        if (ps == null || ps.getOpenConfig() == null) return false;
        if (talentParam.equals(ps.getOpenConfig())) return true;
        return openConfigUnlocksTalentParam(ps.getOpenConfig(), talentParam);
    }

    private static boolean openConfigUnlocksTalentParam(String openConfigName, String talentParam) {
        var entry = GameData.getOpenConfigEntries().get(openConfigName);
        if (entry == null || entry.getTalentParamEntries() == null) return false;
        for (var param : entry.getTalentParamEntries()) {
            if (param != null && talentParam.equals(param.getParamName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean byHasModifier(Map<String, Object> pred, Ability ability, GameEntity target) {
        Object mn = pred.get("modifierName");
        if (!(mn instanceof String modifierName) || ability == null || target == null) return false;
        for (Ability ab : target.getInstancedAbilities()) {
            if (ab == null) continue;
            if (ab.getModifiers().containsKey(modifierName)) return true;
        }
        return false;
    }

    private static boolean byTargetGlobalValue(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null) return false;
        Object key = pred.get("key");
        if (!(key instanceof String k)) return false;
        // Team-scoped keys used by Columbina PermanentSkill_2 live on the team entity.
        GameEntity valueEntity = target;
        if (("_ABILITY_Columbina_IsTeamInField".equals(k)
                || "MoonOvergrowPoint_All".equals(k)
                || "RGV_TempMoonOvergrowPoint".equals(k))
                && ability != null
                && ability.getPlayerOwner() != null) {
            var team = ability.getPlayerOwner().getTeamManager().getEntity();
            if (team != null) valueEntity = team;
        }
        float current = valueEntity.getGlobalAbilityValues().getOrDefault(k, 0f);
        // Also accept avatar-local copies for AddCount / temp keys.
        if (current == 0f && valueEntity != target) {
            current = target.getGlobalAbilityValues().getOrDefault(k, 0f);
        }
        Object boundValue = pred.containsKey("value") ? pred.get("value") : pred.get("CBOMLBFIPJM");
        float bound = readFloat(boundValue, ability);
        Object cmpObj = pred.get("compareType");
        String cmp = cmpObj instanceof String s ? s : "Equal";
        return switch (cmp) {
            case "MoreThan", "Greater"    -> current > bound;
            case "MoreThanAndEqual", "MoreOrEqual", "GreaterOrEqual" -> current >= bound;
            case "LessThan", "Lesser"     -> current < bound;
            case "LessThanAndEqual", "LessOrEqual", "LesserOrEqual" -> current <= bound;
            case "NotEqual"               -> current != bound;
            default                       -> current == bound;
        };
    }

    private static boolean byTargetHPRatio(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (target == null || ability == null) return true;
        Object hpRatio = pred.containsKey("HPRatio") ? pred.get("HPRatio") : pred.get("KIKIBABLEMA");
        float threshold;
        if (hpRatio instanceof String key) {
            threshold = ability.getAbilitySpecials().getOrDefault(key, 0f);
            if (threshold <= 0f) {
                threshold = readFloat(hpRatio, ability);
            }
        } else {
            threshold = readFloat(hpRatio, ability);
        }
        if (threshold <= 0f) return true;
        float maxHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = target.getFightProperty(emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_HP);
        if (maxHp <= 0f) return true;
        float ratio = curHp / maxHp;
        Object logicObj = pred.get("logic");
        String logic = logicObj instanceof String s ? s : "Greater";
        return switch (logic) {
            case "Lesser", "LessThan" -> ratio < threshold;
            case "LesserOrEqual", "LessThanAndEqual", "LessOrEqual" -> ratio <= threshold;
            case "GreaterOrEqual", "MoreThanAndEqual", "MoreOrEqual" -> ratio >= threshold;
            case "Equal" -> Math.abs(ratio - threshold) < 1e-5f;
            case "NotEqual" -> Math.abs(ratio - threshold) >= 1e-5f;
            default -> ratio > threshold; // Greater / MoreThan
        };
    }

    private static boolean byElementType(Map<String, Object> pred, GameEntity target) {
        Object typeObj = pred.get("elementType");
        if (!(typeObj instanceof String elementType) || elementType.isEmpty()) return true;
        // The predicate also appears on gadgets and other non-avatar entities. Leave those permissive
        // until their elemental source is modeled; avatar branches (including MoonPhase) are exact.
        if (!(target instanceof EntityAvatar entityAvatar)
                || entityAvatar.getAvatar() == null
                || entityAvatar.getAvatar().getSkillDepot() == null) {
            return true;
        }
        return elementType.equals(entityAvatar.getAvatar().getSkillDepot().getElementType().name());
    }

    private static boolean byEntityTypes(Map<String, Object> pred, GameEntity target) {
        // Some configs ask for the event source rather than the resolved action target. The
        // evaluator is not given an event-source entity yet, so keep those permissive instead of
        // filtering the wrong object.
        if (Boolean.TRUE.equals(pred.get("useEventSource"))) return true;
        if (target == null) return false;

        Object typesObj = pred.get("entityTypes");
        if (!(typesObj instanceof List<?> types) || types.isEmpty()) return true;

        String actual = target.getEntityType().name();
        boolean matches = false;
        for (Object type : types) {
            if (type instanceof String expected && actual.equals(expected)) {
                matches = true;
                break;
            }
        }
        return Boolean.TRUE.equals(pred.get("reject")) ? !matches : matches;
    }

    private static boolean byAvatarWeaponType(Map<String, Object> pred, GameEntity target) {
        if (!(target instanceof EntityAvatar entityAvatar)
                || entityAvatar.getAvatar() == null
                || entityAvatar.getAvatar().getAvatarData() == null) {
            return false;
        }

        Object typesObj = pred.containsKey("weaponTypes")
                ? pred.get("weaponTypes")
                : pred.get("GDONICCLGCO");
        if (!(typesObj instanceof List<?> types) || types.isEmpty()) return true;

        String actual = entityAvatar.getAvatar().getAvatarData().getWeaponType().name();
        for (Object type : types) {
            if (type instanceof String expected && actual.equals(expected)) return true;
        }
        return false;
    }

    private static boolean byEnergyRatio(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (!(target instanceof EntityAvatar entityAvatar)
                || entityAvatar.getAvatar() == null
                || entityAvatar.getAvatar().getSkillDepot() == null) {
            return true;
        }
        if (!(pred.get("logic") instanceof String logic)) return true;
        Object thresholdValue = pred.get("ratio");
        if (thresholdValue instanceof String && ability == null) return true;

        var avatar = entityAvatar.getAvatar();
        var currentProp = entityAvatar.GetEnergyProp(avatar);
        var maxProp = currentProp == emu.grasscutter.game.props.FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY
                ? emu.grasscutter.game.props.FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY
                : avatar.getSkillDepot().getElementType().getMaxEnergyProp();
        float maxEnergy = entityAvatar.getFightProperty(maxProp);
        if (maxEnergy <= 0f) return true;

        float ratio = entityAvatar.getFightProperty(currentProp) / maxEnergy;
        float threshold = readFloat(thresholdValue, ability);
        return compareEnergy(ratio, threshold, logic);
    }

    private static boolean byEnergy(Map<String, Object> pred, Ability ability, GameEntity target) {
        if (!(target instanceof EntityAvatar entityAvatar)
                || entityAvatar.getAvatar() == null
                || entityAvatar.getAvatar().getSkillDepot() == null) {
            // Monster/gadget energy is not modeled by one authoritative fight property here yet.
            return true;
        }
        if (!(pred.get("logic") instanceof String logic)) return true;

        Object boundValue = pred.containsKey("value") ? pred.get("value") : pred.get("CBOMLBFIPJM");
        if (boundValue instanceof String && ability == null) return true;
        float bound = readFloat(boundValue, ability);
        float current = entityAvatar.getFightProperty(entityAvatar.GetEnergyProp(entityAvatar.getAvatar()));
        return compareEnergy(current, bound, logic);
    }

    private static boolean compareEnergy(float current, float bound, String logic) {
        return switch (logic) {
            case "Lesser", "LessThan" -> current < bound;
            case "LesserOrEqual", "LessThanAndEqual", "LessOrEqual" -> current <= bound;
            case "Greater", "MoreThan" -> current > bound;
            case "GreaterOrEqual", "MoreThanAndEqual", "MoreOrEqual" -> current >= bound;
            case "Equal" -> Math.abs(current - bound) < 1e-5f;
            case "NotEqual" -> Math.abs(current - bound) >= 1e-5f;
            default -> true;
        };
    }

    private static boolean byCurTeamHasElementType(
            Map<String, Object> pred, Ability ability, GameEntity target) {
        var player = ability != null ? ability.getPlayerOwner() : null;
        if (player == null && target instanceof EntityAvatar avatar) {
            player = avatar.getPlayer();
        }
        if (player == null || !(pred.get("logic") instanceof String logic)) return true;
        Object elementObj = pred.get("elementType");
        if (!(elementObj instanceof String elementType) || elementType.isEmpty()) return true;

        int count = 0;
        for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
            if (entityAvatar == null
                    || entityAvatar.getAvatar() == null
                    || entityAvatar.getAvatar().getSkillDepot() == null) {
                continue;
            }
            if (elementType.equals(entityAvatar.getAvatar().getSkillDepot().getElementType().name())) {
                count++;
            }
        }

        float required = readFloat(pred.get("number"), ability);
        return switch (logic) {
            case "Lesser", "LessThan" -> count < required;
            case "LesserOrEqual", "LessThanAndEqual", "LessOrEqual" -> count <= required;
            case "Greater", "MoreThan" -> count > required;
            case "GreaterOrEqual", "MoreThanAndEqual", "MoreOrEqual" -> count >= required;
            case "Equal" -> Math.abs(count - required) < 1e-5f;
            case "NotEqual" -> Math.abs(count - required) >= 1e-5f;
            default -> true;
        };
    }

    private static boolean byStamina(Map<String, Object> pred, Ability ability, GameEntity target) {
        var player = ability != null ? ability.getPlayerOwner() : null;
        if (player == null && target instanceof EntityAvatar avatar) {
            player = avatar.getPlayer();
        }
        if (player == null || player.getStaminaManager() == null) return true;

        float threshold = readFloat(pred.get("stamina"), ability);
        // Player stamina properties use hundredths (24000 == 240 visible stamina), while ability
        // predicates use the visible-unit values found in binout configs.
        float current = player.getStaminaManager().getCurrentCharacterStamina() / 100.0f;
        Object logicObj = pred.get("logic");
        String logic = logicObj instanceof String s ? s : "GreaterOrEqual";
        return switch (logic) {
            case "Lesser", "LessThan" -> current < threshold;
            case "LesserOrEqual", "LessThanAndEqual", "LessOrEqual" -> current <= threshold;
            case "Greater", "MoreThan" -> current > threshold;
            case "Equal" -> Math.abs(current - threshold) < 1e-5f;
            case "NotEqual" -> Math.abs(current - threshold) >= 1e-5f;
            default -> current >= threshold;
        };
    }

    private static float readFloat(Object v) {
        return readFloat(v, null);
    }

    /**
     * Resolves predicate bounds, including DynamicFloat op lists such as
     * {@code ["MoonOverGrow_CountMax", 1.0, "SUB"]} used by Columbina PermanentSkill_2.
     */
    private static float readFloat(Object v, Ability ability) {
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String s) {
            if (ability != null) {
                return ability.getAbilitySpecials().getOrDefault(s, 0f);
            }
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
                return 0f;
            }
        }
        if (v instanceof Map<?, ?> m) {
            Object inner = m.get("value");
            if (inner instanceof Number n) return n.floatValue();
            Object exp = m.get("__exp_FixedValue");
            if (exp instanceof Number n) return n.floatValue();
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            var ops = new ArrayList<DynamicFloat.StackOp>(list.size());
            for (Object item : list) {
                if (item instanceof Number n) {
                    ops.add(new DynamicFloat.StackOp(n.floatValue()));
                } else if (item instanceof String s) {
                    ops.add(new DynamicFloat.StackOp(s));
                } else if (item instanceof Boolean b) {
                    ops.add(new DynamicFloat.StackOp(b));
                }
            }
            if (!ops.isEmpty()) {
                var df = new DynamicFloat(ops);
                if (ability != null) {
                    return df.get(ability.getAbilitySpecials(), 0f);
                }
                return df.get(0f);
            }
        }
        return 0f;
    }
}
