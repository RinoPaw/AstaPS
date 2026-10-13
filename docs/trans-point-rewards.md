# AstaPS first-unlock rewards

`rewards.toml` is an operator-owned configuration, generated in the AstaPS
working directory alongside `config.json` on first startup. Existing files
are never overwritten. Malformed or unsupported configuration stops startup;
there is no native reward-table fallback.

If root-level `rewards.toml` does not exist, startup first imports a
previous `config/rewards.toml`, or otherwise migrates the older
`config/rewards/trans-point-rewards.toml` format into the `transPoint`
namespace. After successful validation, the migrated source file is deleted.
A second older reward file is removed only when its content matches the
new file after conversion. Empty directories are removed; other files
remain untouched. An existing root-level file is always authoritative.
If no old reward file exists, the bundled template is generated.

## Supported selectors

- `[transPoint."kind=waypoint"]`: ordinary teleport points
- `[transPoint."kind=statue"]`: Statues of The Seven
- `[transPoint."kind=domain"]`: dungeon entrances activated by the player
- `[transPoint."7:*"]`: every eligible point in scene 7
- `[transPoint."3:6"]`: one exact scene/point pair

Priority: exact point > scene > kind. Exactly one table applies; missing reward fields
mean zero, not inherited values. `adventureExp`, `primogems`, and `mora` are
non-negative integers; `items` is an array of `{ id = ..., count = ... }`.

The bundled template is `src/main/resources/rewards.toml`.
The root-level operator-owned `rewards.toml` is gitignored. This intentionally does **not** load
`ExcelBinOutput/TransPointRewardConfigData.json` or look up the old `rewardId`
in `RewardExcelConfigData.json`.

## Native first-unlock boundaries

For waypoints and statues, rewards follow the existing permanent first-unlock state
change. Releasing a quest lock does not pay a second reward. Pre-unlocked points,
portal/unknown point subtypes, and hidden ordinary waypoints are ineligible.

For domains, the client `UnlockTransPointReq` for a native `DungeonEntry`
point is the activation event, including story entrances without
`DungeonEntryExcelConfigData.rewardDataId` (scene 3 points 1 and 20).
Native `rewardDataId` no longer decides eligibility or amounts.
The handbook only lists native handbook entries and re-synchronizes already-unlocked
points; it does not discover or unlock new entrances. A separate persisted
`rewardedDungeonEntries` activation receipt prevents duplicate claims and keeps
older player saves with historical synthetic unlocks safe from repeat rewards.
Hidden and natively pre-unlocked domain points are ineligible.
Entering or re-entering a dungeon never grants discovery rewards.

For existing player accounts, previously activated domain points have no
historical claim receipt from before this implementation. Their first new
activation request may pay once; no retroactive mass award is performed.

Domain completion loot, resin, tree claims, and repeated challenges are unaffected.

## Shared file scope

The top-level `version = 1` belongs to the unified rewards file. The
`transPoint` namespace contains the existing unlock rules. Other reward
families (world chests, dungeon completion rewards, bosses) are **not yet implemented in this
file**. Unknown top-level sections, unknown selectors and invalid values
cause loading to fail rather than silently activating an unused policy.

Chest data remains in `data/ChestDrop.json` and the resource
`Server/DropTableExcelConfigData.json` and
`Server/DropSubTableExcelConfigData.json` graphs. The existing runtime
chest behavior is unchanged by this configuration-path migration.
New chest policy must be designed and tested separately, including
confirmation that ground drops and inventory grants succeed before claiming.
