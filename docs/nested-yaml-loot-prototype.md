# Reward configuration

Bundled rewards live in `src/main/resources/rewards/`: the `loot.yaml` pool
definitions and all `.css` files directly in that directory. The current CSS files
are `chests.css`, `transpoints.css`, `blossoms.css`, and `world-boss.css`. The loader
discovers all CSS files automatically from the classpath, including packaged
JAR resources, and combines them in filename order before parsing. Adding a
new CSS file needs no registration or changes to a file-count constant.
Rule specificity and source order still decide overrides. There are no
editable `config/rewards` copies.

CSS selector alternatives use native `:is()`, `:where()`, and `:not()`, for example
`boss[type="world"]:is([tag="anemo_hypostasis"], [tag="cryo_hypostasis"])`.
Quoted attribute commas are literal under CSS semantics. Numeric intervals
use ordinary CSS attribute-presence selectors over server-generated metadata:
`[data-range-level-70-79]` matches source `level` values from 70 through 79,
and `[data-range-level-93-up]` matches every integer level at least 93,
without a fixed maximum. `[data-range-WR-6-9]` works the same way for world
rank. The loader discovers the range attributes used in the stylesheet and
computes their membership from the corresponding numeric source attributes.
No numeric-range syntax is added to CSS itself. css4j parses declarations, nesting, selectors, and Selectors Level 4 specificity.
CSS Nesting is supported, including the standard `&` nesting selector. css4j
parses nested blocks and expands nested selectors. No separate CSS parser or
selector whitelist is used.
The reward language still interprets declaration values as item counts, ranges,
and explicit weighted outcomes. The original `level` and
`WR` attributes remain available for exact-value selectors.

## No implicit implementation ceilings

The DSL has no arbitrary runtime depth limit, count limit, pool-count limit,
entry-count limit, or template-instance limit. Recursive named pools, parameterized
templates, and YAML aliases are valid. The compiler checks the full recursive
pool graph and requires an **expected total of at most 10 pool executions** from
each root, including the root. Infinite expectations are rejected. Item copy
counts do not contribute pool nodes. Native protocol and Java numeric bounds
still apply. Finite expectation does not imply a bounded worst-case call stack.

## Unified weighted expressions

Every entry attribute can use an explicit relative-weight expression. The
shared evaluator also selects weighted pool branches. No candidate receives
an implicit weight, and weights do not need to total 100.

```yaml
weapon: {type: "A:3, B:1"}
same: {type: "A:3, B:1", count: 2}
independent: {type: weapon, count: 2}
gem: {type: genshin.avatar_ascension, element: "anemo:3, pyro:1", rarity: "2:3, 3:1"}
```

A weighted item choice selects its type once, then applies `count`. A named
pool reference with `count: 2` executes the pool twice independently.
Both use the same weighted evaluator; the target determines the count scope.
New item attributes are passed to the owning item compiler, without adding
another weight parser to the entry grammar.

YAML complex mapping keys are valid weighted pool candidates:

```yaml
group:
  - [{type: A, count: 2}, {type: B, count: 3}]: 3
  - {type: C}: 1
```

A plain sequence evaluates every entry. A weighted sequence selects one branch
per execution. Mixing weighted and unweighted branches at the same level is
rejected. Parameterized templates accept uppercase names such as
`boss<MORA, FIRST, SECOND, N>`.

Bundled candidate pools use structured `type` attributes. Artifacts use
`type: genshin.artifact` with `set`, `slot`, `rarity`, `substats`;
gems use `type: genshin.avatar_ascension` with `element` and `rarity`;
talent books use `type: genshin.talent_up` with `series` and `rarity`.
Each attribute uses the common weighted-expression parser.

## Bindings and generic calls

```css
boss[type="world"][tag="maguu_kenki"] {
    --boss-material: "marionette_core";
    --element: "[anemo, cryo]";
}

boss[type="world"][data-range-level-90-92] {
    --grant-genshin\.currency\.mora: 5200;
    --grant-genshin\.experience\.companionship: 45;
    --grant-genshin\.experience\.character: 200;
    --grant-genshin\.avatar_ascension\.\<BOSS_MATERIAL\>: "2:44, 3:56";
}

boss[type="world"][tag="maguu_kenki"][data-range-level-50-54] {
    --grant-astaps\.loot\.world_gem_two\<3\,\<ELEMENT\>\[0\]\,\<ELEMENT\>\[1\]\>: "1:25, 2:75";
}
```

`<ELEMENT>` is a scalar binding; lists use explicit zero-based indexes.
`<BOSS_MATERIAL>` expands a material list into independent per-item grants.
Bindings are resolved from all matching rules before count declarations, so
identity declarations may follow shared level rules. Selector specificity and
source order determine overrides. A missing material or element binding
awards no bound reward; an out-of-range binding index is rejected.

World Boss gem and artifact rules use explicit `tag` selectors and level
intervals. Artifact set names are supplied directly to the existing
`world_artifact_*star<SETS...>` templates. Special Instructor artifacts
retain their own slot/substat distribution.

```yaml
world_gem_two<COUNT, ELEMENTS...>:
  - type: genshin.avatar_ascension
    element: "<ELEMENTS...>:1"
    rarity: "2:79, 3:21"
    count: <COUNT>
```

An ordinary one-element call is `astaps.loot.world_gem_two<3, <ELEMENT>>`.
Literal arguments such as `<electro>` remain valid. YAML template parameters use
`<element>` or `<set1>` inside strings. The obsolete `${parameter}` expression
and the implicit CSS `<ELEMENT>` pack call are rejected.

`ELEMENT` also selects regional chest sigils, including `lunar`. Ley-line
blossom selectors use `WR`, supplied by `rollBlossom` for world ranks 0–9.

## Counts and independent grants

All reward declarations use standard CSS custom-property identifiers.
`--grant-genshin\\.currency\\.mora: 240;` grants 240 Mora;
`--grant-astaps\\.loot\\.currency: 1;` executes a named loot pool.
The `--element`, `--boss-material`, and `--boss-material-count`
properties carry reward-domain bindings. Resource punctuation is CSS-escaped
within property names, following CSS identifier rules. Legacy dotted CSS
property declarations are invalid and have no compatibility parser.

CSS counts accept integers, inclusive ranges (`2~3`), or quoted relative
weights (`"2:4, 3:5"`). The latter awards two with probability 4/9 and three
with probability 5/9; there is no implicit zero. Explicit zero outcomes are
allowed. `+` and curve expressions are not supported in CSS.

YAML entries use `type`, optional `count` and `weight`, and item-specific
attributes. Numeric weights select one sibling; omitted/null weights execute
independently.
An item's count gives copies; a nested table's count gives independent
executions. All count distributions use explicit relative weights: `"2:3, 3:1"`
means 75% two and 25% three. The total weight has no required value.
An empty outcome requires `0` explicitly, such as `"0:7, 1:3"`.
Every outcome requires a weight; `"2, 3:41.4"` is invalid. Native fractional
counts are exported as two explicitly weighted outcomes.

Direct CSS item declarations override that item's total from loot tables.
Additional grants of an item also present in a random pool must therefore use
an independent table call. The single-element Boss bonus fragment table is:

```yaml
world_gem_fragment<COUNT, ELEMENTS...>:
  - type: genshin.avatar_ascension
    element: "<ELEMENTS...>:1"
    rarity: 3
    count: <COUNT>
```

```css
boss[type="world"][tag="anemo_hypostasis"][data-range-level-40-up] {
    --grant-astaps\.loot\.world_gem_fragment\<1\,\<ELEMENT\>\>: "3:88, 6:12";
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

Gem identities and batch sizes retain their seven original groups. Five shared
profiles cover the original 38 gem definitions; simple grants remain in CSS. Some
probabilities have been rounded to integer weights rather than preserved
bit-for-bit. Native rows continue through level 120; open-ended CSS ranges
continue the last row above that level.

Artifacts use six parameterized templates, one fixed five-star pool, and 25 candidate pools, grouped
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
The CI builds the jar and runs the automated test suite. Full live-server
inventory delivery remains unverified.
