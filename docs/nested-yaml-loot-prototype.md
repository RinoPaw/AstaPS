# Reward configuration

AstaPS bundles two concise, editable reward files.

`loot.yaml` defines reusable loot content. A table does not inspect chest
type, location, rank, scene, or other spawn data. Four ordinary chest tables
are defined: `common_chest`, `exquisite_chest`, `precious_chest`,
and `luxurious_chest`. Each is registered in the resource namespace as
`astaps.loot.<name>`.

`rewards.css` selects a named loot table and sets fixed quantities. The
supplied sample uses only four `chest[kind="..."]` selectors. The current
runtime exposes `kind`, `scene`, `gadget`, `group`, `config`, and when
available the source gadget's `level`. No calculated regional currency,
translated tag, or derived rank band is provided.

Example:

```css
chest[kind="common"] {
    --loot: common_chest;
    --genshin-item-primogem: 2;
    --genshin-item-adventure_exp: 30;
}
```

A loot entry has `reward`, optional `count`, and optional `weight`.
`reward` can be a numeric or qualified item ID, an English item name,
a nested list, or `null`. `count` defaults to 1. For nested lists it
means independent repetitions. A list with all-null weights executes
all entries; one with all-numeric weights selects one by relative weight.
Mixing weighted and unweighted entries within a list is invalid.
Counts support ranges `1~3` / `1;3`, fractional expectations `2.2`,
percentage `3:20`, and distributions `0:40,1,2:18`.
YAML aliases can reuse preceding lists but cannot create cycles.

In CSS, `--loot: currency, books 2;` executes two independent tables;
`--loot: none` disables them. A subsequent matching `--loot`
declaration replaces the whole list. Fixed item declarations cascade
independently, override table results, and can remove an item with count 0.
Qualified table IDs must be CSS quoted strings.

**Data status:** the sample chest amounts remain approximate and are
not independently validated against all 7.1 game records. The removed
historical table graph remains available from the separate resource
repository for research. Regional seals and special chest variants are
not added without a verified way to select them. Unverified runtime
location heuristics should not determine loot composition.

The server copies these files to `config/rewards` at first start.
Existing files are not silently overwritten. Back up and regenerate
both files if the installation has a previously generated chest schema.

Only ordinary world chests currently use CSS-based reward settlement.
Quest-created chests, boss flowers, monster loot, and waypoint rewards
are separate paths. Runtime testing should follow configuration review.
