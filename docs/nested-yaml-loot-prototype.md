# Reward configuration

Only `src/main/resources/rewards/rewards.css` and `loot.yaml` define bundled
rewards. There are no curves or editable `config/rewards` copies.

## Bindings and generic calls

```css
boss[type="world"][tag="maguu_kenki"] {
    BOSS_MATERIAL: marionette_core;
    ELEMENT: [anemo, cryo];
}

boss[type="world"][level="90~92"] {
    genshin.currency.mora: 5200;
    genshin.experience.companionship: 45;
    genshin.experience.character: 200;
    genshin.avatar_ascension.<BOSS_MATERIAL>: "2:44, 3:56";
}

boss[type="world"][tag="maguu_kenki"][level="50~54"] {
    astaps.loot.world_50-54_gem_2<<ELEMENT>[0], <ELEMENT>[1]>: "1:25, 2:75";
}
```

`<ELEMENT>` is a scalar binding; lists use explicit zero-based indexes.
`<BOSS_MATERIAL>` expands a material list into independent per-item grants.
Bindings are resolved from all matching rules before count declarations, so
identity declarations may follow shared level rules. Selector specificity and
source order determine overrides. A missing binding awards no bound reward;
an out-of-range index is rejected.

```yaml
world_50-54_gem_1<element>:
  - {reward: "genshin.avatar_ascension.<element>.sliver", count: 3, weight: 79}
  - {reward: "genshin.avatar_ascension.<element>.fragment", count: 3, weight: 21}
```

A single argument call is `astaps.loot.world_50-54_gem_1<<ELEMENT>>`.
Literal arguments such as `<electro>` remain valid. YAML generic parameters use
`<element>` or `<set1>` inside strings. The previous `${parameter}` spelling
and CSS variadic `<ELEMENT>` call remain accepted for compatibility; bundled
configuration uses the new explicit forms.

`ELEMENT` also selects regional chest sigils, including `lunar`. Ley-line
blossom selectors use `WR`, supplied by `rollBlossom` for world ranks 0–9.

## Counts and independent grants

CSS counts accept integers, inclusive ranges (`2~3`), or quoted relative
weights (`"2:4, 3:5"`). The latter awards two with probability 4/9 and three
with probability 5/9; there is no implicit zero. Explicit zero outcomes are
allowed. `+` and curve expressions are not supported in CSS.

YAML primitives remain `reward`, `count`, and optional `weight`. Numeric
weights select one sibling; omitted/null weights execute independently.
An item's count gives copies; a nested table's count gives independent
executions. Existing YAML count percentage expressions retain their legacy
implicit-zero semantics; CSS expressions do not reinterpret them.

Direct CSS item declarations override that item's total from loot tables.
Additional grants of an item also present in a random pool must therefore use
an independent table call. The single-element Boss bonus fragment table is:

```yaml
world_gem_fragment<element>:
  - {reward: "genshin.avatar_ascension.<element>.fragment"}
```

```css
boss[type="world"][tag="anemo_hypostasis"][level="40~"] {
    astaps.loot.world_gem_fragment<<ELEMENT>>: "3:88, 6:12";
}
```

This adds to main-pool fragments instead of replacing them. Boss gem groups
1 and 3 share this deterministic candidate table, supplying their own total
fragment quantities in CSS.

## World Bosses

All 48 native identities have material, gem, Mora, experience, and artifact
configuration. Materials preserve early-game absence for 25 identities,
Pyro Regisvine/Oceanid exceptions, and increased level-100 quantities for
23 identities. Icewind Suite's two materials roll independently.

Gem identities and batch sizes retain their seven original groups. Weighted
candidates use 38 generic definitions; simple grants remain in CSS. Some
probabilities have been rounded to integer weights rather than preserved
bit-for-bit. Native rows continue through level 120; open-ended CSS ranges
continue the last row above that level.

Artifacts use twelve generic definitions and 25 candidate pools, grouped
into nine Boss programs. Slot, rarity, initial substat count, and unusual
native slot/substat correlations remain explicit. Independent branches with
the same candidate pool use an exact convolution of execution counts to avoid
CSS-property overwrites. See [the artifact notes](examples/world-boss-artifacts.md).
Precision Clockwork's missing pools use the explicitly approved Pyro Regisvine
fallback; its internal probabilities have not been recovered from original data.

## Regional chest artifacts

The native chest-artifact migration covers cities 1-4, including 184 combat,
puzzle and Dragonspine source rows, collapsed to 56 city/grade/level selectors.
There are 26 native candidate pools, 20 nested weighted mixtures, and 135
unique source artifact item IDs. Each roll retains the original set, rarity,
slot and initial-substat distribution; the count rules combine independent
branch probability with native fractional execution counts. Combat/puzzle and
Dragonspine sources agree for these artifact branches.

Archipelago source roots are not mapped to the city selector, while shrine
roots are a separate reward type; neither is silently treated as an ordinary
regional chest. Cities 5-9 have no matching native chest roots in the dataset.
Source: AstaPS ChestDrop/DropTableExcelConfigData and AstaPS-Resource
DropSubTable/ReliquaryExcelConfigData at `c0213668889ed05cbeb86660f424c3daf18bfacd`.

## Regional chest payouts and limits

Mora, character EXP books, and talent books for cities 1–4 follow the native
combat roots in `data/ChestDrop.json` and `data/Server/DropTableExcelConfigData.json`,
expanded with `RinoPaw/AstaPS-Resource` at
`c0213668889ed05cbeb86660f424c3daf18bfacd`.
Mondstadt and Liyue retain level tiers. Inazuma and Sumeru use their own
quality-based Mora, EXP books, regional talent families, and talent-book rarities.
Talent candidates live in YAML; execution counts live in CSS.

Several puzzle reward subtables are empty. By explicit user choice, the
same-region combat payouts fill those gaps without adding a source attribute.
This is a fallback, not an exact reconstruction of those puzzle subtables.
The existing combat/puzzle recipe union is retained.

Cities 5–9 lack matching native chest roots in this dataset and retain their
previous generic payouts. Weapon/ore chest subtables are also often empty;
existing weapon candidates and ore amounts remain provisional. Chest artifacts for cities 1-4 have been restored from the historical native
source; weekly Bosses remain absent. Cities 5-9 and island/shrine sources are
not inferred. The 30 Adventure EXP chest policy and regional
Primogem overrides are intentional configuration rules, not a literal copy of
historical data. The source is historical server configuration, not a verified
current official-server drop study.

Both configuration files begin with these limitations. Native item names
still depend on resource mappings; the known material/gem name-map gaps and
full server inventory delivery are outside this change's validation scope.
No test suite or CI is run for this work.
