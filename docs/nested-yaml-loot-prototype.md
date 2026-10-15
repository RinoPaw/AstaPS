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

These twelve short names are registered directly under `astaps.loot`,
for example `astaps.loot.nano` and `astaps.loot.ultra`. YAML also contains supporting subpools and
`three_realms_point`. Those are not additional world-chest tiers.

Loot tiers contain variable contents: character experience items,
talent books, ingredients, recipe items, weapons, enhancement ores and
artifacts. The 12 tables are based on one branch of the historical chest
drop data without combat/puzzle selection. They do not yet reproduce
location-specific book pools, special recipes, or Dragonspine Crimson
Agate rewards correctly. Those variants require separately verified
selection data. The native artifact item IDs are unchanged. Their YAML semantic names now use
`genshin.artifact.<set>.<slot>.<rarity>.<initial_substats>`.

## Common rewards belong to CSS

Since Luna I, ordinary Common, Exquisite, Precious and Luxurious chests
all grant **30 Adventure EXP**. CSS assigns it once:

```css
chest {
    --genshin-item-adventure_exp: 30;
}

chest[kind="common"][level="1~5"] {
    --loot: minimal;
    --genshin-item-mora: 240~360;
    --genshin-item-sigil: 1;
}
```

Mora, Primogems, and regional sigils are likewise defined in CSS only.
Integer counts or inclusive `low~high` ranges are supported for CSS item
quantities; ranges are rolled at grant time. CSS item properties cascade
independently of the complete `--loot` selection. A later fixed amount
overrides an earlier range, and vice versa. A count of zero removes an item.

Ordinary Primogem baselines are 2/5/10/10 for Common/Exquisite/Precious/
Luxurious. Older Mondstadt and Liyue areas give 0/2/5/10 except for the
explicit higher-value area exceptions listed in CSS. The regional sigil
placeholder is resolved using existing authoritative area and scene
metadata at grant time, rather than a guessed translated tag.

CSS supports `[level="1~5"]` through `[level="26~"]`, with the upper
bound open in the final range. Missing or invalid levels fall back
to the general chest-kind rule. CSS has no combat/puzzle selector;
future selectors belong here, never in a YAML item table.

## Weapon and ore investigation

The previous export of `DropTableExcelConfigData` and nested tables
produced empty `weapons` and `enhancement_ore` lists for all twelve
examined root tables. That does **not** mean Genshin 7.1 chests cannot
drop those items. Both are confirmed chest reward categories in public
gameplay documentation, and the repository's old bundled
`src/main/resources/defaults/data/ChestReward.json` contains concrete
examples.

The new YAML weapon and ore entries are **provisional**. Known 1★/2★/3★
weapons and ordinary/fine/mystic enhancement ores have been placed into
sensible tiered pools. Their exact probabilities and counts are not
validated against native Genshin 7.1 reward data. This uncertainty is
intentional and must not be mistaken for confirmed reversed drop rates.

Source records remain at `data/ChestDrop.json` and
`data/Server/DropTableExcelConfigData.json`. The earlier verbose artifact names have been replaced by shorter typed references
that reflect the native `setId`, `equipType`, `rankLevel` and `appendPropNum`.

## Scene points and other reward paths

The CSS also covers first activation of eligible waypoints, statues
and dungeon entrances (50 Adventure EXP and 5 Primogems by default).
Scene 3 point 6 receives 10 Adventure EXP and no Primogems. In scene 7,
`three_realms_point` grants 20 Light Realm Sigils; its CSS awards
5 Primogems and no Adventure EXP.

Quest-created chests, boss flowers, and monster rewards remain separate
routes. World chest metadata sent to the CSS engine includes `kind`,
`scene`, `gadget`, `group`, `config`, and any verified native `level`,
`area`, and `city`.

## YAML semantics

All unweighted entries in one list execute; a fully weighted list chooses
one entry by relative weight. Mixed weighted and unweighted entries are
invalid. `count` defaults to one and supports fixed values, ranges,
fractional expectations and native percentage distributions. A nested
list repeats the entire pool according to the outer count.

Qualified `genshin.item.<name>` identities resolve materials and weapons to game
item IDs. Artifacts use `genshin.artifact.<set>.<slot>.<rarity>.<initial_substats>`,
for example `genshin.artifact.berserker.goblet.3.1` and `genshin.artifact.adventurer.goblet.1.0`.
The final number is the native *initial* substat count (including zero), not
the count after upgrades. The artifact reference resolves to its unchanged
native item ID. Dotted item IDs and `astaps.loot.<name>` remain unchanged.
No numeric source IDs are used in the bundled loot configuration.

Full 7.1 gameplay validation is still required before upstream submission.
