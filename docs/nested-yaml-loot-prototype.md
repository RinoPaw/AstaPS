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

The bundled material rules cover all positive levels for 48 native Boss
identities: early game is 1-29, mid game is 30-99, and late game is 100+.
The 25 identities with no early native material entry use a more-specific
zero-count rule. Pyro Regisvine and Oceanid use their higher-yield mid-game
rule.

At level 100+, material quantities come directly from the bundled native
`data/ChestDrop.json` and `data/Server/DropTableExcelConfigData.json`:

| Boss group | Material count distribution |
| --- | --- |
| 23 identities retaining standard quantities, including Frostnight Scion | 2: 44.44%, 3: 55.56% |
| Pyro Regisvine and Oceanid | 2: 18.884%, 3: 81.116% |
| 23 identities with increased quantities, including Icewind Suite | 3: 75%, 4: 25% |

Icewind Suite grants both bound materials and rolls their counts separately
at every level. The native rows at levels 100, 105, 110, 115, and 120 have
identical material, Mora, and experience quantities. Open-ended CSS selectors
continue the last row above 120, matching native minimum-level selection.
Mora remains 6000 from level 93, with 200 Adventure EXP, 45 Companionship EXP,
and 200 Character EXP at level 100+.

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
