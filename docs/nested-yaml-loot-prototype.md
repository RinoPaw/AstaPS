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

## World Boss gems

The gem lists use item parameters in their names, with exactly the same
`reward`, `count`, and `weight` entries as ordinary YAML loot tables:

```yaml
world_50-54_gem_1<element>:
  - {reward: "genshin.avatar_ascension.${element}.sliver", count: 3, weight: 4193}
  - {reward: "genshin.avatar_ascension.${element}.fragment", count: 3, weight: 1118}

world_50-54_gem_2<element1, element2>:
  - {reward: "genshin.avatar_ascension.${element1}.sliver", count: 3, weight: 4193}
  - {reward: "genshin.avatar_ascension.${element1}.fragment", count: 3, weight: 1118}
  - {reward: "genshin.avatar_ascension.${element2}.sliver", count: 3, weight: 4193}
  - {reward: "genshin.avatar_ascension.${element2}.fragment", count: 3, weight: 1118}
```

CSS binds each Boss's elements beside its material identity, then invokes
shared definitions with `<ELEMENT>`:

```css
boss[type="world"][tag="maguu_kenki"] {
    BOSS_MATERIAL: genshin.avatar_ascension.marionette_core;
    ELEMENT: [anemo, cryo];
}

boss[type="world"][tag="aeonblight_drake"] {
    BOSS_MATERIAL: genshin.avatar_ascension.perpetual_caliber;
    ELEMENT: [hydro, cryo];
}

boss[type="world"][tag="aeonblight_drake,maguu_kenki"][level="50~54"] {
    astaps.loot.world_50-54_gem_2<ELEMENT>: "1:24737, 2:75263";
    astaps.loot.world_40-plus_gem_fragment_2<ELEMENT>: "1:439, 2:61";
}
```

`ELEMENT: electro` binds one argument; `ELEMENT: [anemo, cryo]` binds two ordered
arguments. `<ELEMENT>` becomes `<electro>` or `<anemo, cryo>` before resolving
an ordinary loot definition. The binding itself grants nothing and performs
no random choice. The YAML body retains all item selection and count semantics.
Explicit calls such as `<electro>` and `<anemo, cryo>` continue to work.

Bindings follow selector specificity and source order, independently of where
the count rules appear. Missing ELEMENT bindings skip ELEMENT-dependent declarations.
Unknown, empty, and duplicate element bindings are rejected. If explicit and
ELEMENT-bound calls resolve to the same table, normal CSS declaration order and
specificity determine the final count.

A value of `1` executes the referenced list exactly once. A count distribution
controls independent executions, with each execution choosing a new item.
The selected item's own count still gives an intact batch of 3 or 9 copies.
Separate table outputs are added, including repeated copies of the same item.
Arguments only substitute element names; they do not change amounts or odds.
Whitespace between arguments is accepted. The quoted interpolation strings
above use ordinary YAML string quoting. The item name is `sliver`.

A generic call may also appear in an ordinary YAML `reward` field; its list
is expanded before compiling ordinary loot entries. Existing integer loot
parameters and their safe arithmetic continue to work. Missing definitions,
duplicate parameters, wrong argument counts, and cyclic calls are errors.

There is no `templates`, `parameters`, or `levels` mapping. The range in a
definition's name is just part of its name; CSS owns level matching. No
external curve file or level-template loader is used.

The native data produces these seven groups. Group numbers in the names
identify equal counts and complete probabilities after replacing item names;
a batch of 3 and a batch of 9 stay in separate groups.

| Group | Bosses at levels 1-29 | Bosses at levels 30+ |
| --- | ---: | ---: |
| 1 | 15 | 15 |
| 2 | 9 | 9 |
| 3 | 1 (Setekh Wenut) | 14 |
| 4 | 0 | 2 |
| 5 | 5 | 5 |
| 6 | 2 | 2 |
| 7 | 1 | 1 |

The 48 Boss identity rules each bind both materials and elements. The gem
selection rules share 77 declarations instead of 439 repeated rules.

The other 15 Bosses have no gem entry at levels 1-29. Different level ranges
have separate definitions when their candidate weights change. Independent
fragment lists use the same parameter syntax. Group 7 binds the three
elements of Tenebrous Papilla using the same argument mechanism.

The source is `data/ChestDrop.json` followed by
`data/Server/DropTableExcelConfigData.json`: 48 identities and 1200 native
root rows. Level 90+ uses the last identical gem distribution, including
native rows at 93, 100, 105, 110, 115, and 120; the final CSS range is open-ended.

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

World Boss material, Mora, and experience rules remain in CSS. Gemstone
contents and candidate weights live in parameterized YAML lists selected by
CSS. Artifact and weekly-Boss configuration remain absent during this work.
Chest contents retain weapons, experience items, ore, food, and talent books;
chest artifact entries are removed. Blossoms and scene-point rewards remain.
This is an intermediate world-Boss configuration, not a complete game
reward configuration.

`loot.yaml` retains its existing `count` percentage-distribution syntax
for chest contents; it has not been migrated to a new `roll` field. Gem
execution counts use CSS count-to-weight distributions.
The CSS count-to-weight syntax does not reinterpret those YAML quantities.
Existing typed `genshin.*` inventory identities and `astaps.loot.*` table
identities retain their namespace resolution. A CSS direct item declaration
overrides the same item's total rolled from ordinary loot, as before.
