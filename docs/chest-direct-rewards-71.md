# Chest fixed rewards: evidence and 7.1 scope

## Five exploration chest types

Genshin has five standard types: common, exquisite, precious, luxurious and
remarkable. The shipped 7.1 `ExcelBinOutput/GadgetExcelConfigData.json`
contains **309** gadget definitions with `type = Chest`, but this is NOT
a list of 309 ordinary treasure-box types.

A direct scan of these 309 GadgetExcel rows gives:
- 36 gadget names containing `Lv1`, 32 `Lv2`, 9 `Lv3`,
  37 `Lv4` and 25 `Lv5` (139 total).
- 24 named `Relic`, including notable furnishing/remarkable-family variants.
- 23 names containing `SearchPoint` (investigation interactions).
- 123 others, including special quest objects, events and prefabs.

The naming convention is **not** a validated one-to-one mapping between
`Lv1..Lv5` and the five player-visible chest types. Ordinary source tags
have four grades (`低级`, `中级`, `高级`, `超级`);
remarkable chests have separate blueprint mechanics.

## Data added to rewards.toml

`[chest."kind=common"]`, `kind=exquisite`, `kind=precious`,
`kind=luxurious`, and `kind=remarkable` contain **only direct reward
amounts**, no source ID, tag, routing mode, level, drop probability or
other control fields.

The five 7.1 standard reward entries are **staged configuration data**,
not yet read by ordinary `GadgetChest` payout. The values in the template
are the regular higher-Primogem-area base values:

| Kind | Adventure EXP | Primogems |
| --- | ---: | ---: |
| Common | 30 | 2 |
| Exquisite | 30 | 5 |
| Precious | 30 | 10 |
| Luxurious | 30 | 10 |
| Remarkable | 30 | 5 |

Primary background:
- https://genshin-impact.fandom.com/wiki/Chest
- https://genshin-impact.fandom.com/wiki/Remarkable_Chest
- https://genshin-impact.fandom.com/wiki/Adventure_EXP

**Exceptions are real and must be identified before enabling payouts:**
- Most standard Mondstadt/Liyue common, exquisite and precious chests give
  0, 2 and 5 Primogems respectively. Certain higher-level areas are
  exceptions to that older-region rule.
- Remarkable chests on Tsurumi Island grant no Primogems; elsewhere the
  common reward is 5. Each blueprint is a chest-specific item, never
  replaceable with one fixed item for the entire kind.
- Shrines of Depths give 40 or 80 Primogems, depending on release region;
  those chests also have 90 Adventure EXP.
- Primal Obelisk chests have a special 20-Primogem / 150-Adventure-EXP
  payout, no sigils, and must not inherit ordinary precious chest values.
- Some scripted quests, limited events and hand-placed chests have special
  item sets or independent reward actions.

`mora`, sigils, furnishing blueprints, artifact and weapon items have NOT
been guessed into these fixed default tables. Their native quantities may be
variable, region-dependent or object-specific.

## Native reward integration must avoid double grants

The current `data/ChestDrop.json` bridge uses ordinary four-grade tags,
for example `战斗低级蒙德` -> root `51010000`, whose server drop
`DropTableExcelConfigData.json` contains both fixed item IDs and random
subnodes. In particular, fixed items include Adventure EXP (102), Primogems
(201), Mora (202) and regional sigils (301..308).

The main/child drop tables currently originate from older LunaGC resource
data, and cannot prove authoritative 7.1 amounts. The shipped ordinary chest
fallback in `WorldChestLootHelper` also uses unsupported hardcoded rates.

Before consuming the new `chest` tables at runtime:
1. Resolve *true* chest family and exceptional conditions from the actual
   scene/prefab/source data, not only `GadgetExcel.type = Chest`.
2. Split direct amounts from random native items so IDs 102, 201 etc
   cannot be granted twice. Keep quest items, per-chest blueprints and
   other native drops.
3. Check inventory/ground grant success before marking a chest claimed,
   and keep quest/puzzle scripts functional when a chest intentionally
   grants no regular loot.
4. Audit the 14 absent roots `1050256..1050269`, the 479
   `1000100` empty references and the 3,725 untagged/ID-less gadgets
   separately. Do not populate quest-specific IDs from a tier default.

The previously completed source-pinned full-scene audit is documented in
`audit/chest-data-alignment-71`. This direct-reward config phase changes
no `GadgetChest` or `DropSystem` payout behavior.
