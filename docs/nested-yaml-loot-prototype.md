# Reward configuration

The runtime reads two bundled files: `src/main/resources/rewards/loot.yaml`
and `src/main/resources/rewards/rewards.css`. There is no editable
`config/rewards` copy.

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
tiers follow in ascending order, then the separate `three_realms_point`
table. There are 36 tables in total.

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
    --genshin-item-adventure_exp: 30;
}

chest[type="normal"][grade="common"][level="1~5"] {
    --astaps-loot-minimal: 1;
    --genshin-item-mora: 240~360;
    --genshin-item-sigil: 1;
}
```

Mora, Primogems, and regional sigils are likewise defined in CSS only.
Integer counts or inclusive `low~high` ranges are supported for CSS item
quantities; ranges are rolled at grant time. Loot tables use typed CSS
properties instead of a special list field: `--astaps-loot-xxxhigh: 1;`
references `astaps.loot.xxxhigh` once, and
`--astaps-loot-xxxhigh: 3;` executes it three times. These names must
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

CSS supports `[level="1~5"]` through `[level="26~"]`, with the upper
bound open in the final range. Grade-only rules define Primogems and
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
`common` grade. Reward failures must not pre-mark a chest as claimed.
The bundled CSS currently configures only `type="normal"`:
Mora and Remarkable chests cannot inherit ordinary tier rewards, and their
separate reward tables still need verified native source data.

## YAML semantics

All unweighted entries in one list execute; a fully weighted list chooses
one entry by relative weight. Mixed weighted and unweighted entries are
invalid. `count` defaults to one and supports fixed values, ranges,
integer percentage distributions. Fractional expected counts are forbidden. A nested
list repeats the entire pool according to the outer count.

Qualified `genshin.item.<name>` identities resolve other items to game
item IDs. Artifacts use `genshin.artifact.<set>` plus an optional `properties`
mapping:

```yaml
- reward: genshin.artifact.berserker
  properties: {slot: goblet, rarity: 3, substats: "1:80,2:20"}
- reward: genshin.artifact.adventurer
  properties: {slot: "1~5", rarity: 1}
```

Slots 1–5 mean flower, plume, sands, goblet, circlet. Omitted slot and rarity
choose uniformly from available ordinary variants. Missing `substats` chooses
uniformly from the usual valid counts per rank (1★ 0, 2★ 0–1, 3★ 1–2,
4★ 2–3, 5★ 3–4). This is a project policy, not a confirmed 7.1 drop-rate
distribution; specify `substats: "1:80,2:20"` to define exact odds.
Each artifact gets an independent roll, even when `count` is greater than one.
All counts and weighted-choice weights are integers. Use an explicit percentage
distribution such as `count: "1:52"` instead of decimal count `0.52`.
The remainder of an explicitly specified count distribution implicitly means zero:
`count: "1:20"` means one item 20% of the time and none 80% of the time.
An explicit `0:80` is therefore unnecessary.
Relative probabilities in the original weighted loot lists are preserved.

No numeric source IDs are used in the bundled loot configuration.

Full 7.1 gameplay validation is still required before upstream submission.
