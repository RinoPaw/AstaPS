# Chest data alignment (7.1): evidence before reward-policy design

Snapshot: AstaPS upstream `4ae9884dc25703848f01fa274803a39e29dac70f`;
AstaPS-Resource upstream `85b81a691fcf3d9e8e7db50a8febc8f9043bc084`.
The synthetic-data and `ChestDrop.json` counts below describe that **unmodified**
upstream snapshot. This branch separately fixes one duplicate spawn owner.

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
(203) can **reference** existing `摩拉石箱<region>` tags backed by reward
roots `8100300`, `8100400`, and `8100500`. For Fontaine/Natlan, this
adds 468 lookups; for Sumeru, it corrects a category mismatch in 71 already
matched markers. **This candidate change is not applied on this branch**:
the upstream `GadgetChest` still uses a hardcoded fallback for ordinary
world chests, bypassing the native tag route. Changing a tag alone would
not establish that the player received Mora-only loot.

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
3. Validate `chest_mora` roots and 56 remarkable chests against their
   actual 7.1 item families, then make deliberate data/runtime changes.
4. Check Boss pool gaps separately; never count them as ordinary chests.
5. Require a verified successful payout before recording a claim. The upstream
   hardcoded world-chest fallback is not a valid authority for new data.

Only after the inventory and source-of-truth checks are reconciled should
`rewards.toml` receive a schema. No `rewards.toml` was created here.
