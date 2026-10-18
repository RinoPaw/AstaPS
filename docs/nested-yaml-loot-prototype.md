# Reward configuration

The runtime loads only `src/main/resources/rewards/rewards.css` and
`src/main/resources/rewards/loot.yaml`. There is no separate curve file,
curve lookup, or editable `config/rewards` copy.

## World Boss materials

Material identities and quantities are separate CSS properties:

```css
boss[type="world"][tag="frostnight_scion"] {
    BOSS_MATERIAL: genshin.avatar_ascension.frostnights_glory;
}

boss[type="world"][level="1~4"] {
    getBossMaterial: "1:6614, 2:3386";
}
```

`BOSS_MATERIAL` binds an inventory item; it does not grant a reward.
It also accepts a bracketed list. Each bound item independently executes
the `getBossMaterial` count distribution. The binding and count are resolved
by selector specificity and source order before rewards are rolled.
Different Bosses may share the same count rule while binding different items.

```css
boss[type="world"][tag="icewind_suite"] {
    BOSS_MATERIAL: [
        genshin.avatar_ascension.artificed_spare_clockwork_component_coppelia,
        genshin.avatar_ascension.artificed_spare_clockwork_component_coppelius
    ];
}

boss[type="world"][level="90~99"] {
    getBossMaterial: "2:4444, 3:5556";
}
```

The two materials roll independently: one may award two while the other
awards three. A binding without a matching count does not grant anything.
A generic count without a known Boss binding grants no configured material.
An explicit `getBossMaterial: 0` suppresses materials for that matching rule.

The bundled material rules cover 48 native Boss identities at levels 1-99.
Early game is 1-29; mid game is 30-99. The 25 identities with no early native
material entry use a more-specific zero-count rule. Pyro Regisvine and Oceanid
use their higher-yield mid-game rule. Level 100+ is deferred to native fallback.

## Count expressions

CSS accepts fixed counts, inclusive integer ranges (`2~3`), and quoted
count-to-relative-weight distributions. `"2:4, 3:5"` selects two with
probability 4/9 or three with probability 5/9; it has no implicit zero outcome.
Explicit zero outcomes are allowed, as are zero-weight alternatives.
Weights may be decimal; counts must be integers. Duplicate count outcomes,
negative weights, and a distribution without positive weight are rejected.
The `+` bonus syntax and external curve expressions are not supported in CSS.

CSS direct item declarations and named loot-table execution counts use the
same count expression syntax. Executing an item N times awards N copies;
executing a loot table N times independently evaluates the table N times.

## Current scope

World Boss material, Mora, and experience rules remain in CSS. Gemstone,
artifact, and weekly-Boss configuration has been removed during this work.
Chest contents retain weapons, experience items, ore, food, and talent books;
chest artifact entries are removed. Blossoms and scene-point rewards remain.
This is an intermediate material-focused configuration, not a complete game
reward configuration.

`loot.yaml` currently retains its existing `count` percentage-distribution
syntax for chest contents; it has not been migrated to a new `roll` field.
The CSS count-to-weight syntax does not reinterpret those YAML quantities.
Existing typed `genshin.*` inventory identities and `astaps.loot.*` table
identities retain their namespace resolution. A CSS direct item declaration
overrides the same item's total rolled from ordinary loot, as before.
