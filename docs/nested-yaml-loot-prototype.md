# Reward configuration

AstaPS bundles two editable reward files for chests and scene-point activations.

`loot.yaml` defines reusable loot content. A table does not inspect chest
type, location, rank, scene, or other spawn data. Four ordinary chest tables
are defined: `common_chest`, `exquisite_chest`, `precious_chest`,
and `luxurious_chest`. Each is registered in the resource namespace as
`astaps.loot.<name>`.

`rewards.css` selects a named loot table and sets fixed quantities. The
sample includes four `chest[kind="..."]` selectors. Chest metadata
includes `kind`, `scene`, `gadget`, `group`, `config`, and when
available the source gadget's `level`. No calculated regional currency,
translated tag, or derived rank band is provided.

Eligible scene-point activations use `transpoint` selectors. The server
classifies points as `waypoint`, `statue`, or `domain` from game data;
CSS receives `kind`, `scene`, and `point`. The default first activation
gives 50 Adventure EXP and 5 Primogems for each kind. Scene 3 point 6
gets 10 Adventure EXP and no Primogems. Scene 7 gives 5 Primogems and
20 units of item 101692 via `three_realms_point` in `loot.yaml`, and
no Adventure EXP. These amounts preserve the existing TOML defaults.

Only genuine first unlocks grant waypoint/statue rewards. Dungeon entrances
keep their separate first-activation receipt because map synchronization
can reveal them before manual activation. The eligibility check still
excludes hidden points, portals, and unrelated point types.

The separate `trans-point-rewards.toml` has been retired. Existing
`config/rewards/rewards.css` files are not overwritten automatically:
add these selectors or regenerate the file to apply them.

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

Ordinary world chests and eligible scene-point activations now use CSS
reward selection. Quest-created chests, boss flowers, and monster loot
remain separate paths. Runtime testing should follow configuration review.
