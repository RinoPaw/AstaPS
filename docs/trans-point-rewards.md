# AstaPS first-unlock rewards

The server-owned root-level `rewards.toml` contains both `[transPoint]` and
`[chest]` sections. It is copied once from
`src/main/resources/rewards.toml`. Existing files are never overwritten.
Malformed or unsupported configuration stops startup; native reward tables do
not replace a failing operator file.

The old `config/rewards/trans-point-rewards.toml` is **no longer read**.
Existing operators must manually transfer custom rules to `[transPoint]`
in the new file. No automatic legacy import is performed.

## Supported selectors

- `[transPoint."kind=waypoint"]`: ordinary teleport points
- `[transPoint."kind=statue"]`: Statues of The Seven
- `[transPoint."kind=domain"]`: manually activated dungeon entrances
- `[transPoint."7:*"]`: all eligible points in scene 7
- `[transPoint."3:6"]`: exact scene/point pair

**All matching rules apply from top to bottom.** Each later matching item
amount overrides only that amount; missing fields inherit earlier values.
Explicit zero removes an inherited item. A broad rule written later can
override a more specific rule. For example:

```toml
[transPoint."kind=waypoint"]
adventureExp = 50
primogems = 5

[transPoint."3:6"]
adventureExp = 10
primogems = 0
```

`adventureExp`, `primogems`, and `mora` are non-negative integers.
`items` is an array of `{ id = ..., count = ... }`, with non-negative
counts so an explicit zero can cancel inherited items. Item IDs must be
positive and duplicate item IDs inside one table are invalid.

The supplied `[transPoint."7:*"]` template includes
`adventureExp = 0`, so it preserves the old three-realms 5-Primogem and
20-item reward without inheriting ordinary waypoint EXP.

The native `ExcelBinOutput/TransPointRewardConfigData.json` and old
`rewardId` are not used to decide these amounts.

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
