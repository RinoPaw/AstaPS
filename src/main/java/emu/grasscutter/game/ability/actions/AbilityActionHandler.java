package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

public abstract class AbilityActionHandler {
    protected AbilityManager abilityManager;

    public abstract boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target);

    public AbilityActionHandler setManager(AbilityManager manager) {
        this.abilityManager = manager;
        return this;
    }

    /**
     * Builds the dynamic-value context for the ability owner.
     *
     * <p>Fight properties are loaded first, then owner globals, then ability specials. That ordering
     * is intentional: an ability special wins when the same key exists in more than one source.
     */
    protected static Object2FloatMap<String> propertiesFor(Ability ability) {
        var properties = new Object2FloatOpenHashMap<String>();
        var owner = ability.getOwner();

        addFightProperties(properties, owner);
        if (owner != null) {
            owner.getGlobalAbilityValues()
                    .forEach((key, value) -> properties.put(key, value.floatValue()));
        }
        properties.putAll(ability.getAbilitySpecials());
        return properties;
    }

    /**
     * Builds the dynamic-value context used by actions that evaluate against a specific entity.
     *
     * <p>This intentionally contains only that entity's fight properties followed by the ability
     * specials. Callers that historically included global values add them afterwards so their key
     * precedence stays unchanged.
     */
    protected static Object2FloatMap<String> propertiesFor(Ability ability, GameEntity entity) {
        var properties = new Object2FloatOpenHashMap<String>();
        addFightProperties(properties, entity);
        properties.putAll(ability.getAbilitySpecials());
        return properties;
    }

    private static void addFightProperties(
            Object2FloatMap<String> properties, GameEntity entity) {
        if (entity == null) {
            return;
        }
        for (var property : FightProperty.values()) {
            properties.put(property.name(), entity.getFightProperty(property));
        }
    }

    protected GameEntity getTarget(Ability ability, GameEntity entity, String target) {
        return resolveTarget(ability, entity, target);
    }

    /**
     * Resolves the entity an action's target name refers to. Shared with the mixin handlers so both
     * sides agree on what a name means.
     *
     * <p>25 distinct names appear across the ability configs and only some of them describe a single
     * entity, so anything unrecognised falls back to the entity the action is running on. Throwing
     * instead aborted the whole action, which is what left {@code Caster} and {@code Target} - the
     * two most common names in the corpus by a wide margin - failing on every invocation.
     */
    public static GameEntity resolveTarget(Ability ability, GameEntity entity, String target) {
        // An action that names no target acts on whatever the modifier is attached to. Sandrone's
        // robot has several of those, and switching on the absent name threw before the action ran.
        if (target == null) {
            return entity;
        }

        var playerOwner = ability.getPlayerOwner();
        var teamManager = playerOwner != null ? playerOwner.getTeamManager() : null;

        return switch (target) {
            case "Self", "Target", "Applier" -> entity;
            case "Caster", "Owner" -> ability.getOwner();
            case "Team" -> teamManager != null ? teamManager.getEntity() : null;
            case "OriginOwner", "CurLocalAvatar" -> teamManager != null
                    ? teamManager.getCurrentAvatarEntity()
                    : null;
            case "CasterOriginOwner" -> null; // TODO: Figure out.
            default -> {
                Grasscutter.getLogger().debug("Unknown ability target type: {}", target);
                yield entity;
            }
        };
    }
}
