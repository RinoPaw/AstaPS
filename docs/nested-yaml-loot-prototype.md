# Reward configuration

AstaPS bundles two authoritative reward resources for chests and scene-point activations.
They are read directly from the JAR: `src/main/resources/rewards/loot.yaml`
and `src/main/resources/rewards/rewards.css`. No files are copied to
`config/rewards/`, and any previous copies there are ignored.

`loot.yaml` defines reusable loot content. A table does not inspect chest
type, location, rank, scene, or other spawn data. Four base chest pools
are defined, each with six named level aliases (`light`, `low`, `medium`,
`high`, `xhigh`, `max`). Names register under `astaps.loot.<name>`.

`rewards.css` selects a named loot table and sets fixed quantities. The
sample includes four basic `chest[kind="..."]` rules plus the six
level intervals for each kind. Chest metadata
includes `kind`, `scene`, `gadget`, `group`, `config`, and when
available the source gadget's `level`, plus source `area` and its parent `city` where available.
No calculated regional currency,
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

The separate `trans-point-rewards.toml` has been retired. Scene-point
rewards are defined in the bundled `rewards.css` alongside chest rewards.

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
not independently validated against all 7.1 game records. The historical
sourceType=19 chest drop graph is now also included in `loot.yaml` as
reference data. Region-dependent sigils are resolved at grant time from verified
source metadata. Special chest variants still require verified selection data;
location heuristics do not determine their loot composition.

The server reads the bundled resources after game items load. To change
the defaults, edit `src/main/resources/rewards/` and rebuild the JAR.
An old local `config/rewards/` directory can be deleted; it is no longer read.

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

The source `ChestLevelSetConfigData` defines six thresholds: 1, 6, 11,
16, 21, 26. The historical chest source table uses the highest eligible
`minLevel`. The CSS template now expresses these as inclusive numeric
ranges on the existing `SceneGadget.level` property:

```css
chest[kind="common"][level="1~5"] { --loot: common_chest_light; }
chest[kind="common"][level="6~10"] { --loot: common_chest_low; }
chest[kind="common"][level="11~15"] { --loot: common_chest_medium; }
chest[kind="common"][level="16~20"] { --loot: common_chest_high; }
chest[kind="common"][level="21~25"] { --loot: common_chest_xhigh; }
chest[kind="common"][level="26~"] { --loot: common_chest_max; }
```

The open upper bound `26~` retains the highest eligible tier for
higher levels, matching `minLevel` semantics. Numeric ranges are
an AstaPS extension to ordinary CSS attribute matching, and are only
interpreted for `level`. A missing, zero, or invalid level does not match
these ranges; the general `kind` rule still applies. Exact equality
such as `[level="16"]` still works.

All four chest kinds use the same six interval names. The bundled YAML
declares these names as aliases to each kind's existing base table.
**No 7.1 item distributions for these levels have been verified.**
The aliases intentionally share the same approximate content until
source-specific probabilities are validated; they can be replaced
independently with confirmed data. There are six source thresholds,
so no seventh `ultra` tier is introduced.

The source `ChestDrop.json` has 40 combat/puzzle tags; 16 (Mondstadt
and Liyue) contain all six thresholds, while 24 have only a single
level-1 entry. Repeated thresholds sometimes point to the same drop
ID. Distinguishing combat versus puzzle selection still requires
verified source metadata, not guessed tag strings.

## Historical combat and puzzle loot tables

The bundled `loot.yaml` contains 679 historical `chest_drop_<ID>` tables,
including all 108 sourceType=19 root tables from
`AstaPS-Resource/Server/DropTableExcelConfigData.json` and the 571
reachable subtable dependencies from that file and
`Server/DropSubTableExcelConfigData.json`. The source graph includes 1,843
positive-weight entries. Original table IDs are retained in names:
`astaps.loot.chest_drop_51010000` and
`astaps.loot.chest_drop_52010000` are representative Mondstadt
combat/puzzle roots. The 510/513 and 520/523 families preserve their
separate combat/puzzle drop compositions; additional 530/540/550
families are retained under their original numeric identities.

YAML aliases represent nested source-table references, not string names
and not conditional loot rules. Native `randomType=0` becomes a weighted
choice; `randomType=1` becomes independent item rolls. Native fractional
counts and chance weights are retained, including empty source tables.
The historical pools are research data and **are not selected by the
bundled `rewards.css`**, which remains unchanged. The existing four
active presets and their six level aliases continue to have their
approximate rewards. Fixed item IDs use qualified `genshin.item` names.
Stable, verified keys use descriptive names (for example,
`genshin.item.adventure_exp`); the old source items with no reliable
name use `genshin.item.id_55311`-style aliases, validated against
the real item registry at load time. `count: 1` is omitted and exclusive
weights are scaled to small relative values without changing odds.

This archive preserves the source graph itself. The
`ChestDrop.json` tag/minLevel-to-root selection mapping is not encoded
in the loot tables, so the historical roots must not be assumed to
correspond one-to-one with the six CSS level buckets. No 7.1 gameplay
validation has been performed for the archived item IDs.

## Chest Primogems by native location

Base amounts: common 2, exquisite 5, precious 10, luxurious 10.
The original Mondstadt (city=1) and Liyue (city=2) areas generally
award 0/2/5 for common/exquisite/precious. Source areas 10, 77
(Mondstadt) and 8, 19, 40, 41, 47, 300 (Liyue) explicitly restore
the normal amounts for known later/higher-value world regions.

Native SceneGadget.area_id joins WorldAreaConfigData and CityConfigData
to produce CSS area/city attributes. These are independent of level.
Stormbearer Mountains and Guyun Stone Forest are local exceptions
inside larger parent areas; original chest area_id does not distinguish
them precisely, so they still need native subarea evidence. The bundled reward files do not create or overwrite local `config/rewards` files.

The exceptional top-level area IDs are unique in the 7.1 CityConfigData
ownership lists; area selectors therefore do not need an additional
city filter.
