# Reward chain cleanup handoff

## Context

Branch: `play/rino`

HEAD observed before writing this handoff: `bbd0eb72a139979ffaa39f25d1e79956cd59746c`.

The chest reward refactor landed earlier as:

- `4c270230ae26661d8843df006f3d0d4c96bbb3ff` — `refactor: preserve native chest drops [skip ci]`

Parallel sessions may continue moving `play/rino`. **Always re-fetch the branch HEAD immediately before editing/committing and do not overwrite concurrent work.**

Project rules relevant here:

- Direct commits to `play/rino` are normal; avoid unnecessary PRs.
- During implementation, do not let CI slow iteration. Before asking the user to test, and before any upstream submission, full CI/build is mandatory.
- If user testing is needed, provide one complete copy/paste command block including remote fetch, checkout, build/compile and tests.
- For reverse-engineering questions use `Genshin-Reverse` and prefer authoritative game/resource data over wiki guesses or fabricated defaults.

## Target architecture

Reward contents should come from authoritative game/resource data. Server configuration may scale a resolved reward, but should not replace the native reward composition with invented tier tables or fallback packages.

Preferred shape:

```text
native Reward/Drop metadata
    -> resolve exact native reward items
    -> apply source-specific multipliers
    -> apply global resource multipliers where intended
    -> round once
    -> grant
```

Do not infer reward composition from chest rarity, gadget name, map position, country, etc. Do not fabricate a canonical reward when authoritative metadata is missing.

## Completed: normal chest reward path

The old four-tier chest replacement model was removed in `4c270230`.

`game.json` chest config is now rate-only:

```json
"chests": {
  "primogems": 1.0,
  "mora": 1.0,
  "artifacts": 1.0,
  "weapons": 1.0,
  "expBooks": 1.0
}
```

Defaults are `1.0` so native values remain unchanged.

The normal scripted chest path now resolves:

```text
SceneGadget.drop_tag / chest_drop_id
    -> ChestDrop.json
    -> DropTable
    -> processDrop()
    -> native GameItem list
    -> ChestRewardScaler
    -> grant
```

Important implementation points:

- `src/main/java/emu/grasscutter/game/drop/DropSystem.java`
  - `handleChestDrop(...)` applies `ChestRewardScaler.scaleItems(items)` only after `processDrop(...)`.
- `src/main/java/emu/grasscutter/game/reward/ChestRewardScaler.java`
  - Primogems: item `201`
  - Mora: item `202`
  - EXP books: `104001`, `104002`, `104003`
  - artifacts via `ITEM_RELIQUARY`
  - weapons via `ITEM_WEAPON`
  - equipment multipliers create separate `GameItem` instances instead of invalid equipment stacks.
  - it composes with `RewardScaler`, so global Mora multiplier still participates and rounding happens once.
- `src/main/java/emu/grasscutter/game/entity/gadget/chest/WorldChestLootHelper.java` was deleted.
- `GameConfig` schema version was bumped to 6 and rejects removed `common/exquisite/precious/luxurious` chest replacement fields.

The same commit also normalized obvious reward defaults to vanilla/no-boost values:

- global Adventure EXP multiplier: `1.0`
- global Mora multiplier: `1.0`
- Ley Line wealth: `1.0`
- Ley Line revelation: `1.0`
- waypoint fixed default corrected to `5 Primogems + 50 Adventure EXP`

## Known remaining chest legacy path — clean this first

A second, old chest reward system still exists and bypasses the native DropTable path when the scripted metadata path cannot resolve or scripts are disabled.

Files:

- `src/main/java/emu/grasscutter/game/world/WorldDataSystem.java`
- `src/main/java/emu/grasscutter/game/entity/gadget/chest/NormalChestInteractHandler.java`
- `src/main/java/emu/grasscutter/game/world/ChestReward.java`
- runtime data file `ChestReward.json`
- fallback dispatch in `src/main/java/emu/grasscutter/game/entity/gadget/GadgetChest.java`

Current legacy flow:

```text
WorldDataSystem.loadChestConfig()
    -> loads ChestReward.json
    -> maps gadget jsonName -> NormalChestInteractHandler
    -> GadgetChest fallback invokes handler
    -> handler manually constructs/grants rewards
```

`NormalChestInteractHandler` currently does all of the following itself:

- grants Adventure EXP directly
- treats `chestReward.resin` as item `201` (Primogems)
- computes Mora from `chestReward.mora * (1 + (worldLevel - 1) * 0.5)`
- directly drops fixed `content`
- randomly samples `randomContent`

This is exactly the sort of parallel reward definition we are removing.

### Desired cleanup

1. Remove normal `ChestReward.json` registration from `WorldDataSystem.loadChestConfig()`.
2. Keep the special boss-flower handler registration if it is still needed (`SceneObj_Chest_Flora` / `BossChestInteractHandler`).
3. Remove the normal fallback from `GadgetChest`; if native chest metadata is absent/unresolvable, log/fail instead of fabricating a reward from `ChestReward.json`.
4. Delete `NormalChestInteractHandler.java` if no remaining callers.
5. Delete `game.world.ChestReward` if no remaining callers.
6. Remove obsolete `ChestReward.json` from the runtime data/resource package only after confirming no other code path consumes it.
7. Add tests/guards so ordinary exploration chests cannot silently fall back to hand-authored reward packages.

Before deleting files, search the current branch for all references because parallel work may have changed callers.

## Other reward chains that still need audit/cleanup

Continue from highest risk: places that invent or multiply reward contents outside authoritative Reward/Drop data.

### 1. Statue offering / city level rewards

Inspect:

- `src/main/java/emu/grasscutter/game/managers/StatueOfferRewardHelper.java`
- `src/main/java/emu/grasscutter/game/managers/SotSManager.java`
- `src/main/java/emu/grasscutter/data/excels/StatuePromoteData.java`
- `src/main/java/emu/grasscutter/data/excels/CityLevelupData.java`

Earlier audit found `StatueOfferRewardHelper` applying private-server-style country-specific boosts/fallbacks on top of reward data, including inflated Adventure EXP / Primogem logic. Direction:

- `StatuePromoteData` / `CityLevelupData` + referenced `RewardData` are authoritative.
- remove custom boost multipliers.
- remove fabricated fallback reward packages when data is missing.
- missing authoritative data should fail/log clearly, not invent a canonical reward.

### 2. Teleport Waypoint / Statue first unlock

`GameConfig.Rewards.waypoint` and `.statue` still represent fixed unlock rewards.

Known original values currently used:

- waypoint: `5 Primogems + 50 Adventure EXP`
- statue initial unlock: `5 Primogems + 50 Adventure EXP`

Audit whether these can be sourced from authoritative game/resource data. If resource-native values exist, prefer those over fixed server config. If the protocol/game genuinely requires fixed logic with no available table, keep it explicit and vanilla-default.

### 3. Ley Line / Blossom

Audit the Blossom reward path and ensure it follows:

```text
native reward list
  * source rate (wealth or revelation)
  * global resource rate where applicable
  -> one final round
```

Rules already established:

- Wealth Mora = base × `leyLines.wealth` × global `rewards.mora`
- Revelation EXP books = base × `leyLines.revelation`
- global Adventure EXP multiplier does not affect EXP books
- Condensed Resin multiplies the source reward rate
- no old `global/mora/exp/experienceBooks` ley-line schema fields

Use `RewardScaler` rather than repeated rounding in separate layers.

### 4. World boss / trounce blossom fallback

Inspect:

- `DropSystem.handleBossChestDrop(...)`
- `WorldBossChestLootHelper`
- `GadgetChest.grantBossChestFallback(...)`
- `RewardPreviewData` fallback logic

Primary path through `ChestDrop.json` / DropTable is preferable. Any fallback that reconstructs rewards from preview data, wiki/hardcoded values, or guessed world-level rules should be treated as suspect. Preserve a fallback only when it is demonstrably derived from authoritative resource data.

Do **not** apply exploration chest multipliers (`rewards.chests.*`) to world boss/trounce blossom rewards.

### 5. Generic RewardData consumers

Search for all direct grants around reward IDs and `RewardData` / `RewardPreviewData`, especially quest completion, commissions, achievements, handbook, domains/dungeons, offering systems, activities, battle pass and mail.

Goal is not to force every reward through one giant function. The useful convergence is:

- contents come from the correct authoritative source;
- shared resource multipliers use `RewardScaler`;
- source-specific multipliers are applied once at the source boundary;
- callers do not duplicate/guess the reward composition.

### 6. Sacred Sakura data boost marker

Earlier audit found `data/_sakura_reward_5x.flag`, apparently related to a Sacred Sakura reward table boosted x5. Do not merely delete the marker. Trace the actual modified reward data/import/generator and restore authoritative original reward values if the resource table itself was patched.

## RewardScaler semantics to preserve

File: `src/main/java/emu/grasscutter/game/reward/RewardScaler.java`

Known behavior:

- item `102` -> global Adventure EXP multiplier
- item `202` -> global Mora multiplier
- other items -> global resource multiplier `1.0`
- source rate and resource rate compose before a single final `Math.round`
- negative/NaN/infinite/overflow handling exists
- batch scaling has rollback/fail-open behavior for ordinary Java runtime failures
- do not swallow JVM `Error`

Tests: `src/test/java/emu/grasscutter/game/reward/RewardScalerTest.java`.

## Config philosophy

`game.json` should expose intentional server policy/rates, not copies of original game reward tables. Canonical native values belong in resources/Excel/scene data.

For reward configuration:

- default multipliers = `1.0`
- avoid compatibility aliases/layers for removed reward schemas; user explicitly prefers clean break over accumulating legacy compatibility
- do not reintroduce chest rarity reward packages
- special native rewards must stay special; scaling should act on their resolved items

## Suggested next execution order

1. Re-fetch `play/rino` HEAD.
2. Remove the `ChestReward.json` / `NormalChestInteractHandler` ordinary chest fallback after confirming references.
3. Audit and clean `StatueOfferRewardHelper` custom boosts/fallbacks.
4. Audit boss chest fallback for fabricated preview/hardcoded rewards.
5. Audit Ley Line/Blossom composition for one-round scaling.
6. Trace Sacred Sakura x5 resource modification.
7. Broader search of `RewardData`, `RewardPreviewData`, direct `addItem(201/202...)`, `addExpDirectly`, and hand-authored reward lists.
8. Add focused tests while iterating; full CI only before user testing/upstream as required.

## Useful starting searches

Search current branch for:

```text
NormalChestInteractHandler
ChestReward.json
game.world.ChestReward
RewardScaler.scaleCount
RewardScaler.scaleItems
addExpDirectly
addItem(201
addItem(202
RewardPreviewData
RewardData
StatueOfferRewardHelper
WorldBossChestLootHelper
_sakura_reward_5x
```

When a reward chain is found, classify it as one of:

1. authoritative Reward/Drop data + clean grant path: keep;
2. authoritative data + duplicated multiplier logic: consolidate scaling;
3. hand-authored/fabricated fallback reward: remove or replace with authoritative source;
4. intentional server policy (multiplier): keep in `game.json`, default `1.0`.
