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

Seven item-parameterized templates in `loot.yaml` reproduce the native gem
amounts and probabilities. Bosses share a template only when their full gem
reward distribution at the same level is identical after renaming items.
A batch of 3 and a batch of 9 never share a template.

| Template | Bosses at levels 1-29 | Bosses at levels 30+ |
| --- | ---: | ---: |
| `world_gems_1` | 15 | 15 |
| `world_gems_2` | 9 | 9 |
| `world_gems_3` | 1 (Setekh Wenut) | 14 |
| `world_gems_4` | 0 | 2 |
| `world_gems_5` | 5 | 5 |
| `world_gems_6` | 2 | 2 |
| `world_gems_7` | 1 | 1 |

The other 15 Bosses have no gem entry at levels 1-29. Templates 5-7 grant
four, two, and six distinct items respectively, each with count 9.
Template 4 binds Lightbearing Lunar Moth and Precision Clockwork. Different
element pairs are separate bindings; no candidate probabilities are changed.

A template has `parameters` and `levels`; every level value is an ordinary
loot list containing only `reward`, `count`, and `weight`. Parameters replace
item-family names, and do not affect the meaning of count or weight:

```yaml
templates:
  example_gems:
    parameters: [gem1]
    levels:
      "50~54":
        - reward:
            - {reward: "${gem1}.sliver", count: 3, weight: 4193}
            - {reward: "${gem1}.fragment", count: 3, weight: 1118}
          count: "1:24.737, 2:75.263"

example_electro:
  template: example_gems
  parameters:
    gem1: genshin.avatar_ascension.electro
```

CSS selects a binding with a normal loot declaration:

```css
boss[type="world"][tag="electro_hypostasis"][level="50~54"] {
    astaps.loot.example_electro: 1;
}
```

The examples above are syntax illustrations; the loaded templates contain
all native ranges and independent gem groups. At load time, bindings expand
to ordinary loot pools. CSS chooses the correct pool using its native
`level` attribute before settlement. Missing levels, overlapping ranges,
unknown parameters, and generated-name collisions are errors. Existing
integer loot templates continue to work. No external curves are loaded.

The runtime has seven templates and 31 item bindings for 48 native Boss
identities. Their source is `data/ChestDrop.json` followed by
`data/Server/DropTableExcelConfigData.json`, covering 1200 native root rows.
Level 90+ uses the last identical gem distribution, including native rows
at 93, 100, 105, 110, 115, and 120; the final range remains open-ended.

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
contents and probabilities live in YAML templates selected by CSS. Artifact
and weekly-Boss configuration remain absent during this work.
Chest contents retain weapons, experience items, ore, food, and talent books;
chest artifact entries are removed. Blossoms and scene-point rewards remain.
This is an intermediate world-Boss configuration, not a complete game
reward configuration.

`loot.yaml` retains its existing `count` percentage-distribution syntax
for chest contents and gem-template execution counts; it has not been migrated to a new `roll` field.
The CSS count-to-weight syntax does not reinterpret those YAML quantities.
Existing typed `genshin.*` inventory identities and `astaps.loot.*` table
identities retain their namespace resolution. A CSS direct item declaration
overrides the same item's total rolled from ordinary loot, as before.
