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

## Regional sigil alias

`genshin.item.sigil` is an item reference resolved at reward-grant time,
not a CSS selector field. For example:

```css
chest[kind="exquisite"] { --genshin-item-sigil: 2; }
```

```yaml
seals:
  - {reward: genshin.item.sigil, count: 2}
```

Both forms use the same resolver. Original scene chests use their native
`SceneGadget.area_id`. That area must match a WorldAreaConfig record in
the active scene and exactly one CityConfig record; only nation city IDs
1–9 have a supported sigil mapping. City IDs 99+ are excluded, because
special scene areas such as the Golden Apple Archipelago and Enkanomiya
share other nations' city icons without sharing their regional rewards.

Generated world chests use source provenance: `region` from imported
`region_explore_points.json` and the explicitly named Nod-Krai or
Snezhnaya point source files. Their region is not guessed using
coordinates, synthetic group ranges, translated drop tags, or the
player's current location. Unknown, missing, or ambiguous locations cause a warning and omit only
the regional sigil; other rewards are still granted. No sigil is guessed.

Direct loot-table execution without a reward context cannot resolve
`sigil`. The four bundled chest presets now include 1, 2, 3, and 4 regional
sigils respectively. These amounts remain approximate. If an area cannot
be resolved (such as an auxiliary event city), the resolver warns and omits only the sigil rather than issuing another
nation's sigil. Those sources need separate verified rules for sigils.

Source audit: `region_explore_points.json` has 1,957 entries, including
891 `fontaine`, 863 `natlan`, and 203 `sumeru36` entries. The resolver
accepts `sumeru36` as Dendro/Sumeru. Nod-Krai has 424 and the standalone
Snezhnaya list has 486 entries; these two datasets have no `region`
property but their file provenance is unambiguous. This validates the
imported data labels, not every original scene script chest.

## Chest level selectors

Scene Lua `SceneGadget.level` is passed to CSS as the original `level`
attribute. ChestLevelSetConfigData lists chest-level thresholds
`1, 6, 11, 16, 21, 26`; ChestDrop.json's Mondstadt/Liyue combat and
puzzle families include matching `minLevel` thresholds. The historical
DropSystem chose the largest `minLevel` not greater than the chest's
`level` for the matching drop tag.

Numeric comparisons are a small extension to the supported CSS subset:

```css
chest[kind="common"][level>=6] { --loot: common_level_6; }
chest[kind="common"][level>=11] { --loot: common_level_11; }
chest[kind="common"][level>=16][level<21] { --loot: common_level_16; }
```

`>`, `>=`, `<`, and `<=` are supported for `level` only.
These are not standard CSS attribute selectors. Each comparison counts as
one attribute for specificity; normal CSS cascade order still applies.
Write overlapping minimum-threshold selectors in ascending order so
that the largest matching bound wins. Missing/non-numeric levels never
match numeric selectors. The existing `kind`-only rules cover such cases.

The six thresholds are **not** proof of 24 distinct item distributions.
In the server ChestDrop.json there are 20 combat and 20 puzzle tags;
only the 16 Mondstadt/Liyue tags have all six thresholds, and many
thresholds within a tag refer to the **same** drop ID. Other nations'
tags use a single level-1 row. Furthermore no verified native field
currently distinguishes combat from puzzle chests. Therefore the
bundled four approximate drop tables remain unchanged until the content
and source selector for each genuinely distinct variant can be proven.
