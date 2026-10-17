# Reward configuration

The runtime reads three bundled files: `src/main/resources/rewards/loot.yaml`,
`src/main/resources/rewards/rewards.css`, and
`src/main/resources/rewards/curves.yaml`. There is no editable `config/rewards`
copy.

## CSS selector semantics

A selector element represents a game reward category: `chest` (opening a chest),
`blossom` (claiming a ley-line blossom), `boss` (claiming a Boss reward), or
`transpoint` (first activation of an eligible map point). The `type` attribute
always selects a subtype inside that element: chest `normal`/`mora`/`remarkable`,
blossom `wealth`/`revelation`, boss `world`/`weekly`, and transpoint
`waypoint`/`statue`/`domain`. `grade` is reserved for ordinary chest quality.
Game mechanisms and source identities still belong to their existing Java and
native-data workflows; the CSS selector controls payout selection only. The
CSS `kind` attribute is unsupported. In particular, a `weekly` Boss CSS rule
does not imply the weekly dungeon payment path is already wired to CSS.

## Twelve ordinary chest tiers

Ascending tier order: minimal → nano → micro → light → low → medium → high → xhigh → xxhigh → xxxhigh → max → ultra.

CSS selects among twelve reusable item reward tiers, based on the native
chest grade and `SceneGadget.level` (not the player's Adventure Rank).

| Grade | level 1–5 | 6–10 | 11–15 | 16–20 | 21–25 | 26+ |
|---|---|---|---|---|---|---|
| Common | minimal | nano | micro | light | low | medium |
| Exquisite | medium | medium | medium | medium | high | xhigh |
| Precious | xhigh | xhigh | xhigh | xhigh | xxhigh | xxxhigh |
| Luxurious | xxxhigh | xxxhigh | xxxhigh | xxxhigh | max | ultra |

These twelve names are registered under `astaps.loot`, for example
`astaps.loot.nano` and `astaps.loot.ultra`. All 23 supporting tables come
first in `loot.yaml`, grouped by reward content (weapons, ingredients, talent
books and artifacts) rather than by chest type or level. The twelve selectable
tiers follow in ascending order. CSS alone selects Boss identity and native level. All loot tables,
including complete payout profiles, are named by reward content; optional
`world` and `weekly` prefixes identify the reward type.
Reusable subtables use names such as `midlander_weapon_billets`.
Identical payout profiles are shared, and one-use subtables are inlined.
The bundled YAML currently has 456 top-level definitions, including one
list template. The two `weekly_northlander_extras` /
`weekly_midlander_extras` pools define shared Dream Solvent and weapon-billet
drop chances. Eight further `weekly_*artifacts*` pools hold shared artifact
distributions across level brackets. CSS selects Boss identities and levels.
Direct single-item rewards and independent loot-pool execution counts live in
CSS; nested weighted item/rarity choices remain in YAML. Four
`world_five_star_artifacts_*` pools share five-star Gladiator/Wanderer
drops across 24 older world Boss reward families. Seven `gems_*_basic` tables
hold identical fixed sliver and fragment grants for eight world Boss identities.

World Boss reward categories are independent: 797 previous mixed
`world_*` tables were originally split into direct CSS declarations and
374 category-specific tables. Subsequent exact-curve extraction reduced the
remaining category-specific tables to 238, with weighted choice pools reused
across Boss identities. CSS determines which pool applies and how many times
it is rolled. For Maguu Kenki at native level 50–54:

```css
boss[type="world"][tag="maguu_kenki"][level="50~54"] {
    genshin.avatar_ascension.marionette_core: "${world_ascension_material_count(level)}";
    astaps.loot.gems_anemo_cryo_fragment: "1+1:12.2";
    astaps.loot.world_gems_cryo_anemo_rarity_2to3_copies3: "${world_gems_roll_count(level)}";
    astaps.loot.artifacts_berserker_instructor_lucky_dog_3star_1_2substats: "${world_artifacts_pool_1_roll_count(level)}";
    astaps.loot.artifacts_berserker_instructor_4star: "${world_artifacts_pool_2_roll_count(level)}";
}
```

Each category is selected independently, including any inner weighted draws and
additional rolls. A shared pool may retain an earlier reward-profile-derived
name even when referenced by another Boss; the CSS selector identifies the Boss.

The artifact distributions are `artifacts_1_to_2_low`,
`artifacts_1_to_2_high`, `artifacts_1_to_3`, `artifacts_2_to_3`,
`artifacts_3_to_4_low` and `artifacts_3_to_4_high`. The `low` and `high` suffixes indicate lower and higher odds of obtaining
the higher-star rarity; neither chest grade nor level belongs in a subtable.
The last two distributions include four-star artifacts, so calling them
another `2_to_3` pool would misrepresent the source.

Weapon pools follow the same convention: `weapons_1_to_2_low` (20% two-star)
and `weapons_1_to_2_medium` (50% two-star);
`weapons_2_to_3_low` (40% three-star) and `weapons_2_to_3_high`
(75% three-star). These labels describe only the odds within each
pool, not the chest tier.

Loot tiers contain variable contents: character experience items,
talent books, ingredients, recipe items, weapons, enhancement ores and
artifacts. The 12 tables now combine historic combat EXP/talent rewards with puzzle
weapons and enhancement ores, without requiring a combat/puzzle selector.
Location-specific talent pools and Dragonspine Crimson Agate rewards are
not yet reproduced. Four source-backed recipe rewards are assigned in CSS
by chest grade, level and city, rather than emitted from the loot tiers. Those variants require separately verified
selection data. The native artifact item IDs are unchanged. The resource ID names a set
(for example `genshin.artifact.berserker`); each artifact's `slot`, `rarity`
and initial `substats` are separate properties.

## Common rewards belong to CSS

Since Luna I, ordinary Common, Exquisite, Precious and Luxurious chests
all grant **30 Adventure EXP**. CSS assigns it once:

```css
chest[type="normal"] {
    genshin.experience.adventure: 30;
}

chest[type="normal"][grade="common"][level="1~5"] {
    astaps.loot.minimal: 1;
    genshin.currency.mora: 240~360;
    genshin.currency.sigil: 1;
}
```

Mora, Primogems, and regional sigils are likewise defined in CSS only.
Integer counts or inclusive `low~high` ranges are supported for CSS item
quantities; ranges are rolled at grant time. Loot tables use typed CSS
properties instead of a special list field: `astaps.loot.xxxhigh: 1;`
references `astaps.loot.xxxhigh` once, and
`astaps.loot.xxxhigh: 3;` executes it three times. These names must
exist in `loot.yaml`; unknown names and the obsolete `--loot` syntax
are errors. Different loot properties cascade independently: several
tables may be selected together, a more specific value replaces the
execution count for the *same* table, and zero disables that table.
Loot execution counts use the same nonnegative fixed, inclusive range and
percentage-distribution syntax as YAML `count` (for example `1`, `1~3`,
or `1:50,2:30`). Each CSS loot property's execution count is sampled once
per reward grant; each execution then rolls the table independently.
Fixed item quantities retain their own cascade rules: a later fixed
amount overrides an earlier range and vice versa; zero removes an item.

Ordinary Primogem baselines are 2/5/10/10 for Common/Exquisite/Precious/
Luxurious. Older Mondstadt and Liyue areas give 0/2/5/10 except for the
explicit higher-value area exceptions listed in CSS. The regional sigil
placeholder and CSS `area`/`city` selectors now use one
`RewardAreaResolver`. It looks up the full native area ID in
`WorldAreaConfigData`, uses `areaID1` to identify its parent area, and
resolves the city from `CityConfigData.areaIdVec`. Signed negative native
area IDs are valid. When no native city can be resolved, a source-backed
`rewardRegion` can supply the city for both CSS and regional sigils.
Verified native city ownership takes precedence over that source-region
fallback; conflicting native ownership does not select a city.

CSS supports comma-separated alternatives for any attribute, for example
`[area="10,77,8,19"]` and `[type="waypoint,statue"]`.
Each alternative matches the complete attribute value. Region IDs in bundled
CSS retain the Mondstadt-then-Liyue order and are annotated with bilingual
area names verified in the 7.1 reverse audit.

CSS supports inclusive numeric ranges on any attribute, including
`[level="1~5"]`, `[world_level="6~9"]`, and the open-ended
`[level="93~"]`. Range matching uses integer values, without
special-casing attribute names. Grade-only rules define Primogems and
their city/area adjustments, but never loot, Mora, or regional sigils.
Those three rewards require a matching native level. A world chest
must select at least one loot table with a positive execution count;
missing, zero, invalid, or unmatched chest levels fail explicitly
before reward settlement, rather than granting only partial rewards.
There is no highest-tier or lowest-tier fallback.
CSS has no combat/puzzle selector; future selectors belong here, never
in a YAML item table.

## Combined battle and puzzle rewards

Native combat tables supply character experience items and talent books;
native puzzle tables supply weapon candidates and enhancement ores. The
twelve YAML tiers combine those categories. The ore quantity distributions
are copied from the native Mondstadt puzzle tables 52010000–52011100
(the +14 nested roots), including ordinary/fine/mystic ore chances.
Those values are verified for those source rows, not all later regions.

The native `500000`/`500001`/`500002` weapon source references are
not resolvable as actual nested tables in the checked 7.1 export; the
named 1★/2★/3★ YAML weapon candidates and their probabilities remain
**provisional** until a verified weapon source is available.

Four recipes found as fixed native root entries are instead CSS-controlled:
Mondstadt Exquisite at level 21–25 grants Northern Smoked Chicken; Liyue
Exquisite at level 21–25 grants Mora Meat and Stir-Fried Filet; Mondstadt
Precious at level 21–25 grants Mushroom Pizza. They were combined across
battle/puzzle origin by design, so every matching chest receives the
source-backed recipe even when its original group did not.
These are scoped to native city IDs 1/2 rather than globally applying
recipes to other regions.

Source records remain at `data/ChestDrop.json` and
`data/Server/DropTableExcelConfigData.json`. The artifact resolver uses native `setId`, `equipType`, `rankLevel` and
`appendPropNum` rather than an ID-by-variant name dictionary. Ordinary
generation excludes special append-prop depots with different stat behavior.

## Scene points and other reward paths

The CSS also covers first activation of eligible waypoints, statues
and dungeon entrances (50 Adventure EXP and 5 Primogems by default).
Scene 3 point 6 receives 10 Adventure EXP and no Primogems. In scene 7,
`three_realms_point` grants 20 Light Realm Sigils; its CSS awards
5 Primogems and no Adventure EXP.

Quest-created chests, boss flowers, and monster rewards remain separate
routes. World chest metadata sent to the CSS engine includes `grade` (common,
exquisite, precious, luxurious), `type` (normal, mora, remarkable, when
the source identifies it), `scene`, `gadget`, `group`, `config`, and
any verified native `level`, `area`, and `city`. Imported exploration
points preserve their original chest identity in `SceneGadget.chestType`;
the native ordinary chest prefabs are recognized by their JSON names.
Unknown native chest types are not assigned an invented `normal` type.
Imported ordinary chests also preserve their source-backed `grade` separately,
rather than inferring it from a remapped gadget ID. An unknown native grade
throws with the original gadget ID and JSON name; there is no implicit
`common` grade. Reward failures must not pre-mark a chest as claimed. A missing regional sigil
mapping now fails the entire reward roll instead of omitting the sigil with
a warning, and a failed ground-drop creation is not swallowed as a success.
The bundled CSS currently configures only `type="normal"`:
Mora and Remarkable chests cannot inherit ordinary tier rewards, and their
separate reward tables still need verified native source data.

## Parameterized loot templates

A template is an ordinary YAML list with one integer parameter in its name.
A reference such as `astaps.loot.weekly_gems_cryo_electro_hydro_rarity_roll<21>`
expands the list at load time. Template definitions do not register as
stand-alone loot tables.

```yaml
weekly_gems_cryo_electro_hydro_rarity_roll<chance>:
  - reward:
      - {reward: genshin.avatar_ascension.electro, properties: {rarity: "2:75, 3:20, 4:5"}, weight: 1}
      - {reward: genshin.avatar_ascension.cryo, properties: {rarity: "2:75, 3:20, 4:5"}, weight: 1}
      - {reward: genshin.avatar_ascension.hydro, properties: {rarity: "2:75, 3:20, 4:5"}, weight: 1}
    count: "4+1:${chance}"

example:
  - {reward: astaps.loot.weekly_gems_cryo_electro_hydro_rarity_roll<21>}
  - {reward: genshin.currency.mora, count: 100}
```

The argument `21` substitutes `${chance}`, yielding `4+1:21`.
Integer expressions within `${...}` support integer variables/literals
and `+`/`-` (for example `${rarity - 2}`). Unknown variables,
unsupported operators, integer overflow, empty template lists, and
recursive calls are rejected. No `common`/`variants` mapping or
argument whitelist exists. A template invocation expands to a nested
loot pool; an outer `count` rerolls it independently.

The former artifact rarity template is split into two ordinary tables,
`artifacts_prayers_wisdom_3star` and `artifacts_prayers_wisdom_4star`,
because the available artifact sets differ by rarity.

## Exact level lookup curves

Reward counts that follow an irregular discrete curve belong in
`src/main/resources/rewards/curves.yaml`. The keys are exact levels or
inclusive level ranges. The value is an ordinary quoted loot-count expression.
Ranges may end in `~` to cover all higher levels. The shared
`world_ascension_material_count` curve uses curated integer percentages at
levels 30+ and retains the original values for levels 1–29. The
`weekly_gems_roll_count` curve uses curated integers from level 70 onward. Other shared curves
retain source-accurate decimal odds. Lookup never interpolates.

Curve keys follow `<scope>_<reward>[_pool_n]_<measure>`. The final
`_count` suffix means the value is a complete `LootCount` expression.
Direct item quantities use `_count` (for example,
`world_ascension_material_count`); independent draws from weighted loot
pools use `_roll_count` (for example, `weekly_gems_roll_count`). The
numbered world artifact pools retain separate roll counts because each
pool is evaluated independently. Numbering does not indicate rarity.

```yaml
world_ascension_material_count:
  "1~4": "1+1:33.86"
  "25~29": "1+1:88.01"
  "30~34": "1+1:52"
  "50~54": "1+1:86"
  "60~64": "2+1:3"
  "90~": "2+1:55"
```

The full shared curve has nineteen intervals, from level 1 to 90+, with
curated integer odds for levels 30+. A CSS property selects its value by
the native Boss level:

```css
boss[type="world"][tag="maguu_kenki"][level="50~54"] {
    genshin.avatar_ascension.marionette_core: "${world_ascension_material_count(level)}";
}
```

The `${name(level)}` expression is evaluated when CSS selects the reward,
before the existing count parser rolls it. Both inventory-item declarations and
named loot-pool declarations support this lookup. Unknown curve names, invalid
range definitions, overlaps, and invalid count expressions fail while loading.
If no interval contains the requested level, lookup fails explicitly.
These lookups are separate from the integer substitution performed by
parameterized loot lists in `loot.yaml`; no scripting language is involved.

The same lookup controls the complete level 40–89 weekly Boss gem-roll
curve, combining the former early and 4+1 stages:

```yaml
weekly_gems_roll_count:
  "40~44": "2+1:71.29"
  "45~49": "2+1:92.71"
  "50~54": "3+1:14.16"
  "55~59": "3+1:35.58"
  "60~64": "3+1:75.78"
  "65~69": "3+1:98.37"
  "70~74": "4+1:21"
  "75~79": "4+1:45"
  "80~84": "4+1:69"
  "85~89": "4+1:91"
```

For Andrius at level 75–79:

```css
boss[type="weekly"][tag="andrius"][level="75~79"] {
    astaps.loot.talent_materials_tail_of_boreas_ring_of_boreas_spirit_locket_of_boreas: "1+1:27.5";
    astaps.loot.gems_anemo_geo_pyro_fragment: "1+1:2";
    astaps.loot.weekly_gems_anemo_geo_pyro_fragment_rarity_2to5_first_anemo: "${weekly_gems_roll_count(level)}";
}
```

The first loot declaration independently rerolls a three-star fragment pool.
The second declaration independently rerolls a weighted element/rarity pool
four times, with a fifth roll at the exact probability returned by the curve.
The weighted pool contains no Boss level or bonus count. Twenty-seven redundant
4+1 tables were replaced by fourteen shared weighted pools. The twelve
4+1 tables whose first reward uses an inline elemental-family selector remain
unchanged: they cannot be lifted into a CSS item declaration without losing
their element selection properties.

The remaining shared curves cover world Boss gems and artifact counts:

- `world_gems_roll_count`: level 40+ weighted gem-roll counts.
- `world_artifacts_pool_1_roll_count` and `world_artifacts_pool_2_roll_count`: independent
  counts for the first and second artifact pools. Their suffixes identify
  execution order, **not** artifact rarity; some Bosses use five-star pools.
The shared `world_ascension_material_count` curve also covers Maguu Kenki,
replacing the duplicate `marionette_core` definition. Its level-30+ odds
follow the user-specified integer values; earlier source values remain as-is.
The optional 100+ `3+1:25` alternative remains a separate fixed CSS value.

Together these changes replace 178 repeated world/weekly loot tables with 21
weighted choice pools, migrate 523 Boss CSS loot references to independent
curved selections, and apply the single shared material curve to 743 item declarations.
Each independent pool receives its own `count` evaluation. Tables containing
single-family inner rarity rolls or inline elemental selectors remain in YAML
until an exact equivalent can be expressed without changing copy-versus-roll
semantics.

## YAML semantics

Named Loot tables must contain at least two entries. A table containing
only one family reward is redundant: inline its entry as a nested reward
mapping, preserving the boundary between quantity and independent rolls:

```yaml
# Two copies of the same randomly selected gem grade.
- {reward: genshin.avatar_ascension.electro, properties: {rarity: "2:75, 3:20, 4:5"}, count: 2}

# Independently select a gem grade twice.
- reward: {reward: genshin.avatar_ascension.electro, properties: {rarity: "2:75, 3:20, 4:5"}}
  count: 2

# Two independent grade selections, three copies per selection,
# plus one more grade selection with 17.602% probability.
- reward: {reward: genshin.avatar_ascension.electro, properties: {rarity: "2:75, 3:20, 4:5"}, count: 3}
  count: "2+1:17.602"
```

A direct `count` sets the quantity of the chosen item (or gem grade).
The `count` outside a nested `reward` mapping repeats the whole
selection process. Do not multiply these counts together into a direct
reward count: that would merge independent random choices.

Near-identical native gem rarity proportions are normalized by profile:
`2:79, 3:21`, `2:75, 3:20, 4:5`, or
`2:74.5, 3:20, 4:5, 5:0.5`. These intentionally round the
per-rarity probabilities by small amounts; all percentages still total 100.

All unweighted entries in one list execute; a fully weighted list chooses
one entry by relative weight. Mixed weighted and unweighted entries are
invalid. `count` defaults to one and supports fixed values, ranges,
decimal percentage distributions, and optional bonus executions (e.g.
`count: "2+1:55.56"` means two executions plus one more with 55.56% chance).
A two-way selection between `null` and a single reward is an optional
count: `{reward: genshin.material.dream_solvent, count: "1:33"}`
grants one Dream Solvent with a 33% chance. It works with nested pools,
too: `{reward: *midlander_weapon_billets, count: "1:12"}` selects
one billet with a 12% chance, preserving the inner pool's distribution.

Bare fractional expected counts are forbidden in user YAML; native `2.5556`
is converted to `2+1:55.56` at export time. A nested list is rolled
independently for each execution specified by its outer count.

Qualified `genshin.item.<name>` identities resolve other items to game
item IDs. Artifacts use `genshin.artifact.<set>` plus an optional `properties`
mapping:

```yaml
- reward: genshin.artifact.berserker
  properties: {slot: goblet, rarity: 3, substats: "1:80,2:20"}
- reward: genshin.artifact.adventurer
  properties: {slot: "1~5", rarity: 1}
```

A generic elemental ascension-gem reward can specify the element distribution and a fixed
native rarity together. For example:

```yaml
- {reward: genshin.avatar_ascension, properties: {element: "electro:33.33, cryo:33.33, hydro:33.34", rarity: 3}, count: "1+1:2"}
```

`element` lists exact percentages summing to 100; `rarity` is an integer from
2 to 5. Each awarded copy independently selects its element, including bonus
copies. This preserves the independent rolls of the former
`gems_cryo_electro_hydro_fragment` weighted pool. Invalid elements, duplicate
elements, missing native items, and invalid percentages are rejected.

Gem and talent-book *families* may also use `properties.rarity` to choose
one native 2–5★ gem or one native 2–4★ talent book. For a guaranteed tier,
write an integer such as `properties: {rarity: 2}`. For a random tier,
use a quoted probability distribution such as `rarity: "2:75, 3:20, 4:5"`.
The values after each `:` are percentages with at most two decimal places
and no trailing zeros; they must add to `100`.
A family's grade is sampled once per entry execution. An entry with
`count: 3` gives three items of the selected grade; repeating a nested
reward mapping or pool samples that grade afresh on each execution. The top-level
`weight` controls selection among sibling families:

```yaml
- {reward: genshin.avatar_ascension.electro, properties: {rarity: "2:74.63, 3:19.9, 4:4.97, 5:0.5"}, weight: 1}
- {reward: genshin.talent_up.freedom, properties: {rarity: "2:50, 3:30, 4:20"}, weight: 1}
```

For example, a formerly weighted subpool of 2★/3★/4★ gems with
weights 5697/1519/380 and fixed count 3 is written:

```yaml
- {reward: genshin.avatar_ascension.electro, properties: {rarity: "2:75, 3:20, 4:5"}, count: 3, weight: 1}
```

The bundles use elemental aliases and canonical talent-book subject paths.
A guaranteed tier uses `rarity: 2`, `rarity: 3`, etc., with no probability
string. The older `rarity: "2:100"` form remains accepted for compatibility.
Other talent advancement materials (such as weekly boss drops) are ordinary items.

Slots 1–5 mean flower, plume, sands, goblet, circlet. A full five-slot
weighted subtable can be inlined as a single artifact with
`properties: {slot: "1~5", rarity: 4, substats: 2}`, preserving
uniform slot probability and the parent's selection weight. Likewise,
two identical artifact-set choices weighted 33:17 for different initial
substat counts can use `substats: "1:66, 2:34"` on each artifact
entry. This preserves the joint artifact-set and substat probabilities
without an extra weighted nesting level. Omitted slot and rarity
choose uniformly from available ordinary variants. Missing `substats` chooses
uniformly from the usual valid counts per rank (1★ 0, 2★ 0–1, 3★ 1–2,
4★ 2–3, 5★ 3–4). This is a project policy, not a confirmed 7.1 drop-rate
distribution; specify `substats: "1:80,2:20"` to define exact odds.
Each artifact gets an independent roll, even when `count` is greater than one.
All resulting counts and weighted-choice weights are integers, but
probabilities may be decimal percentages. Use an explicit percentage
distribution such as `count: "1:52"` instead of decimal count `0.52`.
The remainder of an explicitly specified count distribution implicitly means zero:
`count: "1:20"` means one item 20% of the time and none 80% of the time.
An explicit `0:80` is therefore unnecessary.
Relative probabilities in the original weighted loot lists are preserved.
Loot table names describe only reward content and may identify reward
type (`world` or `weekly`); they do not contain Boss tags or native
levels. CSS holds those selectors and references the appropriate payout
profile. Identical payout profiles share one table even when selected by
different Bosses or levels. YAML anchors remain for reusable content pools.

## Semantic Genshin resource namespaces

The semantic identifiers are typed, not interchangeable:
- `genshin.currency.mora` identifies an ordinary inventory item or virtual currency.
- `genshin.weapon.dull_blade` identifies a native weapon ItemData row. Weapons
  no longer have a second `genshin.material.dull_blade` identity.
- `genshin.artifact.berserker` identifies a native artifact **set**, not one
  random piece; `properties` selects the slot/rarity/substats for loot rolls.
- `genshin.avatar.amber` identifies native AvatarData, which is not an item.
- `genshin.recipe.mora_meat` identifies a CookRecipeData cooking recipe,
  not the collectible recipe scroll `genshin.material.recipe_mora_meat`.

The `give` command resolves these domains separately: items and weapons go
through inventory, avatars through the avatar system, recipes unlock the
cooking recipe, and artifact sets grant their native five piece slots. There
are no fabricated aliases for names missing from the native source. Unnamed
recipes and ambiguous names fail resolution; input is never silently
converted into another kind. In loot.yaml and rewards.css only inventory
items/weapons and artifact selectors are legal rewards; recipe and avatar
resources require their distinct grant paths.

No numeric source IDs are used in the bundled loot configuration.

Full 7.1 gameplay validation is still required before upstream submission.

### Compact native material identities

For `give` and `loot.yaml`, use exactly one qualified identity per native
resource. Categories come from native `ItemType`/`MaterialType`, not a
catch-all `item` prefix. The common experience items are:

- `genshin.avatar_up.heros_wit`, `genshin.avatar_up.adventurers_experience`, `genshin.avatar_up.wanderers_advice`
- `genshin.weapon_up.enhancement_ore`, `genshin.weapon_up.fine_ore`, `genshin.weapon_up.mystic_ore`
- `genshin.artifact_up.unction`, `genshin.artifact_up.essence`
- `genshin.currency.resin` is the Original Resin balance (native ID 106), not the consumable Fragile Resin.

The resource type identifies the item's role, independent of which boss or
chest drops it. Level-up consumables use `avatar_up`,
`weapon_up`, `artifact_up`, and `talent_up`.
Promotion ingredients use `avatar_ascension` and `weapon_ascension`.
These are six distinct reward types; the native gameplay cost tables decide
exclusive use, while fixed native EXP-item types identify level-up materials.
Items used by multiple advancement paths keep a single common identity
(`genshin.material.*`), and verified enemy drops use
`genshin.monster_drop.*` even if a particular drop is also consumed
for character, weapon, or talent development. Weapon billets use
`genshin.weapon_crafting.*`. Item IDs are never duplicated under
separate use-specific aliases.

Previous `genshin.material.*` aliases for these items and the `genshin.resource`
category are intentionally unsupported. Adventure EXP is
`genshin.experience.adventure`, while Hero's Wit is an `avatar_up` item.

### Regional sigil IDs

All nine native sigil item identities use one hierarchical currency series:
`genshin.currency.sigil.anemo`, `genshin.currency.sigil.geo`,
`genshin.currency.sigil.electro`, `genshin.currency.sigil.dendro`,
`genshin.currency.sigil.hydro`, `genshin.currency.sigil.pyro`,
`genshin.currency.sigil.cryo`, `genshin.currency.sigil.lunar`, and
`genshin.currency.sigil.light_realm`.

CSS encodes the sigil qualifier with a hyphen, e.g.
`genshin.currency.sigil.anemo: 1;`. The unqualified
`genshin.currency.sigil` still means the deferred region-derived sigil.
Former `genshin.currency.anemo_sigil`-style names are not aliases.

## Boss reward migration status (7.1)

World Boss-exclusive ascension materials are grouped by native `sourceType = 34`
counts: a shared Lv.1–29 early profile on newer Boss families, the
common Lv.30–99 profile, and a distinct higher-yield Lv.30–99 profile for
Pyro Regisvine and Oceanid. Lv.100+ material quantities stay explicit in
`rewards.css` (`2+1:55.56`, `2+1:81.116`, or `3+1:25`).
Icewind Suite's two independently granted material identities now use CSS
as well. Material quantities retain the fractional precision of native
`DropTableExcelConfigData.json`; the old single approximated curve is gone.


Normal open-world boss flowers still roll the native `ChestDrop.json` ->
`Server/DropTableExcelConfigData.json` chain. They now use one
`WORLD_BOSS` grant receipt per player and flower, recording the
intended items and resin debit before payment. A confirmed payment
cannot be retried blindly if inventory delivery fails. Missing
`drop_tag` can use a source-backed `boss-drop-tags.json` mapping;
unidentified flowers still refuse the claim. Battle Pass bookkeeping
is not allowed to invalidate a completed resin payment.

Weekly/Trounce Dungeons cannot be treated as ordinary 20-resin
material Domains. The pinned 7.1 reverse audit reports 66
`DUNGEON_BOSS` rows without a directly linked `statueDrop` root.
There are separate `ChestDrop.json` entries with native
`sourceType = 35`. The server now considers those roots only when
the dungeon preview's 113xxx boss-exclusive talent material IDs
identify **exactly one** candidate. Missing preview counts do not
erase identity evidence; the preview never supplies granted counts.
Level-60 rows with no exclusive talent materials, unmatched IDs,
ambiguous sources and absent native nodes are not assigned a guessed
reward table.

Weekly boss cost is taken from the dungeon record or the
claiming native flower's `boss_chest.resin`, if it is explicitly
30 or 60. A Boss Dungeon may claim only once per run with a single
ordinary-resin roll. Unidentified costs and unsupported payment
multipliers fail before charging. The implementation has not yet
verified the game's cross-boss three-discount weekly counter or
all Boss Dungeon-to-native-source joins. Do not treat this candidate
match as 7.1 drop-rate verification or complete playable coverage.

Boss candidate drop graphs are now bundled in `loot.yaml` and mapped in
`rewards.css`. The 200 weekly payout profiles no longer repeat their
common Dream Solvent and billet entries; the 10 tagged weekly Boss families
share two extras pools. Eight shared artifact packages factor identical
distributions out of 190 weekly payout profiles across the level 5+ brackets.
The remaining 1,109 complete world/weekly payout tables still contain
level-dependent drop probabilities and overly long names; a more extensive
content-based decomposition is outstanding. Four additional shared world
five-star pools remove repeated rates from 144 world payout tables.
Seven shared basic gem packages remove 630 identical item rows from 168
world payout tables.
No new native drop rates are claimed. CSS declares shared fixed Boss amounts by type and native
level bracket without requiring a `tag` attribute; individual tag/level
selectors choose their corresponding named loot pool. CSS Boss tags use
English semantic identifiers (for example `electro_hypostasis`) and
each Boss family has a bilingual source-name comment. Original Chinese
native `ChestDrop.json` tags are mapped to those CSS identifiers at
the reward-selection boundary; the native drop lookup retains the source
string for the existing fallback path. Identifying a valid Boss tag and
handling missing loot-table coverage remain Java-side concerns. Matching configuration alone does not establish that a
payout path is authoritative or that all 7.1 reward probabilities are verified.
World-boss and weekly-boss end-to-end gameplay validation is still required
before upstream submission.
