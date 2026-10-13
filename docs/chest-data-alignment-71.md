# Chest data alignment (7.1): evidence before reward-policy design

Snapshot: AstaPS upstream `4ae9884dc25703848f01fa274803a39e29dac70f`;
AstaPS-Resource upstream `85b81a691fcf3d9e8e7db50a8febc8f9043bc084`.
The synthetic-data and `ChestDrop.json` counts below describe that **unmodified**
upstream snapshot. This branch fixes duplicate synthetic spawn ownership and maps the existing currency-only pools for 539 synthetic Mora chests. Other reward families are unchanged.

## Inputs and provenance

- Scene scripts: `AstaPS-Resource/Scripts/Scene/`; earlier full-scene audit on
  `RinoPaw/AstaPS-Resource:audit/chest-native-coverage-71`.
- Server tag bridge: `AstaPS/data/ChestDrop.json`.
- Server reward graph: `AstaPS-Resource/Server/DropTableExcelConfigData.json`
  and `DropSubTableExcelConfigData.json`.
- Synthetic marker sets: `AstaPS/data/{nodkrai,snezhnaya,region}_explore_points.json`.
- Authoritative 7.1 **rates are not established** by these sources. See
  [drop-table provenance](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/analyses/progression/economy/7.1-drop-table-provenance.md):
  the present `Server/DropTable*` data were also in 6.5 LunaGC resources.

`ChestDrop.json` is a **server-owned lookup**, not proof of a native
7.1 field. Existence of a root ID does not prove that its children,
probabilities, item families, or live payout are correct.

## Full ordinary scene population (previous pinned audit)

14,084 ordinary chest configurations identified in 41,110 scene Lua files;
43 Boss chests excluded. Of the ordinary chests:

- 9,020 use a `drop_tag`, 1,339 use an explicit drop ID, 3,725 lack both.
- 4,586 have structurally complete reward graphs; 5,280 partial graphs;
  479 empty and 14 missing roots; 3,725 have no reward metadata.
- 109 distinct scene `drop_tag` strings are used, all defined in the
  215-tag server bridge. This validates *lookup keys*, not payouts.

These figures come from the earlier pinned scan and require re-running if the
scene scripts or server reward tables change.

## Current server reward bridge

| Measure | Value |
| --- | ---: |
| `ChestDrop.json` rows | 1,771 |
| Unique tag strings | 215 |
| Non-Boss tags | 156 |
| Boss tags | 59 |
| Rows whose drop IDs lack both resource root and sub-root | 625 |
| Different missing-root tags | 25 (Boss families) |

The 625 missing-root rows are the 25 added Boss families, each with 25
level-specific entries. This is a **separate** Boss reward problem.
All other rows have defined entry roots, but many referenced graphs are not
known to be complete. Do not fill the 625 gaps with guessed item pools.

## Full-scene explicit-ID exceptions

The full scene audit identified **493** explicit-ID chests whose referenced
root cannot produce an item in the currently shipped tables. These are
different cases and must not be mass-remapped.

- **479 chests** explicitly reference `1000100`. That root exists, but has
  `sourceType = 0` and an empty `dropVec`. Monster scripts also use
  `drop_id = 1000100`; it is **plausibly a no-drop sentinel**, not proof
  that 479 chest reward tables were lost. Some such chests advance Lua
  `ChestOpened` triggers (example:
  [scene 50079 group 250079007](https://github.com/MeChen618/AstaPS-Resource/blob/main/Scripts/Scene/50079/scene50079_group250079007.lua)).
  Do not block their interaction or fabricate rewards without determining
  their intended quest progression behavior.
- **14 chests** reference distinct missing IDs **`1050256` through
  `1050269`**, all in scene 3 and ten Lua groups. The records are marked
  `isOneoff = true`, `persistent = true`, `autopick = true`, and
  `drop_count = 1`. They include
  [Deshret rune gadgets](https://github.com/MeChen618/AstaPS-Resource/blob/main/Scripts/Scene/3/scene3_group133307177.lua)
  and
  [other Sumeru gadgets](https://github.com/MeChen618/AstaPS-Resource/blob/main/Scripts/Scene/3/scene3_group133307415.lua).
  The native server drop table has `1050255`, but no `1050256`–`1050269`
  rows. Neighboring `1050235`–`1050252` entries commonly grant unique
  `MATERIAL_QUEST` items, so generic chest loot would lose quest items.
  This is a **hypothesis about content family**, not recovery of the
  missing item IDs.
- Cross-checking `Merprose2/LunaGCR-Resources` (main-table Git blob
  `fcc5f1879349e9228022978078a7220ba4256a07`) and
  `invoker-bot/LunaGC-Resources` (`812bdf7372b58d23887540682b910b1486ad3efa`)
  finds `1000100` empty and all 14 subsequent IDs absent in both
  datasets. LunaGCR adds 98 roots and 100 subnodes relative to AstaPS,
  but its additions contain **none** of these 14 IDs or the 625 Boss
  reward roots. Do not import historical data for these gaps.

New read-only audit:
`tools/audit_chest_scene_exceptions.py`, with the complete per-gadget
evidence emitted using `--output`. This adds evidence but deliberately
does **not** assign new rewards.

## Populated roots with empty descendants

A second, separate source of apparent partial coverage is an **existing
nonempty root** referencing one or more **existing empty subnodes**.

For the 296 non-Boss reward bridge rows (`ChestDrop.json`):
192 rows, corresponding to 108 distinct root IDs, reach 205 different
empty descendant nodes. All affected rows are within the battle/puzzle
(and Dragonspine battle/puzzle/agate) families. For example,
`52040000` has mandatory currency/EXP leaves and subnodes
`52040011` and `52040012` with empty `dropVec`.
The analogous battle root `51040000` has different empty child IDs.
An empty subnode may intentionally represent a no-drop possibility.
These 192 rows are **not equivalent** to 192 missing reward roots,
and should not be overwritten with guessed equipment or probabilities.

`tools/audit_chest_data_alignment.py` now reports the exact empty
descendants and counts, without confusing absent node IDs with terminal
item IDs. Authentic 7.1 item-family and probability validation remains open.

## Runtime roll-path distinction (read-only follow-up)

The old scene coverage script's `partial` label means a reachable branch
was empty or structurally suspect. It does **not** mean every roll yields
no items. In the current `DropSystem.processDrop`, `randomType=0` chooses
one weighted entry; `randomType=1` independently checks each entry on a
10,000-point scale. A guaranteed payable-looking terminal path can therefore
coexist with optional empty subnodes. Conversely, an existing populated root
may still fail to produce an item on some rolls.

`tools/audit_chest_roll_paths.py` now audits **all ordinary scene chest
references** under these current Java roll semantics, separately reporting
empty roots, missing roots, invalid graphs, potentially empty outcomes and
roots with a guaranteed terminal ID path. It also counts chests that have
empty descendants *despite* a guaranteed terminal ID path. This is a
read-only instrument; no tables or runtime reward behavior are changed.

Run from the server checkout with the matching resource tree:

```powershell
python -m tools.audit_chest_roll_paths --server . --resources resources --output chest-roll-paths.json
python -m unittest discover -s tests -p test_chest_roll_paths.py -v
```

**Scope limit:** The new local roll-path audit now cross-checks terminal
IDs with all five 7.1 ItemData resources. This updated tool has **not**
yet been run on the entire scene checkout, so its full-scene refined
classification remains pending. The prior successful resource CI scan
below independently established terminal-path guarantees but did not
cross-check every explicit-ID leaf against current 7.1 items or inventory
acceptance. No uncertain rewards were changed.

## Confirmed 7.1 static item and scene checks (2026-10-13)

The complete source-pinned scan was already performed by the resource fork
[successful workflow run 38342651934](https://github.com/RinoPaw/AstaPS-Resource/actions/runs/38342651934)
(41,110 Lua files, 14,084 ordinary chest configurations). The workflow's
`GUARANTEE_SCOPE scene_all` output is:

| Category | Scene chests | Guaranteed terminal-ID path |
| --- | ---: | ---: |
| Structurally complete | 4,586 | 4,586 |
| Structurally partial | 5,280 | **5,280** |
| Explicit empty root `1000100` | 479 | 0 |
| Missing explicit root `1050256..1050269` | 14 | 0 |
| No reward metadata | 3,725 | untested |
| **All ordinary** | **14,084** | **9,866** |

Here "guaranteed" means a terminal drop ID under the existing Java
`randomType` semantics, **not a confirmed inventory payout**. The
reference-coverage count among 10,359 scene chests with reward metadata is
9,866 (95.24%). The older `partial` classification is **not** a
failure count. No new CI was run for this investigation.

The separate read-only analysis of the **current pinned resource tables**
(13,823 main nodes, 1,885 subnodes) and all **296 non-Boss** server tag
configuration rows finds 212 distinct roots. All 296 rows guarantee a
terminal-ID path; 192 have empty descendants yet still guarantee a
terminal-ID path. Their graph reaches 228 distinct terminal IDs, **all 228
present** in the 7.1 `Material`, `Weapon`, `Reliquary`,
`HomeWorldFurniture` or `TpsWeapon` ItemData source JSONs (17,672
distinct defined item IDs total). This proves *ID existence* for the
tag-bridge scope, not native reward quantities/rates or grant acceptance.
The full scene explicit-ID population has not yet received the same
terminal item-family validation. The audit instrument now includes
this 7.1 item-ID cross-check to make re-runs reproducible.

### Fourteen absent explicit-ID roots: source classification

All 14 appear in scene 3, marked `isOneoff`, `persistent` and
`autopick`, and use `drop_count = 1`. The source objects are actual
GadgetExcel `Chest` types, including special pickup objects,
**not all ordinary treasure-box prefabs**:

| Missing IDs | Objects | Lua group(s) | Evidence |
| --- | --- | --- | --- |
| `1050256..1050262` | 7 `70217021` objects (`SceneObj_Area_Xm_Property_Prop_Qzft_Chest_01`) | `133307306`, `133307410`, `133307411`, `133307412`, `133307408`, `133307414`, `133307409` | Explicit Chinese comment: `注意自己配一下对应发条的dropID`; chest-open state triggers operate the groups |
| `1050263..1050267` | 5 `70211001` chest objects | `133307415` | Five named objects in Lua: 太阳船、显圣厅、阿如村、赤王之心、流沙遗迹 |
| `1050268` | `70330368`, Deshret rune chest type | `133314164` | Explicit source comment `dropID没有` |
| `1050269` | `70330386`, Deshret rune chest type | `133307177` | Same group has neighboring populated `1050235`/`1050236` references |

The previous successful full-scene scan also shows that the **3,725
no-metadata chest configurations** all have both `isOneoff=false` and
`persistent=false` in their parsed Lua declarations. Of these, **3,636**
are in scene 3, **83** are in scene 101, and **6** in other scenes. They
must be audited as a distinct implicit/default-source population:
missing explicit reward metadata alone does not prove intended zero loot.
Examples in scene 101 include multiple different GadgetExcel `Chest`
prefabs with no `chest_drop_id` or `drop_tag`.

No replacement item ID or table was inferred. Their scripts and prefab
types are higher-confidence evidence for *quest/puzzle-specific behavior*
than generic world-chest tier rewards. Existing runtime world-chest
fallback remains a separate gameplay issue to address with verified
payout and quest-state semantics.

## Synthetic markers: mapping is not native proof

There are 2,038 synthetic chest markers, none with an explicit reward ID.
The existing spawn helpers derive strings such as `解谜中级枫丹`,
even when the server tag bridge has no such string.

| Region | Synthetic chests | Existing tag lookup | After *candidate* Mora mapping | Still unknown |
| --- | ---: | ---: | ---: | ---: |
| Nod-Krai | 306 | 0 | 0 | 306 |
| Snezhnaya | 346 | 0 | 0 | 346 |
| Fontaine | 562 | 0 | 265 | 297 |
| Natlan | 657 | 0 | 203 | 454 |
| Sumeru | 167 | 167 | 167 | 0 |
| **Total** | **2,038** | **167** | **635** | **1,403** |

The 539 `chest_mora` markers in Sumeru (71), Fontaine (265), and Natlan
(203) now **reference** the existing `摩拉石箱<region>` tag backed by root
`8100300`, `8100400`, or `8100500`. Fontaine/Natlan add 468
matched labels; Sumeru fixes a classification mismatch in 71 already-matched
markers. The separate dedicated payout route in `GadgetChest` accepts
**only** matching synthetic Mora sources, skips the tier-based
`WorldChestLootHelper`, and leaves the chest open on missing/failed
tag/pool grants. This is the only ordinary-world chest family whose payout
route is changed in this branch. The existing unrelated chest payout
behavior remains in place until further data alignment.

The matching roots currently contain only Mora item `202`, with
`randomType=0`, `fallToGround=false`, and weighted quantities
`10000:6500`, `15000:2500`, `20000:1000` (weight total 10,000).
The new audit verifies this leaf shape and that the tag points to the
corresponding root. These are **configured weights**, not proven official
7.1-native rates. A valid lookup or a matching root is not itself proof
of a successful in-game payout.

Of 635 lookups after the Mora mapping, **539 have the explicit scoped
native payout route**. The other 96 are Sumeru exquisite lookups; they
still run through the upstream world-chest fallback. The unmatched
1,403 remain unresolved.

There are also 56 `chest_remarkable` markers (21 Nod-Krai, 35 Snezhnaya)
mapped to the same `解谜超级` tier as luxurious chests. Furnishing
blueprint rewards must be established separately, not guessed.

## Synthetic ownership correction in this branch

The upstream `NodKraiExploreSpawnHelper` and
`SnezhnayaExploreSpawnHelper` **both** load
`snezhnaya_explore_points.json` and run from `Scene`. All 486 records
(346 chest, 140 oculus) are within both effective spawn bands. Their
synthetic group bases (`910700000` and `910800000`) and claim files differ,
so separate claims can generate duplicate chests.

This branch removes the duplicate Snezhnaya file load from the Nod-Krai
generator; SnezhnayaExploreSpawnHelper remains the sole source. The new
owner merges eligible chest claims from the previous
`data/nodkrai_explore_claimed.json`, and corresponding guard timers from
`data/nodkrai_guard_respawn.json`, into its own state files. The migration
accepts only chest IDs that exist in the Snezhnaya synthetic dataset,
preserves newer existing guard timestamps, and does not erase old files.
It does not prove the marker coordinates or rewards themselves are native.

## Reproduction

From the AstaPS checkout with the 7.1 resource checkout mounted at
`resources/`:

```powershell
python tools/audit_chest_data_alignment.py --server . --resources resources --output chest-alignment.json
python tools/audit_chest_scene_exceptions.py --resources resources --output chest-scene-exceptions.json
python -m unittest discover -s tests -p test_chest_data_alignment.py -v
```

The script performs read-only cross-file checks and outputs JSON. It tracks
current source ownership as well as the *hypothetical* Mora mapping; it never
copies candidate rewards into active game data.

## Remaining work before unified rewards.toml

1. Reproduce the complete scene reward graph scan on the exact target commit,
   including item-family checks for partial, empty, and missing roots.
2. Establish verifiable 7.1 reward data for 1,403 unmatched synthetic chests.
   First determine which synthetic markers correspond to *actual* game chests.
3. Verify the three now-bound `chest_mora` pools against 7.1-native evidence;\n   investigate the 56 remarkable chests' blueprint rewards separately.
4. Check Boss pool gaps separately; never count them as ordinary chests.
5. Require a verified successful payout before recording a claim. The upstream
   hardcoded world-chest fallback is not a valid authority for new data.

The scoped `handleChestDrop` validation now rejects missing or empty
roots, invalid rolled item IDs/counts and rejected direct inventory
grants. The ground-drop path still has weaker confirmation; only the
currency-only roots used here are direct-grant (`fallToGround=false`).

Only after the inventory and source-of-truth checks are reconciled should
`rewards.toml` receive a schema. No `rewards.toml` was created here.
